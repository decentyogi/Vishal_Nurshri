package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CashMemoEntity
import com.example.data.repository.CashMemoRepository
import com.example.ui.CashMemoViewModel
import com.example.ui.theme.MaroonPrimary
import com.example.util.CashMemoExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: CashMemoViewModel,
    onOpenMemo: (CashMemoEntity) -> Unit,
    onNewMemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val memos by viewModel.historyMemos.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val businessProfile by viewModel.businessProfile.collectAsState()

    var memoToDelete by remember { mutableStateOf<CashMemoEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Saved Cash Memos",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewMemo,
                containerColor = MaroonPrimary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_new_memo")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Memo")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search by customer name or memo #...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("history_search_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            if (memos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No cash memos yet" else "No matching memos found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Scan or create your first cash memo",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNewMemo,
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Memo")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                ) {
                    items(
                        items = memos,
                        key = { it.id },
                        contentType = { "memo_history_item" }
                    ) { memo ->
                        HistoryMemoCard(
                            memo = memo,
                            businessProfile = businessProfile,
                            onOpen = { onOpenMemo(memo) },
                            onDelete = { memoToDelete = memo }
                        )
                    }
                }
            }
        }
    }

    if (memoToDelete != null) {
        AlertDialog(
            onDismissRequest = { memoToDelete = null },
            title = { Text("Delete Cash Memo?") },
            text = { Text("Are you sure you want to delete Cash Memo #${memoToDelete?.memoNumber} for ${memoToDelete?.customerName}?") },
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
private fun HistoryMemoCard(
    memo: com.example.data.model.CashMemoEntity,
    businessProfile: com.example.data.model.BusinessProfile,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val items = remember(memo) {
        com.example.data.repository.CashMemoRepository.deserializeItems(memo.itemsJson)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "#${memo.memoNumber}",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = memo.date,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = String.format(Locale.ENGLISH, "₹ %.2f", memo.grandTotal),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = memo.customerName.ifBlank { "Walk-in Customer" },
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            if (memo.customerMobile.isNotBlank()) {
                Text(
                    text = "Ph: ${memo.customerMobile}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${items.size} item(s): " + items.take(3).joinToString(", ") { it.description.ifBlank { "Item" } } + if (items.size > 3) "..." else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(thickness = 0.6.dp)
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick Share JPEG
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                com.example.util.CashMemoExporter.renderCashMemoToBitmap(memo, businessProfile, 1200)
                            }
                            val file = com.example.util.CashMemoExporter.saveBitmapToJpeg(context, bitmap, "CashMemo_${memo.memoNumber}.jpg")
                            val shareIntent = com.example.util.CashMemoExporter.getShareIntent(context, file, "image/jpeg")
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Cash Memo Image"))
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
                            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                com.example.util.CashMemoExporter.exportToPdf(context, memo, businessProfile, "CashMemo_${memo.memoNumber}.pdf")
                            }
                            val shareIntent = com.example.util.CashMemoExporter.getShareIntent(context, file, "application/pdf")
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Cash Memo PDF"))
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
