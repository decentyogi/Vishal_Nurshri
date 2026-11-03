package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CashMemoEntity
import com.example.data.repository.CashMemoRepository
import com.example.ui.CashMemoViewModel
import com.example.ui.components.CashMemoPaperSlip
import com.example.ui.theme.MaroonPrimary
import com.example.util.CashMemoExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashMemoPreviewScreen(
    viewModel: CashMemoViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val memo by viewModel.previewMemo.collectAsState()
    val businessProfile by viewModel.businessProfile.collectAsState()

    var isExporting by remember { mutableStateOf(false) }
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var showPrintDialog by remember { mutableStateOf(false) }
    var selectedPrintSize by remember { mutableStateOf("80mm") } // "80mm", "58mm", "A5", "A4"

    BackHandler {
        onBack()
    }

    val allItems = remember(memo) {
        memo?.let { CashMemoRepository.deserializeItems(it.itemsJson) } ?: emptyList()
    }
    val chunkedPages = remember(allItems) {
        if (allItems.isEmpty()) listOf(emptyList()) else allItems.chunked(15)
    }
    val totalPages = chunkedPages.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Cash Memo #${memo?.memoNumber ?: ""}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("preview_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Print Button in Top Bar
                    IconButton(
                        onClick = { showPrintDialog = true },
                        modifier = Modifier.testTag("print_memo_action_button")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print Memo", tint = MaterialTheme.colorScheme.primary)
                    }

                    TextButton(
                        onClick = {
                            memo?.let { viewModel.loadMemoIntoEditor(it) }
                            onBack()
                        },
                        modifier = Modifier.testTag("edit_memo_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Print Button (Prompt & Options)
                    OutlinedButton(
                        onClick = { showPrintDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("print_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Print", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Send / Share as JPEG (1 or 2 images)
                    FilledTonalButton(
                        onClick = {
                            val activeMemo = memo ?: return@FilledTonalButton
                            isExporting = true
                            coroutineScope.launch {
                                try {
                                    val bitmaps = withContext(Dispatchers.Default) {
                                        CashMemoExporter.renderCashMemoPages(activeMemo, businessProfile, 1200)
                                    }
                                    val baseName = "CashMemo_${activeMemo.memoNumber}_${System.currentTimeMillis()}"
                                    val files = CashMemoExporter.saveBitmapsToJpegs(context, bitmaps, baseName)
                                    val shareIntent = CashMemoExporter.getMultipleShareIntent(context, files, "image/jpeg")
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Cash Memo Image(s)"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isExporting = false
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1.1f)
                            .height(46.dp)
                            .testTag("share_jpeg_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (totalPages > 1) "Send $totalPages JPEGs" else "Send JPEG",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // Save / Share as PDF
                    Button(
                        onClick = {
                            val activeMemo = memo ?: return@Button
                            isExporting = true
                            coroutineScope.launch {
                                try {
                                    val fileName = "CashMemo_${activeMemo.memoNumber}_${System.currentTimeMillis()}.pdf"
                                    val file = withContext(Dispatchers.Default) {
                                        CashMemoExporter.exportToPdf(
                                            context = context,
                                            memo = activeMemo,
                                            profile = businessProfile,
                                            fileName = fileName,
                                            pageSize = "A4"
                                        )
                                    }
                                    val shareIntent = CashMemoExporter.getShareIntent(context, file, "application/pdf")
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Cash Memo PDF"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "PDF export error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isExporting = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("save_pdf_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val currentMemo = memo
            if (currentMemo != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Multi-page Selector Tabs if items > 15
                    if (totalPages > 1) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (p in 0 until totalPages) {
                                    val isSelected = selectedPageIndex == p
                                    Button(
                                        onClick = { selectedPageIndex = p },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "Page ${p + 1} (${chunkedPages[p].size} items)",
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Authentic Bill Preview",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    // Render Paper Slip for Selected Page
                    val currentPageItems = chunkedPages.getOrElse(selectedPageIndex) { emptyList() }
                    CashMemoPaperSlip(
                        businessProfile = businessProfile,
                        memoNumber = currentMemo.memoNumber,
                        date = currentMemo.date,
                        customerName = currentMemo.customerName,
                        customerMobile = currentMemo.customerMobile,
                        customerAddress = currentMemo.customerAddress,
                        items = currentPageItems,
                        subtotal = currentMemo.subtotal,
                        discount = currentMemo.discount,
                        taxPercentage = currentMemo.taxPercentage,
                        taxAmount = currentMemo.taxAmount,
                        previousDues = currentMemo.previousDues,
                        grandTotal = currentMemo.grandTotal,
                        pageIndex = selectedPageIndex,
                        totalPages = totalPages
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No cash memo selected for preview.")
                }
            }

            if (isExporting) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaroonPrimary)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Generating documents...")
                            }
                        }
                    }
                }
            }
        }
    }

    // --- PRINT DIALOG WITH PAGE SIZES (80mm, 58mm, A5, A4) ---
    if (showPrintDialog && memo != null) {
        val activeMemo = memo!!
        AlertDialog(
            onDismissRequest = { showPrintDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Print Cash Memo #${activeMemo.memoNumber}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose paper size matching your printer (small receipt vs sheet):",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val sizes = listOf(
                        Pair("80mm", "80mm / 3 inch (Standard Cash Memo Slip)"),
                        Pair("58mm", "58mm / 2 inch (Mini Thermal Receipt)"),
                        Pair("A5", "A5 (Half Page Bill Book)"),
                        Pair("A4", "A4 (Full Sheet Document)")
                    )

                    sizes.forEach { (code, label) ->
                        val isSelected = selectedPrintSize == code
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.5.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPrintSize = code }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedPrintSize = code }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    if (totalPages > 1) {
                        Surface(
                            color = Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Note: This bill has ${allItems.size} items and will print on $totalPages pages (max 15 items per slip).",
                                fontSize = 11.5.sp,
                                color = Color(0xFFE65100),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPrintDialog = false
                        try {
                            CashMemoExporter.printCashMemo(
                                context = context,
                                memo = activeMemo,
                                profile = businessProfile,
                                pageSize = selectedPrintSize
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "Print error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Print Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrintDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
