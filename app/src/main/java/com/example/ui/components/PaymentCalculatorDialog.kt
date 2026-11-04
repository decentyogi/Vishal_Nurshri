package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import java.util.Locale
import kotlin.math.max

@Composable
fun PaymentCalculatorDialog(
    initialTotalBill: Double,
    initialPaidAmount: Double,
    hasExistingCustomPayment: Boolean,
    initialPaymentMethod: String = "Cash",
    initialPaymentDueNote: String = "Next Day (कल देगा)",
    allowEditingTotalBill: Boolean = true,
    onDismiss: () -> Unit,
    onApply: (totalBillOverride: Double?, paidAmount: Double, paymentMethod: String, paymentDueNote: String) -> Unit,
    onResetToFullPaid: (() -> Unit)? = null
) {
    var totalBillText by remember {
        mutableStateOf(
            if (initialTotalBill > 0.0) {
                if (initialTotalBill % 1.0 == 0.0) initialTotalBill.toInt().toString()
                else String.format(Locale.ENGLISH, "%.2f", initialTotalBill)
            } else {
                "1000"
            }
        )
    }

    var paidAmountText by remember {
        mutableStateOf(
            if (hasExistingCustomPayment) {
                if (initialPaidAmount % 1.0 == 0.0) initialPaidAmount.toInt().toString()
                else String.format(Locale.ENGLISH, "%.2f", initialPaidAmount)
            } else if (initialTotalBill > 0.0) {
                if (initialTotalBill % 1.0 == 0.0) initialTotalBill.toInt().toString()
                else String.format(Locale.ENGLISH, "%.2f", initialTotalBill)
            } else {
                "800"
            }
        )
    }

    var selectedPaymentMethod by remember {
        mutableStateOf(initialPaymentMethod.ifBlank { "Cash" })
    }

    var dueScheduleNote by remember {
        mutableStateOf(initialPaymentDueNote.ifBlank { "Next Day (कल देगा)" })
    }

    val currentTotalBill = totalBillText.replace(',', '.').trim().toDoubleOrNull() ?: 0.0
    val currentPaid = paidAmountText.replace(',', '.').trim().toDoubleOrNull() ?: 0.0
    val remainingBalance = max(0.0, currentTotalBill - currentPaid)
    val changeToReturn = max(0.0, currentPaid - currentTotalBill)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
                .testTag("payment_calculator_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Gradient Header Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(MaroonPrimaryDark, MaroonPrimary, MaroonPrimaryLight)
                            )
                        )
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.18f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Calculate,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Payment & Balance Calculator",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.5.sp
                                )
                                Text(
                                    text = "जमा राशि और कल का शेष बकाया हिसाब",
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Quick Presets Row
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Quick Presets (1-Tap Fill):",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Example ₹1000 Bill / ₹800 Paid (Next Day ₹200)
                            SuggestionChip(
                                onClick = {
                                    if (currentTotalBill <= 0.0 || allowEditingTotalBill) {
                                        if (initialTotalBill <= 0.0) totalBillText = "1000"
                                    }
                                    val baseTotal = if (initialTotalBill > 0.0) initialTotalBill else 1000.0
                                    val presetPaid = if (baseTotal == 1000.0) 800.0 else max(0.0, baseTotal - 200.0)
                                    paidAmountText = if (presetPaid % 1.0 == 0.0) presetPaid.toInt().toString() else presetPaid.toString()
                                    dueScheduleNote = "Next Day (कल देगा)"
                                },
                                icon = {
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = GoldAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (initialTotalBill > 0.0 && initialTotalBill != 1000.0)
                                            "-₹200 Due Next Day"
                                        else
                                            "₹1000 Bill / ₹800 Paid",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                modifier = Modifier.testTag("preset_1000_800_chip")
                            )

                            SuggestionChip(
                                onClick = {
                                    paidAmountText = if (currentTotalBill % 1.0 == 0.0) currentTotalBill.toInt().toString() else currentTotalBill.toString()
                                },
                                label = { Text("Full Paid (पूरा जमा)", fontSize = 12.sp) }
                            )

                            SuggestionChip(
                                onClick = {
                                    val half = currentTotalBill / 2.0
                                    paidAmountText = if (half % 1.0 == 0.0) half.toInt().toString() else String.format(Locale.ENGLISH, "%.2f", half)
                                    dueScheduleNote = "Next Day (कल देगा)"
                                },
                                label = { Text("50% Paid (आधा)", fontSize = 12.sp) }
                            )

                            SuggestionChip(
                                onClick = {
                                    paidAmountText = "0"
                                    dueScheduleNote = "Next Day (कल देगा)"
                                },
                                label = { Text("Full Credit (उधार)", fontSize = 12.sp) }
                            )
                        }
                    }

                    // Bill Total & Paid Amount Inputs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Total Bill Amount
                        OutlinedTextField(
                            value = totalBillText,
                            onValueChange = {
                                if (allowEditingTotalBill) {
                                    totalBillText = it
                                }
                            },
                            readOnly = !allowEditingTotalBill && initialTotalBill > 0.0,
                            label = { Text("कुल बिल (Total Bill ₹)") },
                            prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dialog_total_bill_input")
                        )

                        // Amount Paid Now
                        OutlinedTextField(
                            value = paidAmountText,
                            onValueChange = { paidAmountText = it },
                            label = { Text("आज जमा (Paid Now ₹)") },
                            prefix = { Text("₹ ", fontWeight = FontWeight.Bold, color = BotanicalGreen) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BotanicalGreen,
                                focusedLabelColor = BotanicalGreen
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dialog_paid_amount_input")
                        )
                    }

                    // Quick Paid Deduction Buttons (-₹50, -₹100, -₹200, -₹500)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Less from Total:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        listOf(100, 200, 500).forEach { deduct ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    val newPaid = max(0.0, currentTotalBill - deduct)
                                    paidAmountText = if (newPaid % 1.0 == 0.0) newPaid.toInt().toString() else newPaid.toString()
                                }
                            ) {
                                Text(
                                    text = "-₹$deduct",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaroonPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // LIVE CALCULATION SUMMARY CARD
                    val statusBgColor = when {
                        remainingBalance > 0.009 -> DueCrimsonContainer.copy(alpha = 0.65f)
                        changeToReturn > 0.009 -> WarningAmberContainer.copy(alpha = 0.7f)
                        else -> BotanicalGreenContainer.copy(alpha = 0.75f)
                    }
                    val statusBorderColor = when {
                        remainingBalance > 0.009 -> DueCrimson.copy(alpha = 0.5f)
                        changeToReturn > 0.009 -> WarningAmber.copy(alpha = 0.5f)
                        else -> BotanicalGreen.copy(alpha = 0.5f)
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = statusBgColor,
                        border = BorderStroke(1.5.dp, statusBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Bill Amount (कुल बिल):",
                                    fontSize = 13.sp,
                                    color = InkTextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = String.format(Locale.ENGLISH, "₹ %.2f", currentTotalBill),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = InkTextPrimary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Paid by Customer (जमा राशि):",
                                    fontSize = 13.sp,
                                    color = BotanicalGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = String.format(Locale.ENGLISH, "- ₹ %.2f", currentPaid),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BotanicalGreen
                                )
                            }

                            HorizontalDivider(color = statusBorderColor)

                            if (remainingBalance > 0.009) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Remaining Balance Due (शेष बकाया):",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = DueCrimson
                                        )
                                        Text(
                                            text = "Promise: $dueScheduleNote",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DueCrimson.copy(alpha = 0.85f)
                                        )
                                    }
                                    Text(
                                        text = String.format(Locale.ENGLISH, "₹ %.2f", remainingBalance),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = DueCrimson,
                                        modifier = Modifier.testTag("dialog_remaining_balance_text")
                                    )
                                }
                            } else if (changeToReturn > 0.009) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Change to Return (वापस करने की राशि):",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = WarningAmber
                                        )
                                        Text(
                                            text = "Customer paid extra cash",
                                            fontSize = 11.5.sp,
                                            color = InkTextSecondary
                                        )
                                    }
                                    Text(
                                        text = String.format(Locale.ENGLISH, "₹ %.2f", changeToReturn),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = WarningAmber
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = BotanicalGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Fully Paid (पूरा भुगतान चुकता)",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = BotanicalGreen
                                        )
                                    }
                                    Text(
                                        text = "₹ 0.00 Due",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        color = BotanicalGreen
                                    )
                                }
                            }
                        }
                    }

                    // When will customer pay the remaining balance? (Shown prominently when balance > 0)
                    if (remainingBalance > 0.009) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "बाकी पैसा कब देगा? (When will rest be paid?):",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            val scheduleOptions = listOf(
                                "Next Day (कल देगा)",
                                "In 2 Days (परसों देगा)",
                                "Next Week (अगले हफ्ते)",
                                "Next Month (अगले महीने)"
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                scheduleOptions.forEach { option ->
                                    val isSelected = dueScheduleNote == option
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { dueScheduleNote = option },
                                        label = {
                                            Text(
                                                text = option,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    Icons.Default.EventAvailable,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = dueScheduleNote,
                                onValueChange = { dueScheduleNote = it },
                                label = { Text("बकाया नोट / तारीख (Payment Promise Note)") },
                                placeholder = { Text("e.g. Next Day (कल सुबह ₹200 देगा)") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                leadingIcon = {
                                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dialog_due_note_input")
                            )
                        }
                    }

                    // Payment Mode Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "भुगतान का तरीका (Payment Mode):",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Pair("Cash", "Cash (नकद)"),
                                Pair("UPI", "UPI / Online"),
                                Pair("Cash + UPI", "Mixed")
                            ).forEach { (code, label) ->
                                val selected = selectedPaymentMethod == code
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedPaymentMethod = code },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Action Buttons Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasExistingCustomPayment && onResetToFullPaid != null) {
                            OutlinedButton(
                                onClick = {
                                    onResetToFullPaid()
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Reset", fontSize = 13.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Cancel", fontSize = 13.sp)
                            }
                        }

                        Button(
                            onClick = {
                                val overrideBill = if (currentTotalBill > 0.0 && currentTotalBill != initialTotalBill) {
                                    currentTotalBill
                                } else null
                                onApply(
                                    overrideBill,
                                    currentPaid,
                                    selectedPaymentMethod,
                                    if (remainingBalance > 0.009) dueScheduleNote else "Fully Paid (चुकता)"
                                )
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("apply_payment_calc_button")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Save to Summary & Total",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
