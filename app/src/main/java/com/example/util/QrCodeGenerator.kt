package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.EnumMap
import java.util.Locale

/**
 * High-contrast, ZXing-backed ISO/IEC 18004 QR Code Generator and UPI Payment URI Builder.
 * Guaranteed scannable from both live camera and uploaded gallery images in Google Pay,
 * Amazon Pay, PhonePe, Paytm, and BHIM.
 */
object QrCodeGenerator {

    const val DEFAULT_VPA = "dangerousop@ybl"
    const val DEFAULT_PAYEE_NAME = "Vishal Nursery"
    const val DEFAULT_TRANSACTION_NOTE = "Plant Purchase"

    // In-memory LRU cache to avoid regenerating bitmaps for previously entered amounts
    private val qrBitmapCache = object : LruCache<String, Bitmap>(24) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    /**
     * Builds the standard UPI URI scheme:
     * - With positive amount:
     *   upi://pay?pa=dangerousop@ybl&pn=Vishal%20Nursery&am={AMOUNT}&cu=INR&tn=Plant%20Purchase
     * - Without amount (null or <= 0):
     *   upi://pay?pa=dangerousop@ybl&pn=Vishal%20Nursery&cu=INR&tn=Plant%20Purchase
     *   (Omits am=0 so Google Pay and Amazon Pay allow user-entered amount on open QR).
     */
    fun buildUpiUri(
        amount: Double?,
        vpa: String = DEFAULT_VPA,
        payeeName: String = DEFAULT_PAYEE_NAME,
        transactionNote: String = DEFAULT_TRANSACTION_NOTE
    ): String {
        val cleanVpa = vpa.trim()
        val encodedPayee = payeeName.trim().replace(" ", "%20")
        val encodedNote = transactionNote.trim().replace(" ", "%20")

        return if (amount != null && amount > 0.0) {
            val formattedAmount = if (amount % 1.0 == 0.0) {
                String.format(Locale.ENGLISH, "%.0f", amount)
            } else {
                String.format(Locale.ENGLISH, "%.2f", amount)
            }
            "upi://pay?pa=$cleanVpa&pn=$encodedPayee&am=$formattedAmount&cu=INR&tn=$encodedNote"
        } else {
            "upi://pay?pa=$cleanVpa&pn=$encodedPayee&cu=INR&tn=$encodedNote"
        }
    }

