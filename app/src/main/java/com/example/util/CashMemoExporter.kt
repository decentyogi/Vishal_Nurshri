package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.data.model.BusinessProfile
import com.example.data.model.CashMemoEntity
import com.example.data.model.CashMemoItem
import com.example.data.repository.CashMemoRepository
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.max

object CashMemoExporter {

    private const val INK_MAROON = 0xFF8B1527.toInt()
    private const val PAPER_CREAM = 0xFFFFFDF9.toInt()
    private const val ITEMS_PER_PAGE = 15

    /**
     * Splits memo items into pages of max 15 items.
     * Each page is rendered as an authentic cash memo slip bitmap.
     */
    fun renderCashMemoPages(
        memo: CashMemoEntity,
        profile: BusinessProfile,
        width: Int = 1200
    ): List<Bitmap> {
        val allItems = CashMemoRepository.deserializeItems(memo.itemsJson)
        val chunked = if (allItems.isEmpty()) listOf(emptyList()) else allItems.chunked(ITEMS_PER_PAGE)
        val totalPages = chunked.size

        return chunked.mapIndexed { pageIndex, pageItems ->
            renderSinglePageBitmap(
                memo = memo,
                profile = profile,
                pageItems = pageItems,
                pageIndex = pageIndex,
                totalPages = totalPages,
                width = width
            )
        }
    }

    private fun renderSinglePageBitmap(
        memo: CashMemoEntity,
        profile: BusinessProfile,
        pageItems: List<CashMemoItem>,
        pageIndex: Int,
        totalPages: Int,
        width: Int = 1200
    ): Bitmap {
        val isLastPage = pageIndex >= totalPages - 1
        val rowCount = ITEMS_PER_PAGE // Always 15 rows by default to maintain identical page height and width

        val headerHeight = 440
        val metaHeight = 220
        val tableHeaderHeight = 65
        val rowHeight = 62
        val footerHeight = 440
        val totalHeight = headerHeight + metaHeight + tableHeaderHeight + (rowCount * rowHeight) + footerHeight

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw Paper Background
        val bgPaint = Paint().apply {
            color = PAPER_CREAM
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), bgPaint)

        // Outer Borders
        val margin = 36f
        val innerMargin = 44f
        val borderPaint = Paint().apply {
            color = INK_MAROON
            style = Paint.Style.STROKE
            strokeWidth = 5f
            isAntiAlias = true
        }
        val thinBorderPaint = Paint().apply {
            color = INK_MAROON
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }

        canvas.drawRect(margin, margin, width - margin, totalHeight - margin, borderPaint)
        canvas.drawRect(innerMargin, innerMargin, width - innerMargin, totalHeight - innerMargin, thinBorderPaint)

        // Subtle floral watermark in background
        drawSubtleWatermark(canvas, width, totalHeight)

        val textPaint = Paint().apply {
            color = INK_MAROON
            isAntiAlias = true
        }

        var currentY = innerMargin + 35f

        // Top Row: Mobile number & Floral Logos (Matching img 1.jpg)
        textPaint.textSize = 28f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val phoneText = "Mob. ${profile.phone}"
        val phoneWidth = textPaint.measureText(phoneText)
        canvas.drawText(phoneText, (width - phoneWidth) / 2f, currentY, textPaint)

        // Draw Left Flower and Right Lotus/Flower Icons in Top Row (matching img 1.jpg)
        drawFlowerIcon(canvas, innerMargin + 50f, currentY - 8f, 22f, borderPaint)
        drawLotusIcon(canvas, width - innerMargin - 50f, currentY - 8f, 22f, borderPaint)

        // Clean vertical separation so phone number NEVER touches or overlays with Shop Name
        currentY += 82f

        // Shop Name in Hindi (prominent)
        textPaint.textSize = 66f
        textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val shopTitle = profile.shopNameHindi
        val titleWidth = textPaint.measureText(shopTitle)
        val titleX = (width - titleWidth) / 2f
        canvas.drawText(shopTitle, titleX, currentY, textPaint)

        // Draw flanking flower logos beside Shop Title (matching img 2.jpg marked boxes)
        val flowerRadius = 26f
        drawFlowerIcon(canvas, titleX - flowerRadius - 26f, currentY - 22f, flowerRadius, borderPaint)
        drawFlowerIcon(canvas, titleX + titleWidth + flowerRadius + 26f, currentY - 22f, flowerRadius, borderPaint)

