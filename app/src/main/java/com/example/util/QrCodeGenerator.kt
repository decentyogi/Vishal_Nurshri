package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Pure-Kotlin ISO/IEC 18004 QR Code Generator and UPI Payment URI Builder.
 * Generates high-contrast QR Code bitmaps on a background thread without blocking the UI thread.
 */
object QrCodeGenerator {

    const val DEFAULT_VPA = "dangerousop@ybl"
    const val DEFAULT_PAYEE_NAME = "Vishal Nursery"
    const val DEFAULT_TRANSACTION_NOTE = "Plant Purchase"

    /**
     * Builds the exact standard UPI URI scheme:
     * upi://pay?pa=dangerousop@ybl&pn=Vishal%20Nursery&am={AMOUNT}&cu=INR&tn=Plant%20Purchase
     */
    fun buildUpiUri(
        amount: Double?,
        vpa: String = DEFAULT_VPA,
        payeeName: String = DEFAULT_PAYEE_NAME,
        transactionNote: String = DEFAULT_TRANSACTION_NOTE
    ): String {
        val encodedPayee = payeeName.trim().replace(" ", "%20")
        val encodedNote = transactionNote.trim().replace(" ", "%20")
        val formattedAmount = if (amount != null && amount > 0.0) {
            if (amount % 1.0 == 0.0) {
                String.format(Locale.ENGLISH, "%.0f", amount)
            } else {
                String.format(Locale.ENGLISH, "%.2f", amount)
            }
        } else {
            "0"
        }
        return "upi://pay?pa=${vpa.trim()}&pn=$encodedPayee&am=$formattedAmount&cu=INR&tn=$encodedNote"
    }

