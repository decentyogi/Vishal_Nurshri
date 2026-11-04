package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.db.CashMemoDao
import com.example.data.db.CustomerDao
import com.example.data.db.PlantRateDao
import com.example.data.model.BusinessProfile
import com.example.data.model.CashMemoEntity
import com.example.data.model.CashMemoItem
import com.example.data.model.CustomerBalanceHistoryEntity
import com.example.data.model.CustomerProfileEntity
import com.example.data.model.PlantRateEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

class CashMemoRepository(
    private val cashMemoDao: CashMemoDao,
    private val plantRateDao: PlantRateDao,
    private val customerDao: CustomerDao,
    context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("cashmemo_business_prefs", Context.MODE_PRIVATE)

    val allCashMemos: Flow<List<CashMemoEntity>> = cashMemoDao.getAllCashMemos()

    fun searchMemos(query: String): Flow<List<CashMemoEntity>> =
        cashMemoDao.searchCashMemos(query)

    suspend fun getMemoById(id: Long): CashMemoEntity? =
        cashMemoDao.getCashMemoById(id)

    // Customer Profiles & Outstanding Balance History
    val allCustomers: Flow<List<CustomerProfileEntity>> = customerDao.getAllCustomers()

    val allBalanceHistory: Flow<List<CustomerBalanceHistoryEntity>> = customerDao.getAllBalanceHistory()

    fun searchCustomers(query: String): Flow<List<CustomerProfileEntity>> =
        customerDao.searchCustomers(query)

    fun getCustomerBalanceHistory(customerId: Long): Flow<List<CustomerBalanceHistoryEntity>> =
        customerDao.getBalanceHistoryForCustomer(customerId)

    suspend fun findMatchingCustomer(phone: String, name: String): CustomerProfileEntity? {
        val cleanPhone = phone.trim()
        val cleanName = name.trim()
        if (cleanPhone.isNotBlank()) {
            customerDao.getCustomerByPhone(cleanPhone)?.let { return it }
        }
        if (cleanName.isNotBlank()) {
            customerDao.getCustomerByName(cleanName)?.let { return it }
        }
        return null
    }

    suspend fun saveMemo(memo: CashMemoEntity): Long {
        val id = cashMemoDao.insertCashMemo(memo)
        try {
            val num = memo.memoNumber.filter { it.isDigit() }.toIntOrNull()
            if (num != null) {
                val currentNext = getBusinessProfile().nextMemoNumber
                if (num >= currentNext) {
                    saveNextMemoNumber(num + 1)
                }
            }
        } catch (_: Exception) {}

        // Automatically sync customer profile & balance history when customer info is present
        syncCustomerOnMemoSaved(memo.copy(id = id))
        return id
    }

    private suspend fun syncCustomerOnMemoSaved(memo: CashMemoEntity) {
        val cleanName = memo.customerName.trim()
        val cleanPhone = memo.customerMobile.trim()
        if (cleanName.isBlank() && cleanPhone.isBlank()) return

        val displayName = cleanName.ifBlank { "Customer ($cleanPhone)" }
        val existing = findMatchingCustomer(cleanPhone, displayName)

        val currentBillAmount = memo.grandTotal
        val paidNow = memo.effectivePaidAmount
        val newBalanceFromMemo = memo.effectiveBalanceDue

        val now = System.currentTimeMillis()
        if (existing != null) {
            // If memo already included previousDues >= existing.outstandingBalance, newBalanceFromMemo is the new total balance;
            // otherwise add current unpaid delta to existing balance.
            val newOutstanding = if (memo.previousDues >= existing.outstandingBalance && existing.outstandingBalance > 0.0) {
                newBalanceFromMemo
            } else {
                max(0.0, existing.outstandingBalance + (currentBillAmount - paidNow))
            }
            val balanceDelta = newOutstanding - existing.outstandingBalance

            val updatedCustomer = existing.copy(
                name = displayName,
                phoneNumber = cleanPhone.ifBlank { existing.phoneNumber },
                address = memo.customerAddress.trim().ifBlank { existing.address },
                outstandingBalance = newOutstanding,
                totalPurchased = existing.totalPurchased + currentBillAmount,
                totalPaid = existing.totalPaid + paidNow,
                lastPromiseNote = if (newOutstanding > 0.009) memo.paymentStatusNote.ifBlank { "Next Day (कल देगा)" } else "Fully Paid (चुकता)",
                lastTransactionAt = now
            )
            customerDao.updateCustomer(updatedCustomer)

            customerDao.insertBalanceHistory(
                CustomerBalanceHistoryEntity(
                    customerId = existing.id,
                    customerName = updatedCustomer.name,
                    customerPhone = updatedCustomer.phoneNumber,
                    memoId = memo.id,
                    memoNumber = memo.memoNumber,
                    transactionType = "BILL_CREATED",
                    billAmount = memo.totalPayableWithDues,
                    paidAmount = paidNow,
                    balanceChange = balanceDelta,
                    resultingBalance = newOutstanding,
                    paymentMethod = memo.paymentMethod,
                    note = if (newOutstanding > 0.009) {
                        "Bill #${memo.memoNumber}: Paid ₹${String.format("%.0f", paidNow)} of ₹${String.format("%.0f", memo.totalPayableWithDues)} • Rest ₹${String.format("%.0f", newOutstanding)} (${memo.paymentStatusNote.ifBlank { "Next Day" }})"
                    } else {
                        "Bill #${memo.memoNumber}: Full Payment ₹${String.format("%.0f", paidNow)}"
                    },
                    timestamp = now
                )
            )
        } else {
            val newProfile = CustomerProfileEntity(
                name = displayName,
                phoneNumber = cleanPhone,
                address = memo.customerAddress.trim(),
                outstandingBalance = newBalanceFromMemo,
                totalPurchased = currentBillAmount,
                totalPaid = paidNow,
                lastPromiseNote = if (newBalanceFromMemo > 0.009) memo.paymentStatusNote.ifBlank { "Next Day (कल देगा)" } else "Fully Paid (चुकता)",
                lastTransactionAt = now,
                createdAt = now
            )
            val customerId = customerDao.insertCustomer(newProfile)

            customerDao.insertBalanceHistory(
                CustomerBalanceHistoryEntity(
                    customerId = customerId,
                    customerName = displayName,
                    customerPhone = cleanPhone,
                    memoId = memo.id,
                    memoNumber = memo.memoNumber,
                    transactionType = "BILL_CREATED",
                    billAmount = memo.totalPayableWithDues,
                    paidAmount = paidNow,
                    balanceChange = newBalanceFromMemo,
                    resultingBalance = newBalanceFromMemo,
                    paymentMethod = memo.paymentMethod,
                    note = if (newBalanceFromMemo > 0.009) {
                        "Bill #${memo.memoNumber}: Paid ₹${String.format("%.0f", paidNow)} of ₹${String.format("%.0f", memo.totalPayableWithDues)} • Rest ₹${String.format("%.0f", newBalanceFromMemo)} (${memo.paymentStatusNote.ifBlank { "Next Day" }})"
                    } else {
                        "Bill #${memo.memoNumber}: Full Payment ₹${String.format("%.0f", paidNow)}"
                    },
                    timestamp = now
                )
            )
        }
    }

    suspend fun updateMemoPaymentAndSyncCustomer(
        oldMemo: CashMemoEntity,
        updatedMemo: CashMemoEntity
    ) {
        cashMemoDao.updateCashMemo(updatedMemo)

        val additionalPaid = updatedMemo.effectivePaidAmount - oldMemo.effectivePaidAmount
        val balanceDelta = updatedMemo.effectiveBalanceDue - oldMemo.effectiveBalanceDue
        val existing = findMatchingCustomer(updatedMemo.customerMobile, updatedMemo.customerName)

        if (existing != null) {
            val now = System.currentTimeMillis()
            val newOutstanding = max(0.0, existing.outstandingBalance + balanceDelta)
            val newTotalPaid = max(0.0, existing.totalPaid + additionalPaid)
            val updatedCustomer = existing.copy(
                outstandingBalance = newOutstanding,
                totalPaid = newTotalPaid,
                lastPromiseNote = if (newOutstanding > 0.009) updatedMemo.paymentStatusNote.ifBlank { "Next Day (कल देगा)" } else "Fully Paid (चुकता)",
                lastTransactionAt = now
            )
            customerDao.updateCustomer(updatedCustomer)

            customerDao.insertBalanceHistory(
                CustomerBalanceHistoryEntity(
                    customerId = existing.id,
                    customerName = existing.name,
                    customerPhone = existing.phoneNumber,
                    memoId = updatedMemo.id,
                    memoNumber = updatedMemo.memoNumber,
                    transactionType = if (newOutstanding <= 0.009) "BALANCE_SETTLED" else "PAYMENT_RECEIVED",
                    billAmount = updatedMemo.totalPayableWithDues,
                    paidAmount = updatedMemo.effectivePaidAmount,
                    balanceChange = balanceDelta,
                    resultingBalance = newOutstanding,
                    paymentMethod = updatedMemo.paymentMethod,
                    note = if (newOutstanding <= 0.009) {
                        "Settled Bill #${updatedMemo.memoNumber} • Total Paid ₹${String.format("%.0f", updatedMemo.effectivePaidAmount)}"
                    } else {
                        "Updated Bill #${updatedMemo.memoNumber} • Paid ₹${String.format("%.0f", updatedMemo.effectivePaidAmount)}, Remaining ₹${String.format("%.0f", newOutstanding)} (${updatedMemo.paymentStatusNote})"
                    },
                    timestamp = now
                )
            )
        }
    }

    suspend fun saveOrUpdateCustomerProfile(
        id: Long = 0,
        name: String,
        phoneNumber: String,
        address: String,
        outstandingBalance: Double,
        promiseNote: String
    ): Long {
        val now = System.currentTimeMillis()
        val existing = if (id > 0) customerDao.getCustomerById(id) else findMatchingCustomer(phoneNumber, name)

        return if (existing != null) {
            val balanceDelta = outstandingBalance - existing.outstandingBalance
            val updated = existing.copy(
                name = name.trim(),
                phoneNumber = phoneNumber.trim(),
                address = address.trim(),
                outstandingBalance = max(0.0, outstandingBalance),
                lastPromiseNote = if (outstandingBalance > 0.009) promiseNote.ifBlank { "Next Day (कल देगा)" } else "Fully Paid (चुकता)",
                lastTransactionAt = now
            )
            customerDao.updateCustomer(updated)

            if (kotlin.math.abs(balanceDelta) > 0.009) {
                customerDao.insertBalanceHistory(
                    CustomerBalanceHistoryEntity(
                        customerId = existing.id,
                        customerName = updated.name,
                        customerPhone = updated.phoneNumber,
                        transactionType = if (balanceDelta < 0) "PAYMENT_RECEIVED" else "MANUAL_ADJUSTMENT",
                        billAmount = existing.outstandingBalance,
                        paidAmount = if (balanceDelta < 0) -balanceDelta else 0.0,
                        balanceChange = balanceDelta,
                        resultingBalance = updated.outstandingBalance,
                        paymentMethod = "Cash",
                        note = if (updated.outstandingBalance <= 0.009) {
                            "Full balance settled (चुकता)"
                        } else {
                            "Balance updated to ₹${String.format("%.0f", updated.outstandingBalance)} (${updated.lastPromiseNote})"
                        },
                        timestamp = now
                    )
                )
            }
            existing.id
        } else {
            val profile = CustomerProfileEntity(
                name = name.trim(),
                phoneNumber = phoneNumber.trim(),
                address = address.trim(),
                outstandingBalance = max(0.0, outstandingBalance),
                totalPurchased = max(0.0, outstandingBalance),
                totalPaid = 0.0,
                lastPromiseNote = if (outstandingBalance > 0.009) promiseNote.ifBlank { "Next Day (कल देगा)" } else "Fully Paid (चुकता)",
                lastTransactionAt = now,
                createdAt = now
            )
            val newId = customerDao.insertCustomer(profile)
            if (outstandingBalance > 0.009) {
                customerDao.insertBalanceHistory(
                    CustomerBalanceHistoryEntity(
                        customerId = newId,
                        customerName = profile.name,
                        customerPhone = profile.phoneNumber,
                        transactionType = "MANUAL_ADJUSTMENT",
                        billAmount = outstandingBalance,
                        paidAmount = 0.0,
                        balanceChange = outstandingBalance,
                        resultingBalance = outstandingBalance,
                        paymentMethod = "Cash",
                        note = "Opening balance ₹${String.format("%.0f", outstandingBalance)} (${profile.lastPromiseNote})",
                        timestamp = now
                    )
                )
            }
            newId
        }
    }

    suspend fun recordCustomerPaymentSettlement(
        customer: CustomerProfileEntity,
        amountReceivedNow: Double,
        paymentMethod: String,
        remainingPromiseNote: String
    ) {
        val now = System.currentTimeMillis()
        val newBalance = max(0.0, customer.outstandingBalance - amountReceivedNow)
        val updatedCustomer = customer.copy(
            outstandingBalance = newBalance,
            totalPaid = customer.totalPaid + amountReceivedNow,
            lastPromiseNote = if (newBalance > 0.009) remainingPromiseNote.ifBlank { "Next Day (कल देगा)" } else "Fully Paid (चुकता)",
            lastTransactionAt = now
        )
        customerDao.updateCustomer(updatedCustomer)

        customerDao.insertBalanceHistory(
            CustomerBalanceHistoryEntity(
                customerId = customer.id,
                customerName = customer.name,
                customerPhone = customer.phoneNumber,
                transactionType = if (newBalance <= 0.009) "BALANCE_SETTLED" else "PAYMENT_RECEIVED",
                billAmount = customer.outstandingBalance,
                paidAmount = amountReceivedNow,
                balanceChange = -amountReceivedNow,
                resultingBalance = newBalance,
                paymentMethod = paymentMethod,
                note = if (newBalance <= 0.009) {
                    "Received ₹${String.format("%.0f", amountReceivedNow)} ($paymentMethod) • All dues settled (चुकता)"
                } else {
                    "Received ₹${String.format("%.0f", amountReceivedNow)} ($paymentMethod) • Remaining ₹${String.format("%.0f", newBalance)} ($remainingPromiseNote)"
                },
                timestamp = now
            )
        )
    }

    suspend fun deleteCustomer(customerId: Long) {
        customerDao.deleteHistoryForCustomer(customerId)
        customerDao.deleteCustomer(customerId)
    }

    suspend fun syncCustomersFromExistingMemosIfEmpty() {
        if (customerDao.getCustomerCount() == 0) {
            val memos = cashMemoDao.getAllCashMemosList().sortedBy { it.createdAt }
            if (memos.isNotEmpty()) {
                for (memo in memos) {
                    syncCustomerOnMemoSaved(memo)
                }
            } else {
                // Seed realistic nursery customer profiles with sample balance history so workflow is ready immediately
                val now = System.currentTimeMillis()
                val sampleCustomer1 = CustomerProfileEntity(
                    name = "रमेश कुमार (Ramesh Kumar)",
                    phoneNumber = "9835123456",
                    address = "सासाराम (रोहतास)",
                    outstandingBalance = 200.0,
                    totalPurchased = 1000.0,
                    totalPaid = 800.0,
                    lastPromiseNote = "Next Day (कल देगा)",
                    lastTransactionAt = now - 3600_000L
                )
                val id1 = customerDao.insertCustomer(sampleCustomer1)
                customerDao.insertBalanceHistory(
                    CustomerBalanceHistoryEntity(
                        customerId = id1,
                        customerName = sampleCustomer1.name,
                        customerPhone = sampleCustomer1.phoneNumber,
                        memoNumber = "500",
                        transactionType = "BILL_CREATED",
                        billAmount = 1000.0,
                        paidAmount = 800.0,
                        balanceChange = 200.0,
                        resultingBalance = 200.0,
                        paymentMethod = "Cash",
                        note = "Bought plants worth ₹1000, paid ₹800 • Rest ₹200 Next Day (कल देगा)",
                        timestamp = now - 3600_000L
                    )
                )

                val sampleCustomer2 = CustomerProfileEntity(
                    name = "अनीता देवी (Anita Devi)",
                    phoneNumber = "7004128990",
                    address = "डेहरी ऑन सोन",
                    outstandingBalance = 0.0,
                    totalPurchased = 650.0,
                    totalPaid = 650.0,
                    lastPromiseNote = "Fully Paid (चुकता)",
                    lastTransactionAt = now - 86400_000L
                )
                val id2 = customerDao.insertCustomer(sampleCustomer2)
                customerDao.insertBalanceHistory(
                    CustomerBalanceHistoryEntity(
                        customerId = id2,
                        customerName = sampleCustomer2.name,
                        customerPhone = sampleCustomer2.phoneNumber,
                        memoNumber = "499",
                        transactionType = "BILL_CREATED",
                        billAmount = 650.0,
                        paidAmount = 650.0,
                        balanceChange = 0.0,
                        resultingBalance = 0.0,
                        paymentMethod = "UPI",
                        note = "Bill #499: Full Payment ₹650 via UPI",
                        timestamp = now - 86400_000L
                    )
                )
            }
        }
    }

    suspend fun updateMemo(memo: CashMemoEntity) {
        cashMemoDao.updateCashMemo(memo)
    }

    suspend fun deleteMemo(id: Long) = cashMemoDao.deleteCashMemo(id)

    // Plant Rate Catalog operations
    val allRates: Flow<List<PlantRateEntity>> = plantRateDao.getAllRates()

    fun searchRates(query: String): Flow<List<PlantRateEntity>> =
        plantRateDao.searchRates(query)

    suspend fun saveRate(rate: PlantRateEntity): Long =
        plantRateDao.insertRate(rate)

    suspend fun updateRate(rate: PlantRateEntity) =
        plantRateDao.updateRate(rate)

    suspend fun deleteRate(id: Long) =
        plantRateDao.deleteRate(id)

    suspend fun populateDefaultRatesIfEmpty() {
        if (plantRateDao.getRateCount() == 0) {
            val defaultRates = listOf(
                PlantRateEntity(name = "गुलाब (Red Rose Plant)", retailPrice = 30.0, wholesalePrice = 18.0),
                PlantRateEntity(name = "गेंदा (Marigold Plant)", retailPrice = 15.0, wholesalePrice = 8.0),
                PlantRateEntity(name = "गुलदावदी (Chrysanthemum)", retailPrice = 35.0, wholesalePrice = 20.0),
                PlantRateEntity(name = "गुलमोहर (Gulmohar Tree)", retailPrice = 40.0, wholesalePrice = 22.0),
                PlantRateEntity(name = "गुलमेहंदी (Balsam Flower)", retailPrice = 20.0, wholesalePrice = 12.0),
                PlantRateEntity(name = "आम्रपाली आम (Mango Amrapali)", retailPrice = 120.0, wholesalePrice = 75.0),
                PlantRateEntity(name = "अमरूद (Guava L-49)", retailPrice = 80.0, wholesalePrice = 50.0),
                PlantRateEntity(name = "गुड़हल (Hibiscus Hybrid)", retailPrice = 45.0, wholesalePrice = 28.0),
                PlantRateEntity(name = "बोगनवेलिया (Bougainvillea)", retailPrice = 60.0, wholesalePrice = 35.0),
                PlantRateEntity(name = "क्रोटन इंडोर (Croton Indoor)", retailPrice = 90.0, wholesalePrice = 55.0),
                PlantRateEntity(name = "मनी प्लांट (Money Plant Stick)", retailPrice = 80.0, wholesalePrice = 50.0),
                PlantRateEntity(name = "मिट्टी का गमला 10 इंच (Clay Pot 10\")", retailPrice = 60.0, wholesalePrice = 38.0),
                PlantRateEntity(name = "मिट्टी का गमला 12 इंच (Clay Pot 12\")", retailPrice = 85.0, wholesalePrice = 55.0),
                PlantRateEntity(name = "सिरेमिक गमला (Ceramic Planter)", retailPrice = 180.0, wholesalePrice = 120.0),
                PlantRateEntity(name = "वर्मीकम्पोस्ट खाद 5kg (Vermicompost)", retailPrice = 70.0, wholesalePrice = 45.0),
                PlantRateEntity(name = "नीम खली खाद 1kg (Neem Khali)", retailPrice = 45.0, wholesalePrice = 30.0)
            )
            plantRateDao.insertRates(defaultRates)
        }
    }

    suspend fun getAllRatesDirectly(): List<PlantRateEntity> {
        populateDefaultRatesIfEmpty()
        return plantRateDao.getAllRatesList()
    }

    suspend fun getAllCashMemosDirectly(): List<CashMemoEntity> {
        return cashMemoDao.getAllCashMemosList()
    }

    suspend fun getAllCustomersDirectly(): List<CustomerProfileEntity> {
        return customerDao.getAllCustomersList()
    }

    suspend fun exportRatesJson(ratesList: List<PlantRateEntity>? = null): String {
        val list = if (!ratesList.isNullOrEmpty()) ratesList else getAllRatesDirectly()
        val array = JSONArray()
        for (r in list) {
            val obj = JSONObject()
            obj.put("name", r.name)
            obj.put("retailPrice", r.retailPrice)
            obj.put("wholesalePrice", r.wholesalePrice)
            obj.put("category", r.category)
            array.put(obj)
        }
        return array.toString(2)
    }

    suspend fun importRatesJson(jsonString: String): Int {
        val trimmed = jsonString.trim()
        val array: JSONArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            when {
                obj.has("rates") -> obj.getJSONArray("rates")
                obj.has("plants") -> obj.getJSONArray("plants")
                obj.has("items") -> obj.getJSONArray("items")
                else -> JSONArray().apply { put(obj) }
            }
        } else {
            return 0
        }

        val list = mutableListOf<PlantRateEntity>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val name = obj.optString("name", obj.optString("plantName", "")).trim()
            if (name.isNotBlank()) {
                val retail = if (obj.has("retailPrice")) obj.optDouble("retailPrice", 0.0) else obj.optDouble("retail", 0.0)
                val wholesale = if (obj.has("wholesalePrice")) obj.optDouble("wholesalePrice", 0.0) else obj.optDouble("wholesale", 0.0)
                list.add(
                    PlantRateEntity(
                        name = name,
                        retailPrice = retail,
                        wholesalePrice = wholesale,
                        category = obj.optString("category", "Plants")
                    )
                )
            }
        }
        if (list.isNotEmpty()) {
            plantRateDao.insertRates(list)
        }
        return list.size
    }

    suspend fun exportAllMemosJson(): String {
        val memos = getAllCashMemosDirectly()
        val array = JSONArray()
        for (m in memos) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("memoNumber", m.memoNumber)
            obj.put("customerName", m.customerName)
            obj.put("customerMobile", m.customerMobile)
            obj.put("customerAddress", m.customerAddress)
            obj.put("date", m.date)
            obj.put("itemsJson", m.itemsJson)
            obj.put("subtotal", m.subtotal)
            obj.put("discount", m.discount)
            obj.put("taxPercentage", m.taxPercentage)
            obj.put("taxAmount", m.taxAmount)
            obj.put("grandTotal", m.grandTotal)
            obj.put("previousDues", m.previousDues)
            obj.put("totalPayable", m.totalPayableWithDues)
            obj.put("paidAmount", m.effectivePaidAmount)
            obj.put("balanceDue", m.effectiveBalanceDue)
            obj.put("paymentMethod", m.paymentMethod)
            obj.put("paymentStatusNote", m.paymentStatusNote)
            obj.put("createdAt", m.createdAt)
            array.put(obj)
        }
        return array.toString(2)
    }

    suspend fun importMemosJson(jsonString: String): Int {
        val trimmed = jsonString.trim()
        val array: JSONArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            when {
                obj.has("memos") -> obj.getJSONArray("memos")
                obj.has("invoices") -> obj.getJSONArray("invoices")
                else -> JSONArray().apply { put(obj) }
            }
        } else {
            return 0
        }

        var count = 0
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val grandTotal = obj.optDouble("grandTotal", 0.0)
            val prevDues = obj.optDouble("previousDues", 0.0)
            val totalPayable = grandTotal + prevDues
            val paid = if (obj.has("paidAmount")) obj.optDouble("paidAmount", totalPayable) else totalPayable
            val balance = if (obj.has("balanceDue")) obj.optDouble("balanceDue", 0.0) else max(0.0, totalPayable - paid)

            val memo = CashMemoEntity(
                memoNumber = obj.optString("memoNumber", "501"),
                customerName = obj.optString("customerName", ""),
                customerMobile = obj.optString("customerMobile", ""),
                customerAddress = obj.optString("customerAddress", ""),
                date = obj.optString("date", ""),
                itemsJson = obj.optString("itemsJson", "[]"),
                subtotal = obj.optDouble("subtotal", 0.0),
                discount = obj.optDouble("discount", 0.0),
                taxPercentage = obj.optDouble("taxPercentage", 0.0),
                taxAmount = obj.optDouble("taxAmount", 0.0),
                grandTotal = grandTotal,
                previousDues = prevDues,
                paidAmount = paid,
                balanceDue = balance,
                paymentMethod = obj.optString("paymentMethod", "Cash"),
                paymentStatusNote = obj.optString("paymentStatusNote", ""),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
            val id = cashMemoDao.insertCashMemo(memo)
            syncCustomerOnMemoSaved(memo.copy(id = id))
            count++
        }
        return count
    }

    fun getBusinessProfile(): BusinessProfile {
        return BusinessProfile(
            shopNameHindi = prefs.getString("shopNameHindi", "विशाल नर्सरी") ?: "विशाल नर्सरी",
            shopNameEnglish = prefs.getString("shopNameEnglish", "Vishal Nursery") ?: "Vishal Nursery",
            tagline = prefs.getString(
                "tagline",
                "हमारे यहाँ हर प्रकार के फूल, पेड़-पौधा जैसे-गुलाब, गुलदावदी, गेंदा, क्रोटन सब गमले में तैयार पौधा भी मिलता है।"
            ) ?: "",
            phone = prefs.getString("phone", "7631073743") ?: "7631073743",
            addressLine1 = prefs.getString(
                "addressLine1",
                "ग्राम-बेदा, माँ ताराचण्डी रोड (माँ सीताराम मंदिर के निकट)"
            ) ?: "",
            addressLine2 = prefs.getString(
                "addressLine2",
                "पो०-सासाराम (रोहतास)"
            ) ?: "",
            memoPrefix = prefs.getString("memoPrefix", "") ?: "",
            nextMemoNumber = prefs.getInt("nextMemoNumber", 501),
            defaultTaxPercentage = prefs.getFloat("defaultTaxPercentage", 0f).toDouble(),
            enableTax = prefs.getBoolean("enableTax", false),
            customApiKey = prefs.getString("customApiKey", "") ?: "",
            selectedAiProvider = prefs.getString("selectedAiProvider", "gemini") ?: "gemini",
            selectedAiModel = prefs.getString("selectedAiModel", "gemini-2.5-flash") ?: "gemini-2.5-flash",
            signerName = prefs.getString("signerName", "VISHAL NURSERY") ?: "VISHAL NURSERY"
        )
    }

    fun saveBusinessProfile(profile: BusinessProfile) {
        prefs.edit()
            .putString("shopNameHindi", profile.shopNameHindi)
            .putString("shopNameEnglish", profile.shopNameEnglish)
            .putString("tagline", profile.tagline)
            .putString("phone", profile.phone)
            .putString("addressLine1", profile.addressLine1)
            .putString("addressLine2", profile.addressLine2)
            .putString("memoPrefix", profile.memoPrefix)
            .putInt("nextMemoNumber", profile.nextMemoNumber)
            .putFloat("defaultTaxPercentage", profile.defaultTaxPercentage.toFloat())
            .putBoolean("enableTax", profile.enableTax)
            .putString("customApiKey", profile.customApiKey)
            .putString("selectedAiProvider", profile.selectedAiProvider)
            .putString("selectedAiModel", profile.selectedAiModel)
            .putString("signerName", profile.signerName)
            .apply()
    }

    fun saveNextMemoNumber(nextNum: Int) {
        prefs.edit().putInt("nextMemoNumber", nextNum).apply()
    }

    companion object {
        fun serializeItems(items: List<CashMemoItem>): String {
            val array = JSONArray()
            for (item in items) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("serialNo", item.serialNo)
                obj.put("description", item.description)
                obj.put("quantity", item.quantity)
                obj.put("rate", item.rate)
                obj.put("amount", item.amount)
                array.put(obj)
            }
            return array.toString()
        }

        fun deserializeItems(json: String): List<CashMemoItem> {
            val list = mutableListOf<CashMemoItem>()
            if (json.isBlank()) return list
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        CashMemoItem(
                            id = obj.optString("id", i.toString()),
                            serialNo = obj.optInt("serialNo", i + 1),
                            description = obj.optString("description", ""),
                            quantity = obj.optDouble("quantity", 1.0),
                            rate = obj.optDouble("rate", 0.0),
                            amount = obj.optDouble("amount", 0.0)
                        )
                    )
                }
            } catch (_: Exception) {}
            return list
        }
    }
}