        currentY += 45f

        // English Shop Name
        if (profile.shopNameEnglish.isNotBlank() && profile.shopNameEnglish != profile.shopNameHindi) {
            textPaint.textSize = 30f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val enName = "(${profile.shopNameEnglish})"
            val enWidth = textPaint.measureText(enName)
            canvas.drawText(enName, (width - enWidth) / 2f, currentY, textPaint)
            currentY += 40f
        }

        // Address Lines
        textPaint.textSize = 26f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        if (profile.addressLine1.isNotBlank()) {
            val addr1 = profile.addressLine1
            canvas.drawText(addr1, (width - textPaint.measureText(addr1)) / 2f, currentY, textPaint)
            currentY += 34f
        }
        if (profile.addressLine2.isNotBlank()) {
            val addr2 = profile.addressLine2
            canvas.drawText(addr2, (width - textPaint.measureText(addr2)) / 2f, currentY, textPaint)
            currentY += 34f
        }

        // Tagline
        if (profile.tagline.isNotBlank()) {
            textPaint.textSize = 22f
            textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            val lines = splitIntoLines(profile.tagline, textPaint, width - 2 * innerMargin - 60f)
            for (line in lines) {
                canvas.drawText(line, (width - textPaint.measureText(line)) / 2f, currentY, textPaint)
                currentY += 28f
            }
        }

        currentY += 15f
        canvas.drawLine(innerMargin + 20f, currentY, width - innerMargin - 20f, currentY, borderPaint)
        currentY += 35f

        // Meta Info: Memo Number & Date & Page
        textPaint.textSize = 28f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val memoLabel = "नं० "
        canvas.drawText(memoLabel, innerMargin + 25f, currentY, textPaint)

