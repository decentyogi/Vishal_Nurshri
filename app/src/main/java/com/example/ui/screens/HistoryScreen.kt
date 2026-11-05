package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CashMemoEntity
import com.example.data.model.CashMemoItem
import com.example.data.model.CustomerProfileEntity
import com.example.data.repository.CashMemoRepository
import com.example.ui.CashMemoViewModel
import com.example.ui.components.AddOrEditCustomerDialog
import com.example.ui.components.CustomerBalanceHistoryDialog
import com.example.ui.components.PaymentCalculatorDialog
import com.example.ui.theme.*
import com.example.util.CashMemoExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class HistoryViewMode {
    BILLS,
    CUSTOMERS
}

private enum class HistoryFilter {
    ALL,
    PENDING_DUE,
    PAID
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: CashMemoViewModel,
    onOpenMemo: (CashMemoEntity) -> Unit,
    onNewMemo: () -> Unit,
    onSelectCustomerForNewBill: (CustomerProfileEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val memos by viewModel.historyMemos.collectAsState()
    val customers by viewModel.customerProfiles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val businessProfile by viewModel.businessProfile.collectAsState()

    var viewMode by remember { mutableStateOf(HistoryViewMode.BILLS) }
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }

    var memoToDelete by remember { mutableStateOf<CashMemoEntity?>(null) }
    var memoForPaymentCalc by remember { mutableStateOf<CashMemoEntity?>(null) }

