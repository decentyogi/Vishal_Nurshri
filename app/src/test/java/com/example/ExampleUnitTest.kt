package com.example

import com.example.ai.GeminiService
import com.example.data.model.BusinessProfile
import com.example.data.model.CashMemoItem
import com.example.data.model.CustomerBalanceHistoryEntity
import com.example.data.model.CustomerProfileEntity
import com.example.ui.MemoEditorState
import com.example.util.NumberToWordsConverter
import com.example.util.QrCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun numberToWords_isCorrect() {
        val words1 = NumberToWordsConverter.convertToIndianRupees(500.0)
        assertEquals("Five Hundred Rupees Only", words1)

        val words2 = NumberToWordsConverter.convertToIndianRupees(1250.50)
        assertEquals("One Thousand Two Hundred Fifty Rupees and Fifty Paise Only", words2)
    }

    @Test
    fun splitRupeesAndPaise_isCorrect() {
        val (rs1, ps1) = NumberToWordsConverter.splitRupeesAndPaise(450.0)
        assertEquals("450", rs1)
        assertEquals("--", ps1)

        val (rs2, ps2) = NumberToWordsConverter.splitRupeesAndPaise(340.75)
        assertEquals("340", rs2)
        assertEquals("75", ps2)
    }

    @Test
    fun businessProfile_defaultConstantsAreCorrect() {
        val profile = BusinessProfile()
        assertEquals("7631873743", profile.phone)
        assertEquals("ग्राम-कोटा, माँ ताराचण्डी रोड (माँ सीताराम मंदिर)", profile.addressLine1)
        assertEquals("पो०-सासाराम (रोहतास)", profile.addressLine2)
        assertEquals(1001, profile.nextMemoNumber)
        assertEquals("gemini-2.5-flash", profile.selectedAiModel)

        val editorState = MemoEditorState()
        assertEquals("1001", editorState.memoNumber)
    }

    @Test
    fun upiDynamicQrUri_isCorrectlyEncoded() {
        val uri1000 = QrCodeGenerator.buildUpiUri(amount = 1000.0)
        assertEquals(
            "upi://pay?pa=dangerousop@ybl&pn=Vishal%20Nursery&am=1000&cu=INR&tn=Plant%20Purchase",
            uri1000
        )

        val uri850_50 = QrCodeGenerator.buildUpiUri(amount = 850.50)
        assertEquals(
            "upi://pay?pa=dangerousop@ybl&pn=Vishal%20Nursery&am=850.50&cu=INR&tn=Plant%20Purchase",
            uri850_50
        )
    }

    @Test
    fun geminiMarkdownGuardrails_stripsCodeFencesCorrectly() {
        val rawWithFences = """
            Here is the extracted JSON:
            ```json
            {"memoNumber": "1001", "customerName": "Test", "items": []}
            ```
        """.trimIndent()

        val extracted = GeminiService().extractCleanJsonString(rawWithFences)
        assertNotNull(extracted)
        assertEquals("{\"memoNumber\": \"1001\", \"customerName\": \"Test\", \"items\": []}", extracted)
    }

    @Test
    fun partialPayment_1000Bill_800Paid_calculates200BalanceDue() {
        val state = MemoEditorState(
            items = listOf(
                CashMemoItem(serialNo = 1, description = "Mango Plant", quantity = 10.0, rate = 100.0, amount = 1000.0)
            ),
            hasCustomPayment = true,
            paidAmount = 800.0,
            paymentDueNote = "Next Day (कल देगा)"
        )

        assertEquals(1000.0, state.totalPayable, 0.001)
        assertEquals(800.0, state.effectivePaidAmount, 0.001)
        assertEquals(200.0, state.balanceDue, 0.001)
        assertEquals("Next Day (कल देगा)", state.paymentDueNote)
    }

    @Test
    fun customerProfileAndBalanceHistory_tracksOutstandingDuesCorrectly() {
        val profile = CustomerProfileEntity(
            id = 1L,
            name = "Test Customer",
            phoneNumber = "9876543210",
            address = "Sasaram",
            outstandingBalance = 200.0,
            totalPurchased = 1000.0,
            totalPaid = 800.0,
            lastPromiseNote = "Next Day (कल देगा)"
        )

        assertTrue(profile.hasPendingBalance)

        val historyEntry = CustomerBalanceHistoryEntity(
            id = 10L,
            customerId = profile.id,
            customerName = profile.name,
            customerPhone = profile.phoneNumber,
            memoId = 5L,
            memoNumber = "1001",
            transactionType = "BILL_CREATED",
            billAmount = 1000.0,
            paidAmount = 800.0,
            balanceChange = 200.0,
            resultingBalance = 200.0,
            paymentMethod = "Cash",
            note = "Next Day (कल देगा)"
        )

        assertEquals(200.0, historyEntry.resultingBalance, 0.001)
        assertEquals("1001", historyEntry.memoNumber)
    }
}