        val memoNumText = if (totalPages > 1) "${memo.memoNumber} (Page ${pageIndex + 1}/$totalPages)" else memo.memoNumber
        val memoBoxX = innerMargin + 25f + textPaint.measureText(memoLabel) + 10f
        val numPaint = Paint().apply {
            color = INK_MAROON
            textSize = 28f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        val numWidth = numPaint.measureText(memoNumText)
        val numBoxRect = RectF(memoBoxX - 10f, currentY - 30f, memoBoxX + numWidth + 15f, currentY + 10f)
        canvas.drawRoundRect(numBoxRect, 6f, 6f, thinBorderPaint)
        canvas.drawText(memoNumText, memoBoxX, currentY, numPaint)

        // Date on Right
        val dateText = "दिनांक: ${memo.date}"
        val dateWidth = textPaint.measureText(dateText)
        canvas.drawText(dateText, width - innerMargin - dateWidth - 25f, currentY, textPaint)

        currentY += 45f

        // Customer Name & Mobile
        val custLabel = "नाम: "
        canvas.drawText(custLabel, innerMargin + 25f, currentY, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val custNameVal = memo.customerName.ifBlank { "..........................................................." }
        canvas.drawText(custNameVal, innerMargin + 25f + textPaint.measureText(custLabel), currentY, textPaint)

        if (memo.customerMobile.isNotBlank()) {
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val mobText = "मो०: ${memo.customerMobile}"
            val mobWidth = textPaint.measureText(mobText)
            canvas.drawText(mobText, width - innerMargin - mobWidth - 25f, currentY, textPaint)
        }

        currentY += 40f

        // Address
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val addrLabel = "पता: "
        canvas.drawText(addrLabel, innerMargin + 25f, currentY, textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val addrVal = memo.customerAddress.ifBlank { "................................................................................................" }
        canvas.drawText(addrVal, innerMargin + 25f + textPaint.measureText(addrLabel), currentY, textPaint)

        currentY += 35f

        // TABLE SECTION
        val tableLeft = innerMargin + 15f
        val tableRight = width - innerMargin - 15f
        val tableTop = currentY

        val colSnW = 70f
        val colQtyW = 95f
        val colRateW = 120f
        val colRsW = 150f
        val colPsW = 80f
        val colDescW = (tableRight - tableLeft) - (colSnW + colQtyW + colRateW + colRsW + colPsW)

        val xSn = tableLeft
        val xDesc = xSn + colSnW
        val xQty = xDesc + colDescW
        val xRate = xQty + colQtyW
        val xRs = xRate + colRateW
        val xPs = xRs + colRsW

        // Table Header Background
        val thBgPaint = Paint().apply {
            color = INK_MAROON
            style = Paint.Style.FILL
        }
        canvas.drawRect(tableLeft, tableTop, tableRight, tableTop + tableHeaderHeight, thBgPaint)

        // Table Header Text
        val thTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        drawCenteredText(canvas, "क्रम.", xSn, xDesc, tableTop + 42f, thTextPaint)
        drawCenteredText(canvas, "सामग्री / पौधा का विवरण", xDesc, xQty, tableTop + 42f, thTextPaint)
        drawCenteredText(canvas, "संख्या", xQty, xRate, tableTop + 42f, thTextPaint)
        drawCenteredText(canvas, "दाम (दर)", xRate, xRs, tableTop + 42f, thTextPaint)
        drawCenteredText(canvas, "रू०", xRs, xPs, tableTop + 42f, thTextPaint)
        drawCenteredText(canvas, "पै०", xPs, tableRight, tableTop + 42f, thTextPaint)

        var rowY = tableTop + tableHeaderHeight

        val cellTextPaint = Paint().apply {
            color = INK_MAROON
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val numTextPaint = Paint().apply {
            color = INK_MAROON
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        for (i in 0 until rowCount) {
            val item = pageItems.getOrNull(i)
            val nextRowY = rowY + rowHeight

            canvas.drawLine(tableLeft, nextRowY, tableRight, nextRowY, thinBorderPaint)

            if (item != null && item.description.isNotBlank()) {
                val textBaseline = rowY + 40f
                // S.No
                drawCenteredText(canvas, item.serialNo.toString(), xSn, xDesc, textBaseline, numTextPaint)
                // Description
                val truncatedDesc = ellipsize(item.description, cellTextPaint, colDescW - 30f)
                canvas.drawText(truncatedDesc, xDesc + 18f, textBaseline, cellTextPaint)
                // Qty
                val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                drawCenteredText(canvas, qtyStr, xQty, xRate, textBaseline, numTextPaint)
                // Rate
                val rateStr = String.format(Locale.ENGLISH, "%.2f", item.rate)
                drawCenteredText(canvas, rateStr, xRate, xRs, textBaseline, numTextPaint)
                // Rupees & Paise
                val (rs, ps) = NumberToWordsConverter.splitRupeesAndPaise(item.amount)
                drawCenteredText(canvas, rs, xRs, xPs, textBaseline, numTextPaint)
                drawCenteredText(canvas, ps, xPs, tableRight, textBaseline, numTextPaint)
            } else {
                // Empty row to maintain 15 entries on every page
                val textBaseline = rowY + 40f
                val lineSn = (pageIndex * ITEMS_PER_PAGE + i + 1).toString()
                val emptySnPaint = Paint(numTextPaint).apply { alpha = 120 }
                drawCenteredText(canvas, lineSn, xSn, xDesc, textBaseline, emptySnPaint)
                val emptyDashPaint = Paint(cellTextPaint).apply { alpha = 90 }
                drawCenteredText(canvas, "--", xRs, xPs, textBaseline, emptyDashPaint)
                drawCenteredText(canvas, "--", xPs, tableRight, textBaseline, emptyDashPaint)
            }

            rowY = nextRowY
        }

        // Draw vertical column dividers
        canvas.drawLine(xDesc, tableTop, xDesc, rowY, borderPaint)
        canvas.drawLine(xQty, tableTop, xQty, rowY, thinBorderPaint)
        canvas.drawLine(xRate, tableTop, xRate, rowY, thinBorderPaint)
        canvas.drawLine(xRs, tableTop, xRs, rowY, thinBorderPaint)
        canvas.drawLine(xPs, tableTop, xPs, rowY, thinBorderPaint)

        // Outer table borders
        canvas.drawRect(tableLeft, tableTop, tableRight, rowY, borderPaint)

        // TOTAL SECTION
        val totalRowHeight = 65f
        val totalTop = rowY
        val totalBottom = totalTop + totalRowHeight

        canvas.drawRect(tableLeft, totalTop, tableRight, totalBottom, borderPaint)
        canvas.drawLine(xRs, totalTop, xRs, totalBottom, thinBorderPaint)
        canvas.drawLine(xPs, totalTop, xPs, totalBottom, thinBorderPaint)

        textPaint.textSize = 28f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val totalLabel = if (!isLastPage) {
            "आगे ले जाया गया (Carried Over P${pageIndex + 2}) :-"
        } else if (memo.previousDues > 0) {
            "कुल देय (Total Payable inc. Dues) :-"
        } else {
            "टोटल (Grand Total) :-"
        }
        canvas.drawText(totalLabel, xDesc + 40f, totalTop + 44f, textPaint)

        val amountForTotal = if (isLastPage) memo.totalPayableWithDues else pageItems.sumOf { it.amount }
        val (totalRs, totalPs) = NumberToWordsConverter.splitRupeesAndPaise(amountForTotal)
        numTextPaint.textSize = 28f
        drawCenteredText(canvas, totalRs, xRs, xPs, totalTop + 44f, numTextPaint)
        drawCenteredText(canvas, totalPs, xPs, tableRight, totalTop + 44f, numTextPaint)

        currentY = totalBottom + 45f

        // Amount in words: Explicit separate paints and generous gap to prevent text overlay
        val labelPaint = Paint().apply {
            color = INK_MAROON
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val label = "शब्दों में: "
        val labelWidth = labelPaint.measureText(label)
        canvas.drawText(label, innerMargin + 25f, currentY, labelPaint)

        val wordsPaint = Paint().apply {
            color = INK_MAROON
            textSize = 23f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        val wordsText = NumberToWordsConverter.convertToIndianRupees(amountForTotal)
        val wordsStartX = innerMargin + 25f + labelWidth + 18f
        val maxWordsWidth = width - innerMargin - 25f - wordsStartX
        val wordsLines = splitIntoLines(wordsText, wordsPaint, maxWordsWidth)
        if (wordsLines.isNotEmpty()) {
            canvas.drawText(wordsLines[0], wordsStartX, currentY, wordsPaint)
            for (i in 1 until wordsLines.size) {
                currentY += 30f
                canvas.drawText(wordsLines[i], wordsStartX, currentY, wordsPaint)
            }
        }

        currentY += 40f

        // Optional Tax / Discount / Dues / Partial Payment breakdown note
        if (isLastPage && (memo.discount > 0 || memo.taxPercentage > 0 || memo.previousDues > 0)) {
            textPaint.textSize = 20f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            val subDetails = StringBuilder("Subtotal: ₹ ${String.format(Locale.ENGLISH, "%.2f", memo.subtotal)}")
            if (memo.discount > 0) {
                subDetails.append(" | छूट: -₹ ${String.format(Locale.ENGLISH, "%.2f", memo.discount)}")
            }
            if (memo.taxPercentage > 0) {
                subDetails.append(" | GST (${memo.taxPercentage}%): +₹ ${String.format(Locale.ENGLISH, "%.2f", memo.taxAmount)}")
            }
            if (memo.previousDues > 0) {
                subDetails.append(" | पिछला बकाया (Dues): +₹ ${String.format(Locale.ENGLISH, "%.2f", memo.previousDues)}")
            }
            canvas.drawText(subDetails.toString(), innerMargin + 25f, currentY, textPaint)
            currentY += 30f
        }

        if (isLastPage && memo.effectiveBalanceDue > 0.009) {
            textPaint.textSize = 21f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val dueDetails = buildString {
                append("जमा राशि (Paid): ₹ ${String.format(Locale.ENGLISH, "%.2f", memo.effectivePaidAmount)}")
                append(" | शेष बकाया (Balance Due): ₹ ${String.format(Locale.ENGLISH, "%.2f", memo.effectiveBalanceDue)}")
                if (memo.paymentStatusNote.isNotBlank()) {
                    append(" • [${memo.paymentStatusNote}]")
                }
            }
            canvas.drawText(dueDetails, innerMargin + 25f, currentY, textPaint)
            currentY += 32f
        }

        // Bottom Signature Section (Shifted up neatly above bottom border as marked in img 2.jpg)
        val signY = totalHeight - innerMargin - 150f

        textPaint.textSize = 23f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("भूल-चूक लेनी-देनी (E. & O.E.)", innerMargin + 25f, signY + 35f, textPaint)

        // Draw Digital Signature Stamp Box (Matching img 1.jpg and img 2.jpg)
        val signer = profile.signerName.ifBlank { "VISHAL NURSERY" }.uppercase()
        val stampWidth = 360f
        val stampHeight = 92f
        val stampRight = width - innerMargin - 25f
        val stampLeft = stampRight - stampWidth
        val stampTop = signY - 25f
        val stampBottom = stampTop + stampHeight
        val stampCenterX = (stampLeft + stampRight) / 2f

        val stampBoxRect = RectF(stampLeft, stampTop, stampRight, stampBottom)
        val stampBgPaint = Paint().apply {
            color = Color.parseColor("#F9F6F0")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(stampBoxRect, 8f, 8f, stampBgPaint)
        canvas.drawRoundRect(stampBoxRect, 8f, 8f, thinBorderPaint)

        // Center-aligned text inside stamp: "Digitally Signed", "BY [Signer]", "Date - dd/MM/yyyy"
        val stampTextPaint = Paint().apply {
            color = INK_MAROON
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        stampTextPaint.textSize = 21f
        stampTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("✓ Digitally Signed", stampCenterX, stampTop + 28f, stampTextPaint)

        stampTextPaint.textSize = 20f
        stampTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("BY $signer", stampCenterX, stampTop + 54f, stampTextPaint)

        stampTextPaint.textSize = 18f
        stampTextPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        canvas.drawText("Date - ${memo.date}", stampCenterX, stampTop + 78f, stampTextPaint)

        // Center Authorized Signatory label directly under the stamp box
        stampTextPaint.textSize = 18f
        stampTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val authLabel = "हस्ताक्षर (Authorized Signatory)"
        canvas.drawText(authLabel, stampCenterX, stampBottom + 28f, stampTextPaint)

        return bitmap
    }

    private fun drawFlowerIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val flowerPaint = Paint(paint).apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        // 6 circular petals around center (matching LocalFlorist icon)
        val petalR = radius * 0.42f
        val dist = radius * 0.62f
        for (i in 0 until 6) {
            val angle = Math.toRadians(i * 60.0)
            val px = cx + (dist * Math.cos(angle)).toFloat()
            val py = cy + (dist * Math.sin(angle)).toFloat()
            canvas.drawCircle(px, py, petalR, flowerPaint)
        }
        // Center circle
        canvas.drawCircle(cx, cy, radius * 0.4f, flowerPaint)
        // Inner cream contrast dot
        val innerDotPaint = Paint().apply {
            color = PAPER_CREAM
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(cx, cy, radius * 0.2f, innerDotPaint)
    }

    private fun drawLotusIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val lotusPaint = Paint(paint).apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val h = size * 1.2f
        val w = size * 1.1f

        // Center petal
        val centerPetal = Path().apply {
            moveTo(cx, cy + h * 0.45f)
            quadTo(cx - w * 0.28f, cy, cx, cy - h * 0.48f)
            quadTo(cx + w * 0.28f, cy, cx, cy + h * 0.45f)
            close()
        }
        canvas.drawPath(centerPetal, lotusPaint)

        // Left petal
        val leftPetal = Path().apply {
            moveTo(cx - w * 0.1f, cy + h * 0.42f)
            quadTo(cx - w * 0.6f, cy + h * 0.15f, cx - w * 0.5f, cy - h * 0.2f)
            quadTo(cx - w * 0.2f, cy - h * 0.05f, cx - w * 0.08f, cy + h * 0.35f)
            close()
        }
        canvas.drawPath(leftPetal, lotusPaint)

        // Right petal
        val rightPetal = Path().apply {
            moveTo(cx + w * 0.1f, cy + h * 0.42f)
            quadTo(cx + w * 0.6f, cy + h * 0.15f, cx + w * 0.5f, cy - h * 0.2f)
            quadTo(cx + w * 0.2f, cy - h * 0.05f, cx + w * 0.08f, cy + h * 0.35f)
            close()
        }
        canvas.drawPath(rightPetal, lotusPaint)
    }

    private fun drawCenteredText(canvas: Canvas, text: String, left: Float, right: Float, y: Float, paint: Paint) {
        val w = paint.measureText(text)
        val x = left + (right - left - w) / 2f
        canvas.drawText(text, x, y, paint)
    }

    private fun drawSubtleWatermark(canvas: Canvas, width: Int, height: Int) {
        val watermarkPaint = Paint().apply {
            color = INK_MAROON
            alpha = 15
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val path = Path().apply {
            moveTo(centerX - 100f, centerY + 180f)
            quadTo(centerX - 20f, centerY + 50f, centerX, centerY - 150f)
            quadTo(centerX + 20f, centerY - 250f, centerX + 60f, centerY - 320f)
        }
        canvas.drawPath(path, watermarkPaint)

        for (i in -2..2) {
            val ly = centerY + (i * 90f)
            val lx = centerX + (if (i % 2 == 0) -40f else 40f)
            canvas.drawOval(RectF(lx - 45f, ly - 20f, lx + 45f, ly + 20f), watermarkPaint)
        }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return if (end > 0) text.substring(0, end) + "…" else text
    }

    private fun splitIntoLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        val currentLine = StringBuilder()

        for (word in words) {
            val potential = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(potential) <= maxWidth) {
                currentLine.clear().append(potential)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine.clear()
                }
                currentLine.append(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }

    fun saveBitmapsToJpegs(context: Context, bitmaps: List<Bitmap>, baseName: String): List<File> {
        val dir = File(context.cacheDir, "cashmemos").apply { mkdirs() }
        val files = mutableListOf<File>()

        bitmaps.forEachIndexed { index, bitmap ->
            val suffix = if (bitmaps.size > 1) "_Page${index + 1}.jpg" else ".jpg"
            val file = File(dir, "$baseName$suffix")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            files.add(file)
        }
        return files
    }

    fun renderCashMemoToBitmap(
        memo: CashMemoEntity,
        profile: BusinessProfile,
        width: Int = 1200
    ): Bitmap {
        return renderCashMemoPages(memo, profile, width).firstOrNull()
            ?: Bitmap.createBitmap(width, 1000, Bitmap.Config.ARGB_8888)
    }

    fun saveBitmapToJpeg(context: Context, bitmap: Bitmap, fileName: String = "cashmemo.jpg"): File {
        val dir = File(context.cacheDir, "cashmemos").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        return file
    }

    /**
     * Exports a multi-page PDF document matching selected page size.
     */
    fun exportToPdf(
        context: Context,
        memo: CashMemoEntity,
        profile: BusinessProfile,
        fileName: String = "cashmemo.pdf",
        pageSize: String = "A4"
    ): File {
        val bitmaps = renderCashMemoPages(memo, profile, 1200)
        val pdfDocument = PdfDocument()

        val (pageWidth, pageHeight) = when (pageSize.uppercase()) {
            "A5" -> Pair(420, 595)
            "80MM" -> Pair(283, 425)
            "58MM" -> Pair(205, 307)
            else -> Pair(595, 842) // A4 default
        }

        bitmaps.forEachIndexed { index, bitmap ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val destRect = Rect(0, 0, pageWidth, pageHeight)
            canvas.drawBitmap(bitmap, null, destRect, null)
            pdfDocument.finishPage(page)
        }

        val dir = File(context.cacheDir, "cashmemos").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return file
    }

    /**
     * Triggers Android system Print manager with standard printer options.
     */
    fun printCashMemo(
        context: Context,
        memo: CashMemoEntity,
        profile: BusinessProfile,
        pageSize: String = "80mm"
    ) {
        val pdfFile = exportToPdf(
            context = context,
            memo = memo,
            profile = profile,
            fileName = "Print_Memo_${memo.memoNumber}_${System.currentTimeMillis()}.pdf",
            pageSize = pageSize
        )

        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return

        val printJobName = "CashMemo_${memo.memoNumber}_${profile.shopNameEnglish}"
        val printAdapter = PdfFilePrintDocumentAdapter(pdfFile)

        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(
                when (pageSize.uppercase()) {
                    "A5" -> PrintAttributes.MediaSize.ISO_A5
                    "80MM" -> PrintAttributes.MediaSize.ISO_A6
                    "58MM" -> PrintAttributes.MediaSize.ISO_C7
                    else -> PrintAttributes.MediaSize.ISO_A4
                }
            )
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()

        printManager.print(printJobName, printAdapter, printAttributes)
    }

    fun getShareIntent(context: Context, file: File, mimeType: String): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun getMultipleShareIntent(context: Context, files: List<File>, mimeType: String = "image/jpeg"): Intent {
        if (files.size == 1) {
            return getShareIntent(context, files.first(), mimeType)
        }
        val uris = ArrayList<Uri>()
        for (f in files) {
            uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f))
        }
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

/**
 * Standard Android PrintDocumentAdapter for streaming generated PDF files to system printers.
 */
class PdfFilePrintDocumentAdapter(private val pdfFile: File) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(pdfFile.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        try {
            FileInputStream(pdfFile).use { input ->
                FileOutputStream(destination?.fileDescriptor).use { output ->
                    input.copyTo(output)
                }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.message)
        }
    }
}