    var customerForHistory by remember { mutableStateOf<CustomerProfileEntity?>(null) }
    var customerToEdit by remember { mutableStateOf<CustomerProfileEntity?>(null) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var customerToDelete by remember { mutableStateOf<CustomerProfileEntity?>(null) }

    val totalSales = remember(memos) { memos.sumOf { it.totalPayableWithDues } }
    val totalCollected = remember(memos) { memos.sumOf { it.effectivePaidAmount } }
    val totalPendingDue = remember(memos) { memos.sumOf { it.effectiveBalanceDue } }
    val pendingDueCount = remember(memos) { memos.count { it.hasPendingBalance } }
    val customersWithDuesCount = remember(customers) { customers.count { it.hasPendingBalance } }

    val filteredMemos = remember(memos, selectedFilter) {
        when (selectedFilter) {
            HistoryFilter.ALL -> memos
            HistoryFilter.PENDING_DUE -> memos.filter { it.hasPendingBalance }
            HistoryFilter.PAID -> memos.filter { !it.hasPendingBalance }
        }
    }

    val filteredCustomers = remember(customers, selectedFilter) {
        when (selectedFilter) {
            HistoryFilter.ALL -> customers
            HistoryFilter.PENDING_DUE -> customers.filter { it.hasPendingBalance }
            HistoryFilter.PAID -> customers.filter { !it.hasPendingBalance }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (viewMode == HistoryViewMode.BILLS)
                                "Saved Bills & Dues Ledger"
                            else
                                "Customer Profiles & Balance History",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = MaroonPrimary
                        )
                        Text(
                            text = "${memos.size} Bills • ${customers.size} Customers ($customersWithDuesCount with Dues)",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (viewMode == HistoryViewMode.CUSTOMERS) {
                        FilledTonalButton(
                            onClick = { showAddCustomerDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(34.dp)
                                .testTag("history_add_customer_btn")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Customer", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        // Requirement 3: Standard bottom-right anchoring for "+ New Bill" FAB
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (viewMode == HistoryViewMode.CUSTOMERS) {
                        showAddCustomerDialog = true
                    } else {
                        onNewMemo()
                    }
                },
                containerColor = MaroonPrimary,
                contentColor = Color.White,
                icon = {
                    Icon(
                        if (viewMode == HistoryViewMode.CUSTOMERS) Icons.Default.PersonAdd else Icons.Default.Add,
                        contentDescription = "Action"
                    )
                },
                text = {
                    Text(
                        if (viewMode == HistoryViewMode.CUSTOMERS) "+ New Customer" else "+ New Bill",
                        fontWeight = FontWeight.Bold
                    )
                },
                modifier = Modifier.testTag("fab_new_memo")
            )
        },
        modifier = modifier
    ) { innerPadding ->
        // Requirement 3: Horizontally centered parent container with consistent 16dp left/right margins
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Segmented View Switcher: Saved Bills vs Customer Profiles & Balance History
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isBills = viewMode == HistoryViewMode.BILLS
                    Surface(
                        onClick = { viewMode = HistoryViewMode.BILLS },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isBills) MaroonPrimary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_saved_bills")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = if (isBills) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Bills (${memos.size})",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isBills) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val isCustomers = viewMode == HistoryViewMode.CUSTOMERS
                    Surface(
                        onClick = { viewMode = HistoryViewMode.CUSTOMERS },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCustomers) MaroonPrimary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_customer_profiles")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.People,
                                contentDescription = null,
                                tint = if (isCustomers) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Customers (${customers.size})",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isCustomers) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Requirement 1 & 3: Financial Ledger Summary Card (solid color without gradient shader overdraw,
            // and center-aligned aggregate revenue metrics: "Total Sales", "Received", "Pending Due")
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaroonPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Total Sales",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.88f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format(Locale.ENGLISH, "₹ %.0f", totalSales),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(Color.White.copy(alpha = 0.25f))
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Received",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFB9F6CA),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format(Locale.ENGLISH, "₹ %.0f", totalCollected),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFB9F6CA),
                            textAlign = TextAlign.Center
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(Color.White.copy(alpha = 0.25f))
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Pending Due",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFFD54F),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format(Locale.ENGLISH, "₹ %.0f", totalPendingDue),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFFD54F),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = {
                    Text(
                        if (viewMode == HistoryViewMode.BILLS)
                            "Search by customer name or memo #..."
                        else
                            "Search customer name or phone number..."
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaroonPrimary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("history_search_input"),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            // Filter Chips Row (All | Pending Dues | Paid)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalCount = if (viewMode == HistoryViewMode.BILLS) memos.size else customers.size
                val dueCount = if (viewMode == HistoryViewMode.BILLS) pendingDueCount else customersWithDuesCount

                FilterChip(
                    selected = selectedFilter == HistoryFilter.ALL,
                    onClick = { selectedFilter = HistoryFilter.ALL },
                    label = { Text("All ($totalCount)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )

                FilterChip(
                    selected = selectedFilter == HistoryFilter.PENDING_DUE,
                    onClick = { selectedFilter = HistoryFilter.PENDING_DUE },
                    label = {
                        Text(
                            text = "Pending Due ($dueCount)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (selectedFilter == HistoryFilter.PENDING_DUE) DueCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )

                FilterChip(
                    selected = selectedFilter == HistoryFilter.PAID,
                    onClick = { selectedFilter = HistoryFilter.PAID },
                    label = {
                        Text(
                            text = "Settled (${totalCount - dueCount})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }

            if (viewMode == HistoryViewMode.BILLS) {
                if (filteredMemos.isEmpty()) {
                    EmptyHistoryBox(
                        title = when {
                            searchQuery.isNotBlank() -> "No matching bills found"
                            selectedFilter == HistoryFilter.PENDING_DUE -> "No pending dues"
                            else -> "No bills generated yet"
                        },
                        subtitle = "Create a new cash memo or scan a rough bill to get started.",
                        buttonText = "+ New Bill",
                        onAction = onNewMemo
                    )
                } else {
                    // Requirement 1 & 3: Stable item keys, horizontally centered cards, and 88dp bottom padding
                    // so all cards scroll completely clear of the FAB and bottom navigation bar
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 88.dp)
                    ) {
                        items(
                            items = filteredMemos,
                            key = { it.id },
                            contentType = { "memo_history_item" }
                        ) { memo ->
                            HistoryMemoCard(
                                memo = memo,
                                businessProfile = businessProfile,
                                onOpen = { onOpenMemo(memo) },
                                onSettlePayment = { memoForPaymentCalc = memo },
                                onDelete = { memoToDelete = memo }
                            )
                        }
                    }
                }
            } else {
                // CUSTOMERS & BALANCE HISTORY TAB
                if (filteredCustomers.isEmpty()) {
                    EmptyHistoryBox(
                        title = when {
                            searchQuery.isNotBlank() -> "No matching customer profile found"
                            selectedFilter == HistoryFilter.PENDING_DUE -> "No pending dues"
                            else -> "No customer profiles saved yet"
                        },
                        subtitle = "Customers and their balance history are automatically saved when you create bills.",
                        buttonText = "Add Customer Profile",
                        onAction = { showAddCustomerDialog = true }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 88.dp)
                    ) {
                        items(
                            items = filteredCustomers,
                            key = { it.id },
                            contentType = { "customer_profile_item" }
                        ) { customer ->
                            CustomerProfileLedgerCard(
                                customer = customer,
                                onViewHistory = { customerForHistory = customer },
                                onUseInNewBill = { onSelectCustomerForNewBill(customer) },
                                onEdit = { customerToEdit = customer },
                                onDelete = { customerToDelete = customer }
                            )
                        }
                    }
                }
            }
        }
    }

    var customerForSettlement by remember { mutableStateOf<CustomerProfileEntity?>(null) }

    // Payment & Balance Calculator Dialog for any saved memo
    if (memoForPaymentCalc != null) {
        val targetMemo = memoForPaymentCalc!!
        PaymentCalculatorDialog(
            initialTotalBill = targetMemo.totalPayableWithDues,
            initialPaidAmount = targetMemo.effectivePaidAmount,
            hasExistingCustomPayment = targetMemo.hasPendingBalance,
            initialPaymentMethod = targetMemo.paymentMethod,
            initialPaymentDueNote = targetMemo.paymentStatusNote.ifBlank { "Next Day (कल देगा)" },
            allowEditingTotalBill = false,
            onDismiss = { memoForPaymentCalc = null },
            onApply = { _, paid, method, note ->
                viewModel.updateSavedMemoPayment(
                    memo = targetMemo,
                    newPaidAmount = paid,
                    newPaymentMethod = method,
                    newPaymentNote = note
                )
                Toast.makeText(
                    context,
                    "Updated payment for Memo #${targetMemo.memoNumber}",
                    Toast.LENGTH_SHORT
                ).show()
                memoForPaymentCalc = null
            }
        )
    }

    // Direct Customer Balance Settlement Calculator Dialog
    if (customerForSettlement != null) {
        val targetCustomer = customerForSettlement!!
        PaymentCalculatorDialog(
            initialTotalBill = targetCustomer.outstandingBalance,
            initialPaidAmount = targetCustomer.outstandingBalance,
            hasExistingCustomPayment = true,
            initialPaymentMethod = "Cash",
            initialPaymentDueNote = targetCustomer.lastPromiseNote.ifBlank { "Next Day (कल देगा)" },
            allowEditingTotalBill = false,
            onDismiss = { customerForSettlement = null },
            onApply = { _, paidNow, method, note ->
                viewModel.recordDirectCustomerPayment(
                    customer = targetCustomer,
                    amountReceivedNow = paidNow,
                    paymentMethod = method,
                    remainingPromiseNote = note
                )
                Toast.makeText(
                    context,
                    "Recorded ₹${String.format(Locale.ENGLISH, "%.0f", paidNow)} for ${targetCustomer.name}",
                    Toast.LENGTH_SHORT
                ).show()
                customerForSettlement = null
            }
        )
    }

    // Customer Balance History & Direct Settlement Dialog
    if (customerForHistory != null) {
        val activeCustomer = customers.find { it.id == customerForHistory!!.id } ?: customerForHistory!!
        val historyEntries by viewModel.getBalanceHistoryForCustomer(activeCustomer.id).collectAsState(initial = emptyList())
        CustomerBalanceHistoryDialog(
            customer = activeCustomer,
            historyList = historyEntries,
            onDismiss = { customerForHistory = null },
            onCreateBillForCustomer = { selected ->
                customerForHistory = null
                onSelectCustomerForNewBill(selected)
            },
            onSettleBalance = { selected ->
                customerForSettlement = selected
            }
        )
    }

    // Add or Edit Customer Profile Dialog
    if (showAddCustomerDialog || customerToEdit != null) {
        AddOrEditCustomerDialog(
            initialCustomer = customerToEdit,
            onDismiss = {
                showAddCustomerDialog = false
                customerToEdit = null
            },
            onSave = { id, name, phone, address, openingBalance, note ->
                viewModel.saveOrUpdateCustomer(
                    id = id,
                    name = name,
                    phone = phone,
                    address = address,
                    outstandingBalance = openingBalance,
                    promiseNote = note
                )
                Toast.makeText(
                    context,
                    if (customerToEdit != null) "Updated customer $name" else "Saved customer $name",
                    Toast.LENGTH_SHORT
                ).show()
                showAddCustomerDialog = false
                customerToEdit = null
            }
        )
    }

    if (customerToDelete != null) {
        AlertDialog(
            onDismissRequest = { customerToDelete = null },
            title = { Text("Delete Customer Profile?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Remove ${customerToDelete?.name} and their balance history from customer profiles? Saved cash memos will remain intact.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        customerToDelete?.let { viewModel.deleteCustomer(it.id) }
                        customerToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { customerToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (memoToDelete != null) {
        AlertDialog(
            onDismissRequest = { memoToDelete = null },
            title = { Text("Delete Cash Memo?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete Cash Memo #${memoToDelete?.memoNumber} for ${memoToDelete?.customerName.orEmpty().ifBlank { "Customer" }}?") },
            confirmButton = {
                Button(
                    onClick = {
                        memoToDelete?.let { viewModel.deleteMemo(it.id) }
                        memoToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { memoToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyHistoryBox(
    title: String,
    subtitle: String,
    buttonText: String,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaroonContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = MaroonPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(buttonText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CustomerProfileLedgerCard(
    customer: CustomerProfileEntity,
    onViewHistory: () -> Unit,
    onUseInNewBill: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(
            1.dp,
            if (customer.hasPendingBalance) DueCrimson.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewHistory)
            .testTag("customer_card_${customer.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (customer.hasPendingBalance) DueCrimsonContainer else MaroonContainer,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = customer.name.take(1).uppercase(),
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = if (customer.hasPendingBalance) DueCrimson else MaroonPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = customer.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = customer.phoneNumber.ifBlank { "No phone number" },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (customer.address.isNotBlank()) {
                                Text(
                                    text = " • ${customer.address}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (customer.hasPendingBalance) DueCrimsonContainer else BotanicalGreenContainer
                    ) {
                        Text(
                            text = if (customer.hasPendingBalance)
                                "DUE: ₹${String.format(Locale.ENGLISH, "%.0f", customer.outstandingBalance)}"
                            else
                                "✓ SETTLED",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            color = if (customer.hasPendingBalance) DueCrimson else BotanicalGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Summary metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Purchased: ₹${String.format(Locale.ENGLISH, "%.0f", customer.totalPurchased)}",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Total Paid: ₹${String.format(Locale.ENGLISH, "%.0f", customer.totalPaid)}",
                    fontSize = 11.5.sp,
                    color = BotanicalGreen,
                    fontWeight = FontWeight.Bold
                )
            }

            if (customer.hasPendingBalance && customer.lastPromiseNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DueCrimsonContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Promise Note: ${customer.lastPromiseNote}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = DueCrimson,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(thickness = 0.6.dp)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = onViewHistory,
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Balance History",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onUseInNewBill,
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New Bill",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Customer", modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete Customer",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMemoCard(
    memo: CashMemoEntity,
    businessProfile: com.example.data.model.BusinessProfile,
    onOpen: () -> Unit,
    onSettlePayment: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Requirement 1: Offload JSON deserialization of bill items off the Main UI thread
    val items by produceState<List<CashMemoItem>>(initialValue = emptyList(), key1 = memo.itemsJson) {
        value = withContext(Dispatchers.Default) {
            CashMemoRepository.deserializeItems(memo.itemsJson)
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(
            1.dp,
            if (memo.hasPendingBalance) DueCrimson.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("memo_card_${memo.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaroonPrimary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "#${memo.memoNumber}",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = memo.date,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format(Locale.ENGLISH, "₹ %.2f", memo.totalPayableWithDues),
                        fontWeight = FontWeight.Black,
                        fontSize = 16.5.sp,
                        color = MaroonPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (memo.hasPendingBalance) DueCrimsonContainer else BotanicalGreenContainer
                    ) {
                        Text(
                            text = if (memo.hasPendingBalance)
                                "DUE: ₹${String.format(Locale.ENGLISH, "%.0f", memo.effectiveBalanceDue)}"
                            else
                                "✓ PAID",
                            color = if (memo.hasPendingBalance) DueCrimson else BotanicalGreen,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.5.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = memo.customerName.ifBlank { "Walk-in Customer" },
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )

            if (memo.customerMobile.isNotBlank()) {
                Text(
                    text = "Mob: ${memo.customerMobile}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (items.isNotEmpty()) {
                Text(
                    text = "${items.size} item(s): " + items.take(3).joinToString(", ") { it.description.ifBlank { "Item" } } + if (items.size > 3) "..." else "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Partial Payment & Next-Day Promise Strip
            if (memo.hasPendingBalance) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DueCrimsonContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Paid: ₹${String.format(Locale.ENGLISH, "%.0f", memo.effectivePaidAmount)} • Rest ₹${String.format(Locale.ENGLISH, "%.0f", memo.effectiveBalanceDue)} (${memo.paymentStatusNote.ifBlank { "Next Day" }})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = DueCrimson,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(thickness = 0.6.dp)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Settle / Calculate Balance Button
                FilledTonalButton(
                    onClick = onSettlePayment,
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (memo.hasPendingBalance) "Settle Due (बकाया जमा)" else "Payment Info",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick Share JPEG
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    val bitmap = CashMemoExporter.renderCashMemoToBitmap(memo, businessProfile, 1200)
                                    CashMemoExporter.saveBitmapToJpeg(context, bitmap, "CashMemo_${memo.memoNumber}.jpg")
                                }
                                val shareIntent = CashMemoExporter.getShareIntent(context, file, "image/jpeg")
                                context.startActivity(Intent.createChooser(shareIntent, "Share Cash Memo Image"))
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                    }

                    // Quick PDF
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    CashMemoExporter.exportToPdf(context, memo, businessProfile, "CashMemo_${memo.memoNumber}.pdf")
                                }
                                val shareIntent = CashMemoExporter.getShareIntent(context, file, "application/pdf")
                                context.startActivity(Intent.createChooser(shareIntent, "Share Cash Memo PDF"))
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", modifier = Modifier.size(18.dp))
                    }

                    // Delete
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
