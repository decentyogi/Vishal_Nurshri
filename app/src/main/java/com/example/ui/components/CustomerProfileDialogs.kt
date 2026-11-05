package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CustomerBalanceHistoryEntity
import com.example.data.model.CustomerProfileEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CustomerPickerDialog(
    customers: List<CustomerProfileEntity>,
    onDismiss: () -> Unit,
    onSelectCustomer: (CustomerProfileEntity) -> Unit,
    onViewHistory: (CustomerProfileEntity) -> Unit,
    onAddNewCustomer: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }

    val filtered = remember(customers, searchText) {
        if (searchText.isBlank()) customers
        else {
            val q = searchText.trim().lowercase()
            customers.filter {
                it.name.lowercase().contains(q) ||
                    it.phoneNumber.lowercase().contains(q) ||
                    it.address.lowercase().contains(q)
            }
        }
    }

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
                .heightIn(max = 600.dp)
                .testTag("customer_picker_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaroonPrimary)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Select Customer Profile (ग्राहक चुनें)",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Auto-fills Name, Phone, Address & Previous Dues",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.5.sp
                            )
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            placeholder = { Text("Search name or mobile...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("customer_picker_search_input")
                        )

                        FilledTonalButton(
                            onClick = onAddNewCustomer,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.PeopleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No matching customer profiles",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered, key = { it.id }) { customer ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (customer.hasOutstandingBalance)
                                        DueCrimsonContainer.copy(alpha = 0.35f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (customer.hasOutstandingBalance) DueCrimson.copy(alpha = 0.4f)
                                        else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectCustomer(customer)
                                            onDismiss()
                                        }
                                        .testTag("pick_customer_${customer.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (customer.hasOutstandingBalance) DueCrimson else BotanicalGreen,
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = customer.name.take(1).uppercase(),
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 15.sp
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = customer.name,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 14.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = buildString {
                                                        if (customer.phoneNumber.isNotBlank()) append("Mob: ${customer.phoneNumber}")
                                                        if (customer.address.isNotBlank()) {
                                                            if (isNotEmpty()) append(" • ")
                                                            append(customer.address)
                                                        }
                                                    },
                                                    fontSize = 11.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (customer.hasOutstandingBalance && customer.lastPromiseNote.isNotBlank()) {
                                                    Text(
                                                        text = "Note: ${customer.lastPromiseNote}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = DueCrimson
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(horizontalAlignment = Alignment.End) {
                                            if (customer.hasOutstandingBalance) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = DueCrimson
                                                ) {
                                                    Text(
                                                        text = "Due: ₹${String.format(Locale.ENGLISH, "%.0f", customer.outstandingBalance)}",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 11.5.sp,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            } else {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = BotanicalGreenContainer
                                                ) {
                                                    Text(
                                                        text = "No Due",
                                                        color = BotanicalGreen,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            TextButton(
                                                onClick = { onViewHistory(customer) },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                                modifier = Modifier.height(26.dp)
                                            ) {
                                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("History", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerBalanceHistoryDialog(
    customer: CustomerProfileEntity,
    historyList: List<CustomerBalanceHistoryEntity>,
    onDismiss: () -> Unit,
    onCreateBillForCustomer: (CustomerProfileEntity) -> Unit,
    onSettleBalance: (CustomerProfileEntity) -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH) }

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
                .heightIn(max = 640.dp)
                .testTag("customer_balance_history_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaroonPrimary)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = customer.name,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = buildString {
                                    if (customer.phoneNumber.isNotBlank()) append("Mob: ${customer.phoneNumber}")
                                    if (customer.address.isNotBlank()) {
                                        if (isNotEmpty()) append(" • ")
                                        append(customer.address)
                                    }
                                },
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Summary Strip (Total Purchased | Total Paid | Outstanding Balance)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("Total Bought", fontSize = 11.sp, color = InkTextSecondary)
                            Text(
                                text = String.format(Locale.ENGLISH, "₹ %.0f", customer.totalPurchased),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.5.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("Total Paid", fontSize = 11.sp, color = BotanicalGreen)
                            Text(
                                text = String.format(Locale.ENGLISH, "₹ %.0f", customer.totalPaid),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.5.sp,
                                color = BotanicalGreen
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("Current Due", fontSize = 11.sp, color = if (customer.hasOutstandingBalance) DueCrimson else InkTextSecondary)
                            Text(
                                text = String.format(Locale.ENGLISH, "₹ %.0f", customer.outstandingBalance),
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = if (customer.hasOutstandingBalance) DueCrimson else BotanicalGreen
                            )
                        }
                    }

                    Text(
                        text = "बकाया और लेन-देन इतिहास (Outstanding Balance History):",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        color = MaroonPrimary
                    )

                    if (historyList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No balance transactions recorded yet.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(historyList, key = { it.id }) { entry ->
                                val isDueAdded = entry.balanceChange > 0.009
                                val isPayment = entry.balanceChange < -0.009 || entry.transactionType == "BALANCE_SETTLED"

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = when {
                                                        isDueAdded -> Icons.AutoMirrored.Filled.CallMade
                                                        isPayment -> Icons.AutoMirrored.Filled.CallReceived
                                                        else -> Icons.Default.Receipt
                                                    },
                                                    contentDescription = null,
                                                    tint = when {
                                                        isDueAdded -> DueCrimson
                                                        isPayment -> BotanicalGreen
                                                        else -> MaroonPrimary
                                                    },
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = when (entry.transactionType) {
                                                        "BILL_CREATED" -> "Bill #${entry.memoNumber.ifBlank { "-" }}"
                                                        "BALANCE_SETTLED" -> "Balance Settled (चुकता)"
                                                        "PAYMENT_RECEIVED" -> "Payment Received (जमा)"
                                                        else -> "Balance Adjustment"
                                                    },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }

                                            Text(
                                                text = "Bal: ₹${String.format(Locale.ENGLISH, "%.0f", entry.resultingBalance)}",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 13.sp,
                                                color = if (entry.resultingBalance > 0.009) DueCrimson else BotanicalGreen
                                            )
                                        }

                                        if (entry.note.isNotBlank()) {
                                            Text(
                                                text = entry.note,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = dateFormatter.format(Date(entry.timestamp)),
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Text(
                                                text = entry.paymentMethod,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaroonPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (customer.hasOutstandingBalance) {
                            OutlinedButton(
                                onClick = { onSettleBalance(customer) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Settle Due", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                        }

                        Button(
                            onClick = { onCreateBillForCustomer(customer) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(46.dp)
                                .testTag("use_customer_in_bill_button")
                        ) {
                            Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Use in New Bill", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddOrEditCustomerDialog(
    initialCustomer: CustomerProfileEntity? = null,
    onDismiss: () -> Unit,
    onSave: (id: Long, name: String, phone: String, address: String, outstandingBalance: Double, promiseNote: String) -> Unit
) {
    var name by remember { mutableStateOf(initialCustomer?.name ?: "") }
    var phone by remember { mutableStateOf(initialCustomer?.phoneNumber ?: "") }
    var address by remember { mutableStateOf(initialCustomer?.address ?: "") }
    var balanceText by remember {
        mutableStateOf(
            if ((initialCustomer?.outstandingBalance ?: 0.0) > 0.0)
                String.format(Locale.ENGLISH, "%.0f", initialCustomer!!.outstandingBalance)
            else ""
        )
    }
    var promiseNote by remember {
        mutableStateOf(initialCustomer?.lastPromiseNote?.ifBlank { "Next Day (कल देगा)" } ?: "Next Day (कल देगा)")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialCustomer == null) "Add Customer Profile (नया ग्राहक)" else "Edit Customer Profile",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ग्राहक का नाम (Customer Name)*") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_customer_name_input")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("मोबाइल नंबर (Phone Number)*") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_customer_phone_input")
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("पता / गाँव / शहर (Address)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("बकाया राशि (Outstanding Balance ₹)") },
                    placeholder = { Text("0") },
                    prefix = { Text("₹ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_customer_balance_input")
                )

                val balVal = balanceText.replace(',', '.').trim().toDoubleOrNull() ?: 0.0
                if (balVal > 0.0) {
                    OutlinedTextField(
                        value = promiseNote,
                        onValueChange = { promiseNote = it },
                        label = { Text("भुगतान वादा (Promise Note)") },
                        placeholder = { Text("Next Day (कल देगा)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() || phone.isNotBlank()) {
                        val bal = balanceText.replace(',', '.').trim().toDoubleOrNull() ?: 0.0
                        onSave(
                            initialCustomer?.id ?: 0L,
                            name.ifBlank { "Customer ($phone)" },
                            phone,
                            address,
                            bal,
                            promiseNote
                        )
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                modifier = Modifier.testTag("save_customer_profile_button")
            ) {
                Text("Save Customer", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