    /**
     * Generates a high-contrast QR Code Bitmap for the given content string on Dispatchers.Default.
     */
    suspend fun generateQrBitmap(
        content: String,
        sizePx: Int = 720,
        quietZoneModules: Int = 4
    ): Bitmap = withContext(Dispatchers.Default) {
        val matrix = encodeIsoQrMatrix(content.toByteArray(Charsets.UTF_8))
        val moduleCount = matrix.size
        val totalModules = moduleCount + (quietZoneModules * 2)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Crisp white background for high-contrast scanning
        val bgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), bgPaint)

        val darkPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = false
        }

        val moduleSize = sizePx.toFloat() / totalModules.toFloat()
        for (r in 0 until moduleCount) {
            for (c in 0 until moduleCount) {
                if (matrix[r][c]) {
                    val left = (c + quietZoneModules) * moduleSize
                    val top = (r + quietZoneModules) * moduleSize
                    canvas.drawRect(
                        left,
                        top,
                        left + moduleSize + 0.4f,
                        top + moduleSize + 0.4f,
                        darkPaint
                    )
                }
            }
        }
        bitmap
    }

    /**
     * Renders a complete shareable UPI QR Payment Card JPEG on Dispatchers.IO.
     */
    suspend fun saveAndGetShareableQrCard(
        context: Context,
        qrBitmap: Bitmap,
        amount: Double?,
        vpa: String = DEFAULT_VPA,
        payeeName: String = DEFAULT_PAYEE_NAME
    ): Intent = withContext(Dispatchers.IO) {
        val cardWidth = 900
        val cardHeight = 1180
        val cardBitmap = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cardBitmap)

        val bgPaint = Paint().apply {
            color = Color.parseColor("#FFFDF9")
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
        canvas.drawRoundRect(RectF(28f, 28f, cardWidth - 28f, cardHeight - 28f), 28f, 28f, borderPaint)

        val textPaint = Paint().apply {
            color = maroonColor
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        textPaint.textSize = 48f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("विशाल नर्सरी (Vishal Nursery)", cardWidth / 2f, 115f, textPaint)

        textPaint.textSize = 26f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.color = Color.parseColor("#4A3B3E")
        canvas.drawText("Scan & Pay with any UPI App (GPay • PhonePe • Paytm)", cardWidth / 2f, 162f, textPaint)

        // Draw QR Bitmap in center
        val qrSize = 620
        val qrLeft = (cardWidth - qrSize) / 2f
        val qrTop = 200f
        val qrDest = RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize)
        canvas.drawBitmap(qrBitmap, null, qrDest, null)

        val thinBorder = Paint().apply {
            color = Color.parseColor("#E0D4D6")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawRoundRect(qrDest, 16f, 16f, thinBorder)

        // Amount & VPA details
        var footerY = qrTop + qrSize + 75f
        if (amount != null && amount > 0.0) {
            textPaint.color = maroonColor
            textPaint.textSize = 52f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val amtText = if (amount % 1.0 == 0.0) {
                String.format(Locale.ENGLISH, "Amount: ₹ %.0f", amount)
            } else {
                String.format(Locale.ENGLISH, "Amount: ₹ %.2f", amount)
            }
            canvas.drawText(amtText, cardWidth / 2f, footerY, textPaint)
            footerY += 56f
        }

        textPaint.color = Color.parseColor("#1E191A")
        textPaint.textSize = 30f
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        canvas.drawText("UPI ID: $vpa", cardWidth / 2f, footerY, textPaint)

        footerY += 44f
        textPaint.color = Color.parseColor("#5C4346")
        textPaint.textSize = 24f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Payee: $payeeName • Mob: 7631873743", cardWidth / 2f, footerY, textPaint)

        val dir = File(context.cacheDir, "cashmemos").apply { mkdirs() }
        val file = File(dir, "VishalNursery_UPI_QR_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            cardBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        cardBitmap.recycle()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Pay to $payeeName ($vpa)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // =========================================================================
    // ISO/IEC 18004 QR Code Encoder (Byte Mode, EC Level L, Versions 1..10)
    // =========================================================================

    private data class VersionSpec(
        val version: Int,
        val totalDataCodewords: Int,
        val ecCodewordsPerBlock: Int,
        val numBlocksGroup1: Int,
        val dataCodewordsGroup1: Int,
        val numBlocksGroup2: Int = 0,
        val dataCodewordsGroup2: Int = 0,
        val alignmentCenters: IntArray = intArrayOf()
    )

    // Standard QR Code Version 1-10 specifications at Error Correction Level L
    private val VERSION_SPECS_L = listOf(
        VersionSpec(1, 19, 7, 1, 19),
        VersionSpec(2, 34, 10, 1, 34, alignmentCenters = intArrayOf(6, 18)),
        VersionSpec(3, 55, 15, 1, 55, alignmentCenters = intArrayOf(6, 22)),
        VersionSpec(4, 80, 20, 1, 80, alignmentCenters = intArrayOf(6, 26)),
        VersionSpec(5, 108, 26, 1, 108, alignmentCenters = intArrayOf(6, 30)),
        VersionSpec(6, 136, 18, 2, 68, alignmentCenters = intArrayOf(6, 34)),
        VersionSpec(7, 156, 20, 2, 78, alignmentCenters = intArrayOf(6, 22, 38)),
        VersionSpec(8, 194, 24, 2, 97, alignmentCenters = intArrayOf(6, 24, 42)),
        VersionSpec(9, 232, 30, 2, 116, alignmentCenters = intArrayOf(6, 26, 46)),
        VersionSpec(10, 274, 18, 2, 68, 2, 69, alignmentCenters = intArrayOf(6, 28, 50))
    )

    internal fun encodeIsoQrMatrix(data: ByteArray): Array<BooleanArray> {
        val spec = VERSION_SPECS_L.firstOrNull { v ->
            val countBits = if (v.version <= 9) 8 else 16
            val availableBits = v.totalDataCodewords * 8
            4 + countBits + (data.size * 8) <= availableBits
        } ?: VERSION_SPECS_L.last()

        val dataCodewords = buildDataCodewords(data, spec)
        val finalMessage = interleaveWithEc(dataCodewords, spec)

        val size = 17 + 4 * spec.version
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        placeFunctionPatterns(modules, isFunction, spec)
        placeCodewords(modules, isFunction, finalMessage)

        // Choose mask with lowest penalty score
        var bestMask = 0
        var lowestPenalty = Int.MAX_VALUE
        var bestGrid = modules

        for (mask in 0..7) {
            val copy = Array(size) { r -> modules[r].clone() }
            applyMask(copy, isFunction, mask)
            drawFormatBits(copy, isFunction, mask)
            val penalty = calculatePenalty(copy)
            if (penalty < lowestPenalty) {
                lowestPenalty = penalty
                bestMask = mask
                bestGrid = copy
            }
        }

        // Ensure format bits are drawn for the winning mask
        drawFormatBits(bestGrid, isFunction, bestMask)
        return bestGrid
    }

    private fun buildDataCodewords(data: ByteArray, spec: VersionSpec): IntArray {
        val capacityBits = spec.totalDataCodewords * 8
        val bits = ArrayList<Int>(capacityBits)

        fun appendBits(value: Int, len: Int) {
            for (i in len - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }

        // Byte mode indicator = 0100 (4)
        appendBits(0b0100, 4)
        val countBits = if (spec.version <= 9) 8 else 16
        appendBits(data.size, countBits)

        for (b in data) {
            appendBits(b.toInt() and 0xFF, 8)
        }

        // Terminator (up to 4 zero bits)
        val terminatorLen = minOf(4, capacityBits - bits.size)
        appendBits(0, terminatorLen)

        // Pad to byte boundary
        while (bits.size % 8 != 0) {
            bits.add(0)
        }

        // Pad bytes 0xEC, 0x11 alternately
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bits.size < capacityBits) {
            appendBits(padBytes[padIdx % 2], 8)
            padIdx++
        }

        val codewords = IntArray(spec.totalDataCodewords)
        for (i in codewords.indices) {
            var byteVal = 0
            for (b in 0..7) {
                byteVal = (byteVal shl 1) or bits[i * 8 + b]
            }
            codewords[i] = byteVal
        }
        return codewords
    }

    private fun interleaveWithEc(dataCodewords: IntArray, spec: VersionSpec): IntArray {
        val dataBlocks = ArrayList<IntArray>()
        val ecBlocks = ArrayList<IntArray>()
        var offset = 0

        repeat(spec.numBlocksGroup1) {
            val block = dataCodewords.copyOfRange(offset, offset + spec.dataCodewordsGroup1)
            offset += spec.dataCodewordsGroup1
            dataBlocks.add(block)
            ecBlocks.add(computeReedSolomonEc(block, spec.ecCodewordsPerBlock))
        }
        repeat(spec.numBlocksGroup2) {
            val block = dataCodewords.copyOfRange(offset, offset + spec.dataCodewordsGroup2)
            offset += spec.dataCodewordsGroup2
            dataBlocks.add(block)
            ecBlocks.add(computeReedSolomonEc(block, spec.ecCodewordsPerBlock))
        }

        val totalBlocks = dataBlocks.size
        val maxDataLen = maxOf(spec.dataCodewordsGroup1, spec.dataCodewordsGroup2)
        val result = ArrayList<Int>(spec.totalDataCodewords + totalBlocks * spec.ecCodewordsPerBlock)

        for (i in 0 until maxDataLen) {
            for (b in dataBlocks) {
                if (i < b.size) result.add(b[i])
            }
        }
        for (i in 0 until spec.ecCodewordsPerBlock) {
            for (eb in ecBlocks) {
                result.add(eb[i])
            }
        }
        return result.toIntArray()
    }

    private fun computeReedSolomonEc(data: IntArray, ecLen: Int): IntArray {
        // Generator polynomial in GF(256) with primitive polynomial 0x11D
        var gen = intArrayOf(1)
        var root = 1
        repeat(ecLen) {
            val nextGen = IntArray(gen.size + 1)
            for (i in gen.indices) {
                nextGen[i] = nextGen[i] xor gen[i]
                nextGen[i + 1] = nextGen[i + 1] xor gfMultiply(gen[i], root)
            }
            gen = nextGen
            root = gfMultiply(root, 2)
        }

        val remainder = IntArray(ecLen)
        for (byteVal in data) {
            val factor = byteVal xor remainder[0]
            System.arraycopy(remainder, 1, remainder, 0, ecLen - 1)
            remainder[ecLen - 1] = 0
            for (i in 0 until ecLen) {
                remainder[i] = remainder[i] xor gfMultiply(gen[i + 1], factor)
            }
        }
        return remainder
    }

    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        var a = x
        var b = y
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((b ushr i) and 1) * a)
        }
        return z
    }

    private fun placeFunctionPatterns(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        spec: VersionSpec
    ) {
        val size = modules.size

        fun setFunctionModule(r: Int, c: Int, isDark: Boolean) {
            if (r in 0 until size && c in 0 until size) {
                modules[r][c] = isDark
                isFunction[r][c] = true
            }
        }

        // Finder patterns + separators
        fun drawFinderPattern(centerR: Int, centerC: Int) {
            for (dr in -4..4) {
                for (dc in -4..4) {
                    val dist = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc))
                    val isDark = dist != 2 && dist != 4
                    setFunctionModule(centerR + dr, centerC + dc, isDark)
                }
            }
        }

        drawFinderPattern(3, 3)
        drawFinderPattern(3, size - 4)
        drawFinderPattern(size - 4, 3)

        // Timing patterns
        for (i in 0 until size) {
            if (!isFunction[6][i]) setFunctionModule(6, i, i % 2 == 0)
            if (!isFunction[i][6]) setFunctionModule(i, 6, i % 2 == 0)
        }

        // Alignment patterns
        val centers = spec.alignmentCenters
        for (r in centers) {
            for (c in centers) {
                if ((r == 6 && c == 6) || (r == 6 && c == size - 7) || (r == size - 7 && c == 6)) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val dist = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc))
                        setFunctionModule(r + dr, c + dc, dist != 1)
                    }
                }
            }
        }

        // Reserve format info areas & dark module
        for (i in 0..8) {
            if (i != 6) {
                isFunction[8][i] = true
                isFunction[i][8] = true
            }
        }
        for (i in 0..7) {
            isFunction[8][size - 1 - i] = true
            isFunction[size - 1 - i][8] = true
        }
        setFunctionModule(size - 8, 8, true) // Always dark module

        // Version info for version >= 7
        if (spec.version >= 7) {
            var rem = spec.version
            repeat(12) {
                rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
            }
            val bits = (spec.version shl 12) or rem
            for (i in 0 until 18) {
                val bit = ((bits ushr i) and 1) != 0
                val a = size - 11 + (i % 3)
                val b = i / 3
                setFunctionModule(a, b, bit)
                setFunctionModule(b, a, bit)
            }
        }
    }

    private fun placeCodewords(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        codewords: IntArray
    ) {
        val size = modules.size
        var bitIndex = 0
        val totalBits = codewords.size * 8

        var right = size - 1
        var upward = true
        while (right >= 1) {
            if (right == 6) right = 5 // Skip vertical timing pattern column
            for (vert in 0 until size) {
                val r = if (upward) size - 1 - vert else vert
                for (j in 0..1) {
                    val c = right - j
                    if (!isFunction[r][c]) {
                        val dark = if (bitIndex < totalBits) {
                            ((codewords[bitIndex ushr 3] ushr (7 - (bitIndex and 7))) and 1) != 0
                        } else {
                            false
                        }
                        modules[r][c] = dark
                        bitIndex++
                    }
                }
            }
            upward = !upward
            right -= 2
        }
    }

    private fun applyMask(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        mask: Int
    ) {
        val size = modules.size
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (!isFunction[r][c]) {
                    val invert = when (mask) {
                        0 -> (r + c) % 2 == 0
                        1 -> r % 2 == 0
                        2 -> c % 3 == 0
                        3 -> (r + c) % 3 == 0
                        4 -> (r / 2 + c / 3) % 2 == 0
                        5 -> (r * c) % 2 + (r * c) % 3 == 0
                        6 -> ((r * c) % 2 + (r * c) % 3) % 2 == 0
                        else -> ((r + c) % 2 + (r * c) % 3) % 2 == 0
                    }
                    if (invert) modules[r][c] = !modules[r][c]
                }
            }
        }
    }

    private fun drawFormatBits(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        mask: Int
    ) {
        val size = modules.size
        // EC Level L = 01
        val data = (0b01 shl 3) or mask
        var rem = data
        repeat(10) {
            rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        }
        val bits = ((data shl 10) or rem) xor 0x5412

        fun getBit(idx: Int): Boolean = ((bits ushr idx) and 1) != 0

        // First copy around top-left finder
        for (i in 0..5) modules[8][i] = getBit(i)
        modules[8][7] = getBit(6)
        modules[8][8] = getBit(7)
        modules[7][8] = getBit(8)
        for (i in 9..14) modules[14 - i][8] = getBit(i)

        // Second copy around top-right and bottom-left finders
        for (i in 0..7) modules[size - 1 - i][8] = getBit(i)
        for (i in 8..14) modules[8][size - 15 + i] = getBit(i)

        isFunction[8][8] = true
    }

    private fun calculatePenalty(modules: Array<BooleanArray>): Int {
        val size = modules.size
        var penalty = 0

        // Rule 1: 5+ consecutive same-colored modules in row/col
        for (r in 0 until size) {
            var runColor = modules[r][0]
            var runLen = 1
            for (c in 1 until size) {
                if (modules[r][c] == runColor) {
                    runLen++
                    if (runLen == 5) penalty += 3
                    else if (runLen > 5) penalty += 1
                } else {
                    runColor = modules[r][c]
                    runLen = 1
                }
            }
        }
        for (c in 0 until size) {
            var runColor = modules[0][c]
            var runLen = 1
            for (r in 1 until size) {
                if (modules[r][c] == runColor) {
                    runLen++
                    if (runLen == 5) penalty += 3
                    else if (runLen > 5) penalty += 1
                } else {
                    runColor = modules[r][c]
                    runLen = 1
                }
            }
        }

        // Rule 2: 2x2 blocks of same color
        for (r in 0 until size - 1) {
            for (c in 0 until size - 1) {
                val color = modules[r][c]
                if (modules[r][c + 1] == color &&
                    modules[r + 1][c] == color &&
                    modules[r + 1][c + 1] == color
                ) {
                    penalty += 3
                }
            }
        }
        return penalty
    }
}
