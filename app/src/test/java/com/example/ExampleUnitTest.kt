package com.example

import com.example.ai.GeminiService
import com.example.data.model.BusinessProfile
import com.example.data.model.CashMemoItem
import com.example.data.model.CustomerBalanceHistoryEntity
import com.example.data.model.CustomerProfileEntity
import com.example.ui.MemoEditorState
import com.example.util.NumberToWordsConverter
import com.example.util.QrCodeGenerator
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.EnumMap

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
    fun upiDynamicQrUri_isCorrectlyEncodedAndDecodableByZxingScanner() {
        val testAmounts = listOf(100.0, 250.50, 800.0, 1000.0, 12500.0, null)

        for (amount in testAmounts) {
            val expectedUri = QrCodeGenerator.buildUpiUri(amount = amount)
            if (amount == 1000.0) {
                assertEquals(
                    "upi://pay?pa=dangerousop@ybl&pn=Vishal%20Nursery&am=1000&cu=INR&tn=Plant%20Purchase",
                    expectedUri
                )
            } else if (amount == null) {
                assertEquals(
                    "upi://pay?pa=dangerousop@ybl&pn=Vishal%20Nursery&cu=INR&tn=Plant%20Purchase",
                    expectedUri
                )
            }

            // Encode QR Matrix and render into pixel array exactly as QrCodeGenerator does
            val matrix = QrCodeGenerator.encodeIsoQrMatrix(expectedUri, quietZoneModules = 5)
            val mSize = matrix.size
            val scale = 8
            val imgSize = mSize * scale
            val pixels = IntArray(imgSize * imgSize) { -0x1 } // WHITE (0xFFFFFFFF)
            for (r in 0 until mSize) {
                for (c in 0 until mSize) {
                    if (matrix[r][c]) {
                        for (dy in 0 until scale) {
                            for (dx in 0 until scale) {
                                pixels[(r * scale + dy) * imgSize + (c * scale + dx)] = -0x1000000 // BLACK (0xFF000000)
                            }
                        }
                    }
                }
            }

            // Decode using ZXing QRCodeReader (same engine used by Google Pay / Amazon Pay)
            val source = RGBLuminanceSource(imgSize, imgSize, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.TRY_HARDER, true)
                put(DecodeHintType.CHARACTER_SET, "UTF-8")
            }
            val decoded = QRCodeReader().decode(binaryBitmap, hints)
            assertEquals("QR code for amount=$amount must decode to exact UPI URI", expectedUri, decoded.text)
        }
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
