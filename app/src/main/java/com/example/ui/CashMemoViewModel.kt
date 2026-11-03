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
import com.example.data.model.PlantRateEntity
import com.example.data.repository.CashMemoRepository
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
    val customerName: String = "",
    val customerMobile: String = "",
    val customerAddress: String = "",
    val items: List<CashMemoItem> = listOf(
        CashMemoItem(id = UUID.randomUUID().toString(), serialNo = 1, description = "", quantity = 1.0, rate = 0.0, amount = 0.0)
    ),
    val discount: Double = 0.0,
    val taxPercentage: Double = 0.0,
    val previousDues: Double = 0.0,
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
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CashMemoViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = CashMemoRepository(db.cashMemoDao(), db.plantRateDao(), application)
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
     * If the current memo has a single empty item (#1), it populates it.
     * Otherwise it adds a new entry (#2, #3, etc.)
     */
    fun addRateItemToCurrentMemo(plant: PlantRateEntity, isWholesale: Boolean): String {
        val price = if (isWholesale) plant.wholesalePrice else plant.retailPrice
        var assignedIndex = 1

        _editorState.update { state ->
            val firstItem = state.items.firstOrNull()
            if (state.items.size == 1 && firstItem != null && firstItem.description.isBlank() && firstItem.rate == 0.0) {
                // Populate existing empty first slot
                val updatedFirst = firstItem.copy(
                    description = plant.name,
                    quantity = 1.0,
                    rate = price,
                    amount = price
                )
                assignedIndex = 1
                state.copy(items = listOf(updatedFirst))
            } else {
                // Append new item
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
        root.put("version", 1)
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
