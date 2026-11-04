package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ExtractedBillResult
import com.example.ai.GeminiService
import com.example.data.db.AppDatabase
import com.example.data.model.BusinessProfile
import com.example.data.model.CashMemoEntity
import com.example.data.model.CashMemoItem
import com.example.data.model.CustomerBalanceHistoryEntity
import com.example.data.model.CustomerProfileEntity
import com.example.data.model.PlantRateEntity
import com.example.data.repository.CashMemoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max

data class MemoEditorState(
    val memoNumber: String = "501",
    val date: String = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(Date()),
    val selectedCustomerId: Long? = null,
    val customerName: String = "",
    val customerMobile: String = "",
    val customerAddress: String = "",
    val items: List<CashMemoItem> = listOf(
        CashMemoItem(id = UUID.randomUUID().toString(), serialNo = 1, description = "", quantity = 1.0, rate = 0.0, amount = 0.0)
    ),
    val discount: Double = 0.0,
    val taxPercentage: Double = 0.0,
    val previousDues: Double = 0.0,
    val hasCustomPayment: Boolean = false,
    val paidAmount: Double = 0.0,
    val paymentMethod: String = "Cash",
    val paymentDueNote: String = "Next Day (कल देगा)",
    val notes: String = "",
    val isAnalyzing: Boolean = false,
    val analysisError: String? = null,
    val aiValidationNote: String? = null,
    val scannedBitmap: Bitmap? = null
) {
    val subtotal: Double
        get() = items.sumOf { it.amount }

    val taxAmount: Double
        get() = if (taxPercentage > 0) max(0.0, (subtotal - discount) * (taxPercentage / 100.0)) else 0.0

    val grandTotal: Double
        get() = max(0.0, subtotal - discount + taxAmount)

    val totalPayable: Double
        get() = grandTotal + previousDues

    val effectivePaidAmount: Double
        get() = if (hasCustomPayment) paidAmount else totalPayable

    val balanceDue: Double
        get() = if (hasCustomPayment) max(0.0, totalPayable - paidAmount) else 0.0

    val changeToReturn: Double
        get() = if (hasCustomPayment) max(0.0, paidAmount - totalPayable) else 0.0
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CashMemoViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = CashMemoRepository(
        db.cashMemoDao(),
        db.plantRateDao(),
        db.customerDao(),
        application
    )
    private val geminiService = GeminiService()

    private val _businessProfile = MutableStateFlow(repository.getBusinessProfile())
    val businessProfile: StateFlow<BusinessProfile> = _businessProfile.asStateFlow()

    private val _editorState = MutableStateFlow(
        MemoEditorState(
            memoNumber = _businessProfile.value.nextMemoNumber.toString(),
            taxPercentage = if (_businessProfile.value.enableTax) _businessProfile.value.defaultTaxPercentage else 0.0
        )
    )
    val editorState: StateFlow<MemoEditorState> = _editorState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val historyMemos: StateFlow<List<CashMemoEntity>> = _searchQuery.flatMapLatest { q ->
        if (q.isBlank()) repository.allCashMemos else repository.searchMemos(q)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Customer Profiles & Balance History
    val customerProfiles: StateFlow<List<CustomerProfileEntity>> = _searchQuery.flatMapLatest { q ->
        if (q.isBlank()) repository.allCustomers else repository.searchCustomers(q)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allCustomersUnfiltered: StateFlow<List<CustomerProfileEntity>> = repository.allCustomers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allBalanceHistory: StateFlow<List<CustomerBalanceHistoryEntity>> = repository.allBalanceHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _previewMemo = MutableStateFlow<CashMemoEntity?>(null)
    val previewMemo: StateFlow<CashMemoEntity?> = _previewMemo.asStateFlow()

    // Rate catalog search and list
    private val _rateSearchQuery = MutableStateFlow("")
    val rateSearchQuery: StateFlow<String> = _rateSearchQuery.asStateFlow()

    val rateCatalog: StateFlow<List<PlantRateEntity>> = _rateSearchQuery.flatMapLatest { q ->
        if (q.isBlank()) repository.allRates else repository.searchRates(q)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            repository.populateDefaultRatesIfEmpty()
            repository.syncCustomersFromExistingMemosIfEmpty()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateRateSearchQuery(query: String) {
        _rateSearchQuery.value = query
    }

    fun setCustomerName(name: String) = _editorState.update { it.copy(customerName = name) }
    fun setCustomerMobile(mobile: String) = _editorState.update { it.copy(customerMobile = mobile) }
    fun setCustomerAddress(addr: String) = _editorState.update { it.copy(customerAddress = addr) }
    fun setMemoNumber(num: String) = _editorState.update { it.copy(memoNumber = num) }
    fun setDate(date: String) = _editorState.update { it.copy(date = date) }
    fun setDiscount(discount: Double) = _editorState.update { it.copy(discount = discount) }
    fun setTaxPercentage(tax: Double) = _editorState.update { it.copy(taxPercentage = tax) }
    fun setPreviousDues(dues: Double) = _editorState.update { it.copy(previousDues = dues) }

    /**
     * Auto-fills customer details and their outstanding balance into the active Cash Memo editor.
     */
    fun selectCustomerProfileForEditor(customer: CustomerProfileEntity) {
        _editorState.update { state ->
            state.copy(
                selectedCustomerId = customer.id,
                customerName = customer.name,
                customerMobile = customer.phoneNumber,
                customerAddress = customer.address.ifBlank { state.customerAddress },
                previousDues = customer.outstandingBalance
            )
        }
    }

    fun getBalanceHistoryForCustomer(customerId: Long): Flow<List<CustomerBalanceHistoryEntity>> =
        repository.getCustomerBalanceHistory(customerId)

    fun saveOrUpdateCustomer(
        id: Long = 0,
        name: String,
        phone: String,
        address: String,
        outstandingBalance: Double,
        promiseNote: String
    ) {
        viewModelScope.launch {
            repository.saveOrUpdateCustomerProfile(
                id = id,
                name = name,
                phoneNumber = phone,
                address = address,
                outstandingBalance = outstandingBalance,
                promiseNote = promiseNote
            )
        }
    }

    fun recordDirectCustomerPayment(
        customer: CustomerProfileEntity,
        amountReceivedNow: Double,
        paymentMethod: String,
        remainingPromiseNote: String
    ) {
        viewModelScope.launch {
            repository.recordCustomerPaymentSettlement(
                customer = customer,
                amountReceivedNow = amountReceivedNow,
                paymentMethod = paymentMethod,
                remainingPromiseNote = remainingPromiseNote
            )
        }
    }

    fun deleteCustomer(customerId: Long) {
        viewModelScope.launch {
            repository.deleteCustomer(customerId)
        }
    }

    fun applyPaymentCalculation(
        billTotalOverride: Double?,
        paidAmount: Double,
        paymentMethod: String,
        paymentDueNote: String
    ) {
        _editorState.update { state ->
            val updatedItems = if (billTotalOverride != null && billTotalOverride > 0.0 && state.subtotal == 0.0) {
                val first = state.items.firstOrNull()
                if (first != null) {
                    listOf(
                        first.copy(
                            description = first.description.ifBlank { "पौधा व सामग्री (Plant Purchase)" },
                            quantity = 1.0,
                            rate = billTotalOverride,
                            amount = billTotalOverride
                        )
                    )
                } else {
                    listOf(
                        CashMemoItem(
                            id = UUID.randomUUID().toString(),
                            serialNo = 1,
                            description = "पौधा व सामग्री (Plant Purchase)",
                            quantity = 1.0,
                            rate = billTotalOverride,
                            amount = billTotalOverride
                        )
                    )
                }
            } else {
                state.items
            }

            state.copy(
                items = updatedItems,
                hasCustomPayment = true,
                paidAmount = max(0.0, paidAmount),
                paymentMethod = paymentMethod.ifBlank { "Cash" },
                paymentDueNote = paymentDueNote.trim()
            )
        }
    }

    fun resetCustomPayment() {
        _editorState.update {
            it.copy(
                hasCustomPayment = false,
                paidAmount = 0.0,
                paymentMethod = "Cash",
                paymentDueNote = "Next Day (कल देगा)"
            )
        }
    }

    fun updateSavedMemoPayment(
        memo: CashMemoEntity,
        newPaidAmount: Double,
        newPaymentMethod: String,
        newPaymentNote: String
    ) {
        viewModelScope.launch {
            val totalPayable = memo.totalPayableWithDues
            val updatedBalance = max(0.0, totalPayable - newPaidAmount)
            val finalNote = if (updatedBalance <= 0.009) "Fully Paid (चुकता)" else newPaymentNote
            val updatedMemo = memo.copy(
                paidAmount = newPaidAmount,
                balanceDue = updatedBalance,
                paymentMethod = newPaymentMethod,
                paymentStatusNote = finalNote
            )
            repository.updateMemoPaymentAndSyncCustomer(memo, updatedMemo)
            if (_previewMemo.value?.id == memo.id) {
                _previewMemo.value = updatedMemo
            }
        }
    }

    fun addItem() {
        _editorState.update { state ->
            val nextSn = state.items.size + 1
            val newItem = CashMemoItem(
                id = UUID.randomUUID().toString(),
                serialNo = nextSn,
                description = "",
                quantity = 1.0,
                rate = 0.0,
                amount = 0.0
            )
            state.copy(items = state.items + newItem)
        }
    }

    fun removeItem(index: Int) {
        _editorState.update { state ->
            if (state.items.size <= 1) {
                state.copy(items = listOf(CashMemoItem(id = UUID.randomUUID().toString(), serialNo = 1, description = "", quantity = 1.0, rate = 0.0, amount = 0.0)))
            } else {
                val updated = state.items.toMutableList().apply { removeAt(index) }
                val renumbered = updated.mapIndexed { idx, itm -> itm.copy(serialNo = idx + 1) }
                state.copy(items = renumbered)
            }
        }
    }

    fun updateItem(index: Int, description: String, quantity: Double, rate: Double) {
        _editorState.update { state ->
            if (index in state.items.indices) {
                val calculatedAmount = if (quantity > 0 && rate > 0) quantity * rate else 0.0
                val updated = state.items.toMutableList()
                val current = updated[index]
                updated[index] = current.copy(
                    description = description,
                    quantity = quantity,
                    rate = rate,
                    amount = calculatedAmount
                )
                state.copy(items = updated)
            } else state
        }
    }

    fun incrementItemQty(index: Int) {
        _editorState.update { state ->
            if (index in state.items.indices) {
                val updated = state.items.toMutableList()
                val current = updated[index]
                val newQty = current.quantity + 1.0
                updated[index] = current.copy(
                    quantity = newQty,
                    amount = if (current.rate > 0) newQty * current.rate else 0.0
                )
                state.copy(items = updated)
            } else state
        }
    }

    fun decrementItemQty(index: Int) {
        _editorState.update { state ->
            if (index in state.items.indices) {
                val updated = state.items.toMutableList()
                val current = updated[index]
                if (current.quantity > 1.0) {
                    val newQty = current.quantity - 1.0
                    updated[index] = current.copy(
                        quantity = newQty,
                        amount = if (current.rate > 0) newQty * current.rate else 0.0
                    )
                    state.copy(items = updated)
                } else {
                    if (state.items.size <= 1) {
                        state.copy(items = listOf(CashMemoItem(id = UUID.randomUUID().toString(), serialNo = 1, description = "", quantity = 1.0, rate = 0.0, amount = 0.0)))
                    } else {
                        updated.removeAt(index)
                        val renumbered = updated.mapIndexed { idx, itm -> itm.copy(serialNo = idx + 1) }
                        state.copy(items = renumbered)
                    }
                }
            } else state
        }
    }

    /**
     * Directly loads an item from the Rate Tab into the current active Cash Memo.
     */
    fun addRateItemToCurrentMemo(plant: PlantRateEntity, isWholesale: Boolean): String {
        val price = if (isWholesale) plant.wholesalePrice else plant.retailPrice
        var assignedIndex = 1

        _editorState.update { state ->
            val firstItem = state.items.firstOrNull()
            if (state.items.size == 1 && firstItem != null && firstItem.description.isBlank() && firstItem.rate == 0.0) {
                val updatedFirst = firstItem.copy(
                    description = plant.name,
                    quantity = 1.0,
                    rate = price,
                    amount = price
                )
                assignedIndex = 1
                state.copy(items = listOf(updatedFirst))
            } else {
                assignedIndex = state.items.size + 1
                val newItem = CashMemoItem(
                    id = UUID.randomUUID().toString(),
                    serialNo = assignedIndex,
                    description = plant.name,
                    quantity = 1.0,
                    rate = price,
                    amount = price
                )
                state.copy(items = state.items + newItem)
            }
        }
        val modeStr = if (isWholesale) "Wholesale" else "Retail"
        return "Added #${assignedIndex} ${plant.name} (₹$price, $modeStr) to New Memo"
    }

    // Rate catalog management
    fun addRate(name: String, retailPrice: Double, wholesalePrice: Double, category: String = "Plants") {
        viewModelScope.launch {
            val entity = PlantRateEntity(
                name = name.trim(),
                retailPrice = retailPrice,
                wholesalePrice = wholesalePrice,
                category = category.trim()
            )
            repository.saveRate(entity)
        }
    }

    fun updateRate(rate: PlantRateEntity) {
        viewModelScope.launch {
            repository.updateRate(rate)
        }
    }

    fun deleteRate(id: Long) {
        viewModelScope.launch {
            repository.deleteRate(id)
        }
    }

    suspend fun exportRatesJson(): String {
        return repository.exportRatesJson(null)
    }

    suspend fun importRatesJson(json: String): Int {
        return repository.importRatesJson(json)
    }

    suspend fun exportAllMemosJson(): String {
        return repository.exportAllMemosJson()
    }

    suspend fun importMemosJson(json: String): Int {
        return repository.importMemosJson(json)
    }

    fun exportCurrentMemoItemsJson(): String {
        val array = org.json.JSONArray()
        for (item in _editorState.value.items) {
            val obj = org.json.JSONObject()
            obj.put("serialNo", item.serialNo)
            obj.put("description", item.description)
            obj.put("quantity", item.quantity)
            obj.put("rate", item.rate)
            obj.put("amount", item.amount)
            array.put(obj)
        }
        return array.toString(2)
    }

    suspend fun exportFullBackupJson(): String {
        val root = org.json.JSONObject()
        root.put("version", 3)
        root.put("exportTime", System.currentTimeMillis())
        val rates = repository.getAllRatesDirectly()
        val ratesArr = org.json.JSONArray()
        for (r in rates) {
            val obj = org.json.JSONObject()
            obj.put("name", r.name)
            obj.put("retailPrice", r.retailPrice)
            obj.put("wholesalePrice", r.wholesalePrice)
            obj.put("category", r.category)
            ratesArr.put(obj)
        }
        root.put("rates", ratesArr)

        val memos = repository.getAllCashMemosDirectly()
        val memosArr = org.json.JSONArray()
        for (m in memos) {
            val obj = org.json.JSONObject()
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
            memosArr.put(obj)
        }
        root.put("memos", memosArr)
        return root.toString(2)
    }

    suspend fun importFullBackupJson(json: String): Pair<Int, Int> {
        val trimmed = json.trim()
        if (trimmed.startsWith("{")) {
            val root = org.json.JSONObject(trimmed)
            var rateCount = 0
            var memoCount = 0
            if (root.has("rates")) {
                rateCount = repository.importRatesJson(root.getJSONArray("rates").toString())
            }
            if (root.has("memos")) {
                memoCount = repository.importMemosJson(root.getJSONArray("memos").toString())
            }
            if (rateCount > 0 || memoCount > 0) {
                return Pair(rateCount, memoCount)
            }
        }
        val rateCount = repository.importRatesJson(json)
        return Pair(rateCount, 0)
    }

    fun resetEditor() {
        val nextNum = _businessProfile.value.nextMemoNumber.toString()
        _editorState.value = MemoEditorState(
            memoNumber = nextNum,
            taxPercentage = if (_businessProfile.value.enableTax) _businessProfile.value.defaultTaxPercentage else 0.0
        )
    }

    fun analyzeRoughBillImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _editorState.update { it.copy(isAnalyzing = true, analysisError = null, scannedBitmap = bitmap) }
            val profile = _businessProfile.value
            val result = geminiService.analyzeRoughBillImage(
                bitmap = bitmap,
                customApiKey = profile.customApiKey,
                provider = profile.selectedAiProvider,
                modelName = profile.selectedAiModel
            )
            handleAnalysisResult(result)
        }
    }

    fun analyzeRoughBillText(text: String) {
        viewModelScope.launch {
            _editorState.update { it.copy(isAnalyzing = true, analysisError = null) }
            val profile = _businessProfile.value
            val result = geminiService.analyzeRoughBillText(
                textDescription = text,
                customApiKey = profile.customApiKey,
                provider = profile.selectedAiProvider,
                modelName = profile.selectedAiModel
            )
            handleAnalysisResult(result)
        }
    }

    private fun handleAnalysisResult(result: Result<ExtractedBillResult>) {
        result.fold(
            onSuccess = { extracted ->
                _editorState.update { state ->
                    val finalItems = if (extracted.items.isNotEmpty()) {
                        extracted.items
                    } else {
                        state.items
                    }
                    state.copy(
                        isAnalyzing = false,
                        customerName = extracted.customerName.ifBlank { state.customerName },
                        customerMobile = extracted.customerMobile.ifBlank { state.customerMobile },
                        customerAddress = extracted.customerAddress.ifBlank { state.customerAddress },
                        memoNumber = extracted.memoNumber.ifBlank { state.memoNumber },
                        date = extracted.memoDate.ifBlank { state.date },
                        items = finalItems,
                        discount = if (extracted.discount > 0) extracted.discount else state.discount,
                        taxPercentage = if (extracted.taxPercentage > 0) extracted.taxPercentage else state.taxPercentage,
                        aiValidationNote = extracted.notes.ifBlank { "✓ AI validated bill calculations and items." }
                    )
                }
            },
            onFailure = { error ->
                _editorState.update {
                    it.copy(
                        isAnalyzing = false,
                        analysisError = error.message ?: "Failed to analyze bill."
                    )
                }
            }
        )
    }

    fun loadSampleRoughBill(sampleId: Int) {
        when (sampleId) {
            1 -> {
                val sampleText = """
                विशाल नर्सरी रफ काम:
                ग्राहक: रमेश कुमार, सासाराम, फोन: 9835123456
                - गुलाब का पौधा (लाल) 10 पीस @ 45 = 450
                - गेंदा पौधा 15 पीस @ 20 = 300
                - क्रोटन इंडोर 2 पीस @ 120 = 240
                - मिट्टी का गमला (12 इंच) 4 पीस @ 85 = 340
                - वर्मीकम्पोस्ट खाद 2 पैकेट @ 60 = 120
                कुल छूट: 50
                """.trimIndent()
                analyzeRoughBillText(sampleText)
            }
            2 -> {
                val sampleText = """
                Rough bill:
                Customer: Anita Devi, Sasaram
                Mob: 7004128990
                1. Mango Amrapali graft 2 @ 150 = 300
                2. Guava plant 1 @ 80 = 80
                3. Hibiscus (Gudhal) 4 @ 50 = 200
                4. Ceramic planter 2 @ 180 = 360
                5. Bone meal fertilizer 1 @ 90 = 90
                """.trimIndent()
                analyzeRoughBillText(sampleText)
            }
            3 -> {
                val sampleText = """
                Rough scratchpad:
                Santosh Verma, Dehri On Sone, 9431002233
                - Bougainvillea 5 @ 60 = 350 (note: shopkeeper rough mistake, 5x60 is 300)
                - Money plant stick 2 @ 110 = 220
                - Neem khali 3 @ 40 = 120
                """.trimIndent()
                analyzeRoughBillText(sampleText)
            }
        }
    }

    fun saveAndPreviewMemo(onSuccess: (Long) -> Unit) {
        val state = _editorState.value
        val profile = _businessProfile.value

        val activeItems = state.items.filter { it.description.isNotBlank() || (it.quantity > 0 && it.rate > 0) }
        val finalItems = if (activeItems.isNotEmpty()) {
            activeItems.mapIndexed { idx, item -> item.copy(serialNo = idx + 1) }
        } else {
            state.items
        }

        val calculatedSubtotal = finalItems.sumOf { it.amount }
        val calculatedTax = if (state.taxPercentage > 0) max(0.0, (calculatedSubtotal - state.discount) * (state.taxPercentage / 100.0)) else 0.0
        val calculatedGrandTotal = max(0.0, calculatedSubtotal - state.discount + calculatedTax)
        val calculatedTotalPayable = calculatedGrandTotal + state.previousDues

        val finalPaidAmount = if (state.hasCustomPayment) state.paidAmount else calculatedTotalPayable
        val finalBalanceDue = if (state.hasCustomPayment) max(0.0, calculatedTotalPayable - state.paidAmount) else 0.0
        val finalPaymentNote = if (state.hasCustomPayment) {
            if (finalBalanceDue > 0.009) state.paymentDueNote.ifBlank { "Next Day (कल देगा)" } else "Fully Paid (चुकता)"
        } else {
            ""
        }

        val entity = CashMemoEntity(
            memoNumber = state.memoNumber,
            date = state.date,
            customerName = state.customerName,
            customerMobile = state.customerMobile,
            customerAddress = state.customerAddress,
            itemsJson = CashMemoRepository.serializeItems(finalItems),
            subtotal = calculatedSubtotal,
            discount = state.discount,
            taxPercentage = state.taxPercentage,
            taxAmount = calculatedTax,
            previousDues = state.previousDues,
            grandTotal = calculatedGrandTotal,
            paidAmount = finalPaidAmount,
            balanceDue = finalBalanceDue,
            paymentMethod = state.paymentMethod,
            paymentStatusNote = finalPaymentNote,
            notes = state.aiValidationNote ?: "",
            businessName = profile.shopNameHindi,
            businessPhone = profile.phone,
            businessAddress = "${profile.addressLine1}, ${profile.addressLine2}".trim().trim(','),
            signerName = profile.signerName
        )

        viewModelScope.launch {
            val id = repository.saveMemo(entity)
            val savedEntity = entity.copy(id = id)
            _previewMemo.value = savedEntity
            _businessProfile.value = repository.getBusinessProfile()
            onSuccess(id)
        }
    }

    fun setPreviewMemo(memo: CashMemoEntity) {
        _previewMemo.value = memo
    }

    fun loadMemoIntoEditor(memo: CashMemoEntity) {
        val items = CashMemoRepository.deserializeItems(memo.itemsJson)
        val hasCustom = memo.balanceDue > 0.009 || (memo.paidAmount > 0.0 && memo.paidAmount != memo.totalPayableWithDues)
        _editorState.value = MemoEditorState(
            memoNumber = memo.memoNumber,
            date = memo.date,
            customerName = memo.customerName,
            customerMobile = memo.customerMobile,
            customerAddress = memo.customerAddress,
            items = if (items.isNotEmpty()) items else listOf(CashMemoItem(id = UUID.randomUUID().toString(), serialNo = 1)),
            discount = memo.discount,
            taxPercentage = memo.taxPercentage,
            previousDues = memo.previousDues,
            hasCustomPayment = hasCustom,
            paidAmount = memo.effectivePaidAmount,
            paymentMethod = memo.paymentMethod.ifBlank { "Cash" },
            paymentDueNote = memo.paymentStatusNote.ifBlank { "Next Day (कल देगा)" },
            aiValidationNote = memo.notes
        )
    }

    fun deleteMemo(id: Long) {
        viewModelScope.launch {
            repository.deleteMemo(id)
        }
    }

    fun updateBusinessProfile(profile: BusinessProfile) {
        repository.saveBusinessProfile(profile)
        _businessProfile.value = profile
    }
}
