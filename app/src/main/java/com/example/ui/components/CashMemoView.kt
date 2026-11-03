package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BusinessProfile
import com.example.data.model.CashMemoItem
import com.example.ui.theme.BillMaroonInk
import com.example.util.NumberToWordsConverter
import java.util.Locale
import kotlin.math.max

@Composable
fun CashMemoPaperSlip(
    businessProfile: BusinessProfile,
    memoNumber: String,
    date: String,
    customerName: String,
    customerMobile: String,
    customerAddress: String,
    items: List<CashMemoItem>,
    subtotal: Double,
    discount: Double = 0.0,
    taxPercentage: Double = 0.0,
    taxAmount: Double = 0.0,
    previousDues: Double = 0.0,
    grandTotal: Double,
    pageIndex: Int = 0,
    totalPages: Int = 1,
    modifier: Modifier = Modifier
) {
    val maroon = BillMaroonInk
    val paperColor = Color(0xFFFFFDF9)
    val displayRowCount = 15 // Always 15 rows by default to maintain identical page height and width
    val isLastPage = pageIndex >= totalPages - 1
    val finalTotalPayable = grandTotal + previousDues

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = paperColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(4.dp)
    ) {
        // Outer Maroon Border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .border(BorderStroke(2.5.dp, maroon), RoundedCornerShape(2.dp))
                .padding(3.dp)
                .border(BorderStroke(1.dp, maroon), RoundedCornerShape(1.dp))
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Top: Mobile Number & Floral Emblems
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFlorist,
                        contentDescription = "Floral icon",
                        tint = maroon,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Mob. ${businessProfile.phone}",
                        color = maroon,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.Spa,
                        contentDescription = "Floral icon",
                        tint = maroon,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Shop Name in prominent Hindi typography with flanking floral emblems (matching img 1.jpg & img 2.jpg)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFlorist,
                        contentDescription = "Floral emblem",
                        tint = maroon,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = businessProfile.shopNameHindi,
                        color = maroon,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.LocalFlorist,
                        contentDescription = "Floral emblem",
                        tint = maroon,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (businessProfile.shopNameEnglish.isNotBlank() &&
                    businessProfile.shopNameEnglish != businessProfile.shopNameHindi
                ) {
                    Text(
                        text = "(${businessProfile.shopNameEnglish})",
                        color = maroon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                // Address
                if (businessProfile.addressLine1.isNotBlank()) {
                    Text(
                        text = businessProfile.addressLine1,
                        color = maroon,
                        fontSize = 10.5.sp,
                        textAlign = TextAlign.Center
                    )
                }
                if (businessProfile.addressLine2.isNotBlank()) {
                    Text(
                        text = businessProfile.addressLine2,
                        color = maroon,
                        fontSize = 10.5.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Tagline / Specialization note
                if (businessProfile.tagline.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = businessProfile.tagline,
                        color = maroon,
                        fontSize = 9.5.sp,
                        fontStyle = FontStyle.Italic,
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Center,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(thickness = 1.5.dp, color = maroon)
                Spacer(modifier = Modifier.height(6.dp))

                // Meta Row: Memo No & Date & Page Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "नं० ",
                            color = maroon,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .border(1.5.dp, maroon, RoundedCornerShape(2.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (totalPages > 1) "$memoNumber (P${pageIndex + 1}/$totalPages)" else memoNumber,
                                color = maroon,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (totalPages > 1) {
                        Surface(
                            color = maroon.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Page ${pageIndex + 1} of $totalPages",
                                color = maroon,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "दिनांक: ",
                            color = maroon,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = date,
                            color = maroon,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Customer Name & Mobile Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "नाम: ",
                        color = maroon,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = customerName.ifBlank { "..................................................." },
                        color = maroon,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (customerMobile.isNotBlank()) {
                        Text(
                            text = "मो०: ",
                            color = maroon,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = customerMobile,
                            color = maroon,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Address Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "पता: ",
                        color = maroon,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = customerAddress.ifBlank { "......................................................................................." },
                        color = maroon,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // TABULAR SECTION
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, maroon)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(maroon)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "क्रम.",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(30.dp)
                        )
                        Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color.White))
                        Text(
                            text = "पौधा / सामग्री का विवरण",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        )
                        Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color.White))
                        Text(
                            text = "संख्या",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(38.dp)
                        )
                        Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color.White))
                        Text(
                            text = "दाम",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(46.dp)
                        )
                        Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color.White))
                        Text(
                            text = "रू०",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(64.dp)
                        )
                        Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color.White))
                        Text(
                            text = "पै०",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(32.dp)
                        )
                    }

                    // Item rows
                    for (i in 0 until displayRowCount) {
                        val item = items.getOrNull(i)
                        HorizontalDivider(thickness = 0.8.dp, color = maroon.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 28.dp)
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // S.No
                            val lineSn = if (item != null) "${item.serialNo}" else "${pageIndex * 15 + i + 1}"
                            Text(
                                text = lineSn,
                                color = if (item != null) maroon else maroon.copy(alpha = 0.4f),
                                fontSize = 10.sp,
                                fontWeight = if (item != null) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(30.dp)
                            )
                            Box(modifier = Modifier.width(0.8.dp).height(24.dp).background(maroon.copy(alpha = 0.4f)))

                            // Description
                            Text(
                                text = item?.description ?: "",
                                color = maroon,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            )
                            Box(modifier = Modifier.width(0.8.dp).height(24.dp).background(maroon.copy(alpha = 0.4f)))

                            // Qty
                            val qtyText = if (item != null) {
                                if (item.quantity % 1.0 == 0.0) "${item.quantity.toInt()}" else "${item.quantity}"
                            } else ""
                            Text(
                                text = qtyText,
                                color = maroon,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(38.dp)
                            )
                            Box(modifier = Modifier.width(0.8.dp).height(24.dp).background(maroon.copy(alpha = 0.4f)))

                            // Rate
                            val rateText = if (item != null && item.rate > 0) String.format(Locale.ENGLISH, "%.2f", item.rate) else ""
                            Text(
                                text = rateText,
                                color = maroon,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(46.dp)
                            )
                            Box(modifier = Modifier.width(0.8.dp).height(24.dp).background(maroon.copy(alpha = 0.4f)))

                            // Rupees & Paise
                            val (rs, ps) = if (item != null) NumberToWordsConverter.splitRupeesAndPaise(item.amount) else Pair("--", "--")
                            Text(
                                text = rs,
                                color = if (item != null) maroon else maroon.copy(alpha = 0.35f),
                                fontSize = 10.5.sp,
                                fontWeight = if (item != null) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.width(64.dp)
                            )
                            Box(modifier = Modifier.width(0.8.dp).height(24.dp).background(maroon.copy(alpha = 0.4f)))
                            Text(
                                text = ps,
                                color = if (item != null) maroon else maroon.copy(alpha = 0.35f),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.width(32.dp)
                            )
                        }
                    }

                    // TOTAL ROW
                    HorizontalDivider(thickness = 1.5.dp, color = maroon)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 34.dp)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val totalLabel = if (!isLastPage) {
                            "आगे ले जाया गया (Carried Over P${pageIndex + 2}):"
                        } else if (previousDues > 0) {
                            "कुल देय (Total Payable inc. Dues):"
                        } else {
                            "टोटल (Grand Total):"
                        }

                        Text(
                            text = totalLabel,
                            color = maroon,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        )
                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(maroon))

                        val displayTotal = if (isLastPage) finalTotalPayable else items.sumOf { it.amount }
                        val (totRs, totPs) = NumberToWordsConverter.splitRupeesAndPaise(displayTotal)
                        Text(
                            text = totRs,
                            color = maroon,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.width(64.dp)
                        )
                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(maroon))
                        Text(
                            text = totPs,
                            color = maroon,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.width(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Words Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "शब्दों में: ",
                        color = maroon,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val wordsAmount = if (isLastPage) finalTotalPayable else items.sumOf { it.amount }
                    Text(
                        text = NumberToWordsConverter.convertToIndianRupees(wordsAmount),
                        color = maroon,
                        fontSize = 10.sp,
                        fontStyle = FontStyle.Italic,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Discount / Tax / Dues breakdown line if last page
                if (isLastPage && (discount > 0 || taxPercentage > 0 || previousDues > 0)) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = buildString {
                                append("Bill Subtotal: ₹${String.format(Locale.ENGLISH, "%.2f", subtotal)}")
                                if (discount > 0) append(" | छूट: -₹${String.format(Locale.ENGLISH, "%.2f", discount)}")
                                if (taxPercentage > 0) append(" | GST (${taxPercentage}%): +₹${String.format(Locale.ENGLISH, "%.2f", taxAmount)}")
                                if (previousDues > 0) append(" | पिछला बकाया (Dues): +₹${String.format(Locale.ENGLISH, "%.2f", previousDues)}")
                            },
                            color = maroon.copy(alpha = 0.9f),
                            fontSize = 9.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer: Terms & Digital Signature Block (Matching 1.JPG)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "भूल-चूक लेनी-देनी (E. & O.E.)",
                        color = maroon,
                        fontSize = 9.sp
                    )

                    // Authenticated Digital Signature Box (Matching user's reference image 1.JPG)
                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, maroon.copy(alpha = 0.6f)),
                            color = Color(0xFFFBF8F4)
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(min = 150.dp)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Digitally Signed",
                                        tint = maroon,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Digitally Signed",
                                        color = maroon,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Text(
                                    text = "BY ${businessProfile.signerName.ifBlank { "VISHAL NURSERY" }.uppercase()}",
                                    color = maroon,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Date - $date",
                                    color = maroon,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "हस्ताक्षर (Authorized Signatory)",
                            color = maroon,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}
