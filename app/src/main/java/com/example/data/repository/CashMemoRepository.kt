package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.db.CashMemoDao
import com.example.data.db.PlantRateDao
import com.example.data.model.BusinessProfile
import com.example.data.model.CashMemoEntity
import com.example.data.model.CashMemoItem
import com.example.data.model.PlantRateEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class CashMemoRepository(
    private val cashMemoDao: CashMemoDao,
    private val plantRateDao: PlantRateDao,
    context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("cashmemo_business_prefs", Context.MODE_PRIVATE)

    val allCashMemos: Flow<List<CashMemoEntity>> = cashMemoDao.getAllCashMemos()

    fun searchMemos(query: String): Flow<List<CashMemoEntity>> =
        cashMemoDao.searchCashMemos(query)

    suspend fun getMemoById(id: Long): CashMemoEntity? =
        cashMemoDao.getCashMemoById(id)

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
        return id
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
                grandTotal = obj.optDouble("grandTotal", 0.0),
                previousDues = obj.optDouble("previousDues", 0.0),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
            cashMemoDao.insertCashMemo(memo)
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