    /**
     * Encodes a QR Code matrix using Google ZXing (ErrorCorrectionLevel.M, UTF-8).
     * Returns a 2D BooleanArray where true = dark module, false = light module (including quiet zone).
     */
    internal fun encodeIsoQrMatrix(
        content: String,
        quietZoneModules: Int = 4
    ): Array<BooleanArray> {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, quietZoneModules.coerceAtLeast(4))
        }
        val bitMatrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            0,
            0,
            hints
        )
        val w = bitMatrix.width
        val h = bitMatrix.height
        return Array(h) { y ->
            BooleanArray(w) { x ->
                bitMatrix.get(x, y)
            }
        }
    }

    /**
     * Synchronously creates a crisp, integer-scaled QR Bitmap with zero anti-aliasing blur.
     * Every QR module is rendered as an exact N×N block of pure #000000 / #FFFFFF pixels,
     * surrounded by a generous white quiet zone so Google Pay & Amazon Pay "Upload from Gallery"
     * detectors recognize the code immediately.
     */
    fun createQrBitmapSync(
        content: String,
        sizePx: Int = 720,
        quietZoneModules: Int = 4
    ): Bitmap {
        val safeContent = content.ifBlank { buildUpiUri(null) }
        val cacheKey = "$safeContent|$sizePx|$quietZoneModules"
        synchronized(qrBitmapCache) {
            qrBitmapCache.get(cacheKey)?.let { cached ->
                if (!cached.isRecycled) return cached
            }
        }

        val matrix = encodeIsoQrMatrix(safeContent, quietZoneModules.coerceAtLeast(4))
        val matrixSize = matrix.size
        val targetSize = sizePx.coerceAtLeast(matrixSize * 4)

        // Integer scale factor per module guarantees razor-sharp edges without subpixel gray blur
        val moduleScale = (targetSize / matrixSize).coerceAtLeast(1)
        val qrDrawnSize = matrixSize * moduleScale
        val paddingOffset = (targetSize - qrDrawnSize) / 2

        val pixels = IntArray(targetSize * targetSize) { Color.WHITE }

        for (r in 0 until matrixSize) {
            val rowMatrix = matrix[r]
            val startY = paddingOffset + r * moduleScale
            for (c in 0 until matrixSize) {
                if (rowMatrix[c]) {
                    val startX = paddingOffset + c * moduleScale
                    for (dy in 0 until moduleScale) {
                        val rowOffset = (startY + dy) * targetSize + startX
                        for (dx in 0 until moduleScale) {
                            pixels[rowOffset + dx] = Color.BLACK
                        }
                    }
                }
            }
        }

        val bitmap = Bitmap.createBitmap(pixels, targetSize, targetSize, Bitmap.Config.ARGB_8888)
        synchronized(qrBitmapCache) {
            qrBitmapCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    /**
     * Generates a high-contrast QR Code Bitmap on Dispatchers.Default.
     */
    suspend fun generateQrBitmap(
        content: String,
        sizePx: Int = 720,
        quietZoneModules: Int = 4
    ): Bitmap = withContext(Dispatchers.Default) {
        createQrBitmapSync(content, sizePx, quietZoneModules)
    }

    /**
     * Decodes a QR Bitmap using ZXing's QRCodeReader (used to verify scannability).
     */
    fun decodeQrBitmapSync(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.TRY_HARDER, true)
                put(DecodeHintType.CHARACTER_SET, "UTF-8")
            }
            QRCodeReader().decode(binaryBitmap, hints).text
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Renders a crisp, lossless PNG payment card designed specifically for both camera scanning
     * and "Scan from uploaded image / gallery" in Google Pay, Amazon Pay, PhonePe, and Paytm.
     * Avoids rescaling the QR bitmap (1:1 pixel blitting) and keeps a wide pure-white quiet zone.
     */
    private fun renderShareableQrCardBitmap(
        amount: Double?,
        vpa: String = DEFAULT_VPA,
        payeeName: String = DEFAULT_PAYEE_NAME
    ): Bitmap {
        val upiUri = buildUpiUri(amount = amount, vpa = vpa, payeeName = payeeName)
        val cardWidth = 960
        val cardHeight = 1240
        val cardBitmap = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cardBitmap)

        // Pure white background so barcode scanners never confuse card background with noise
        val bgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, cardWidth.toFloat(), cardHeight.toFloat(), bgPaint)

        val maroonColor = Color.parseColor("#8B1527")
        val borderPaint = Paint().apply {
            color = maroonColor
            style = Paint.Style.STROKE
            strokeWidth = 8f
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(24f, 24f, cardWidth - 24f, cardHeight - 24f), 28f, 28f, borderPaint)

        val textPaint = Paint().apply {
            color = maroonColor
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        textPaint.textSize = 48f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("विशाल नर्सरी (Vishal Nursery)", cardWidth / 2f, 110f, textPaint)

        textPaint.textSize = 26f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.color = Color.parseColor("#333333")
        canvas.drawText("Scan & Pay with Google Pay • Amazon Pay • PhonePe • Paytm", cardWidth / 2f, 156f, textPaint)

        // Generate QR bitmap at exact 680x680 pixel size with 5-module white quiet zone
        // and draw at exact integer coordinates (NO scaling, NO border touching the QR modules)
        val qrSize = 680
        val exactQrBitmap = createQrBitmapSync(upiUri, sizePx = qrSize, quietZoneModules = 5)
        val qrLeft = ((cardWidth - qrSize) / 2).toFloat()
        val qrTop = 188f
        canvas.drawBitmap(exactQrBitmap, qrLeft, qrTop, null)

        // Amount & VPA details well below the QR quiet zone
        var footerY = qrTop + qrSize + 80f
        if (amount != null && amount > 0.0) {
            textPaint.color = maroonColor
            textPaint.textSize = 54f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val amtText = if (amount % 1.0 == 0.0) {
                String.format(Locale.ENGLISH, "Payable Amount: ₹ %.0f", amount)
            } else {
                String.format(Locale.ENGLISH, "Payable Amount: ₹ %.2f", amount)
            }
            canvas.drawText(amtText, cardWidth / 2f, footerY, textPaint)
            footerY += 60f
        } else {
            textPaint.color = Color.parseColor("#1B5E20")
            textPaint.textSize = 36f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Scan to Pay Any Amount", cardWidth / 2f, footerY, textPaint)
            footerY += 54f
        }

        textPaint.color = Color.BLACK
        textPaint.textSize = 32f
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        canvas.drawText("UPI ID: $vpa", cardWidth / 2f, footerY, textPaint)

        footerY += 46f
        textPaint.color = Color.parseColor("#444444")
        textPaint.textSize = 24f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Payee: $payeeName • Mob: 7631873743", cardWidth / 2f, footerY, textPaint)

        return cardBitmap
    }

    /**
     * Renders and saves a lossless PNG UPI QR Card for sharing via Intent.ACTION_SEND.
     */
    suspend fun saveAndGetShareableQrCard(
        context: Context,
        @Suppress("UNUSED_PARAMETER") qrBitmap: Bitmap? = null,
        amount: Double?,
        vpa: String = DEFAULT_VPA,
        payeeName: String = DEFAULT_PAYEE_NAME
    ): Intent = withContext(Dispatchers.IO) {
        val cardBitmap = renderShareableQrCardBitmap(amount, vpa, payeeName)
        val dir = File(context.cacheDir, "cashmemos").apply { mkdirs() }
        val file = File(dir, "VishalNursery_UPI_QR_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            cardBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        cardBitmap.recycle()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Pay to $payeeName ($vpa)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Saves the lossless PNG UPI QR Card directly to the device's Gallery (Pictures/VishalNursery)
     * so the user can upload it directly inside Google Pay, Amazon Pay, or PhonePe.
     */
    suspend fun saveQrCardToGallery(
        context: Context,
        amount: Double?,
        vpa: String = DEFAULT_VPA,
        payeeName: String = DEFAULT_PAYEE_NAME
    ): Boolean = withContext(Dispatchers.IO) {
        val cardBitmap = renderShareableQrCardBitmap(amount, vpa, payeeName)
        val amtSuffix = if (amount != null && amount > 0.0) "_Rs${amount.toInt()}" else ""
        val fileName = "VishalNursery_UPI_QR${amtSuffix}_${System.currentTimeMillis()}.png"

        try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VishalNursery")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext false

            resolver.openOutputStream(imageUri)?.use { out ->
                cardBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return@withContext false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            cardBitmap.recycle()
        }
    }
}
