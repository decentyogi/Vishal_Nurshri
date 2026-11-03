package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.CashMemoItem
import com.example.ui.CashMemoViewModel
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.MaroonPrimary
import com.example.util.BitmapUtils
import com.example.util.NumberToWordsConverter
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerAndEditorScreen(
    viewModel: CashMemoViewModel,
    onNavigateToPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val editorState by viewModel.editorState.collectAsState()
    val businessProfile by viewModel.businessProfile.collectAsState()

    var showTextInputDialog by remember { mutableStateOf(false) }
    var roughTextPrompt by remember { mutableStateOf("") }
    var showDemoPicker by remember { mutableStateOf(false) }

    // Camera Capture setup using FileProvider & BitmapUtils
    var tempCameraImageUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraImageUri != null) {
            coroutineScope.launch {
                try {
                    val bitmap = BitmapUtils.decodeSampledBitmap(context, tempCameraImageUri!!)
                    if (bitmap != null) {
                        viewModel.analyzeRoughBillImage(bitmap)
                    } else {
                        Toast.makeText(context, "Could not process captured photo", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val launchCameraSafe = {
        try {
            val (uri, _) = BitmapUtils.createTempCameraUri(context)
            tempCameraImageUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open camera: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Permission launcher for CAMERA
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCameraSafe()
        } else {
            Toast.makeText(
                context,
                "Camera permission is required to scan handwritten rough bills",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Gallery Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val bitmap = BitmapUtils.decodeSampledBitmap(context, uri)
                    if (bitmap != null) {
                        viewModel.analyzeRoughBillImage(bitmap)
                    } else {
                        Toast.makeText(context, "Could not load selected image", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Memoized computations to eliminate scroll lag
    val subtotalFormatted = remember(editorState.subtotal) {
        String.format(Locale.ENGLISH, "₹ %.2f", editorState.subtotal)
    }
    val grandTotalFormatted = remember(editorState.grandTotal) {
        String.format(Locale.ENGLISH, "₹ %.2f", editorState.grandTotal)
    }
    val duesFormatted = remember(editorState.previousDues) {
        String.format(Locale.ENGLISH, "₹ %.2f", editorState.previousDues)
    }
    val totalPayableFormatted = remember(editorState.totalPayable) {
        String.format(Locale.ENGLISH, "₹ %.2f", editorState.totalPayable)
    }
    val grandTotalWords = remember(editorState.totalPayable) {
        NumberToWordsConverter.convertToIndianRupees(editorState.totalPayable)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "New Cash Memo",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = businessProfile.shopNameHindi,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val itemsJson = viewModel.exportCurrentMemoItemsJson()
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("CashMemoItems", itemsJson))
                            Toast.makeText(context, "Copied active memo items JSON to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Export Active Items", tint = MaterialTheme.colorScheme.primary)
                    }

                    TextButton(
                        onClick = { viewModel.resetEditor() },
                        modifier = Modifier.testTag("reset_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Form", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (editorState.previousDues > 0) "Total Payable (inc. Dues)" else "Grand Total",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = totalPayableFormatted,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.saveAndPreviewMemo {
                                onNavigateToPreview()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("generate_memo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generate Cash Memo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Scanner / Capture Options Card
            item(key = "scanner_card") {
                Spacer(modifier = Modifier.height(2.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Scan Rough Bill with AI",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Take a photo of handwritten rough bill notes or scratchpad to auto-extract items, calculate totals, and fix arithmetic errors.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Camera Button with Safe Runtime Permission Check
                            FilledTonalButton(
                                onClick = {
                                    val hasCameraPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasCameraPermission) {
                                        launchCameraSafe()
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("camera_scan_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Camera", fontSize = 13.sp)
                            }

                            // Gallery Button
                            FilledTonalButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("gallery_picker_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Gallery", fontSize = 13.sp)
                            }

                            // Text / Paste Button
                            OutlinedButton(
                                onClick = { showTextInputDialog = true },
                                modifier = Modifier.testTag("paste_text_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.EditNote, contentDescription = "Text Note", modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Quick Demo Samples
                        TextButton(
                            onClick = { showDemoPicker = true },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .testTag("demo_samples_button")
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Try Nursery Demo Rough Bill", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // AI Analyzing State / Validation feedback
            if (editorState.isAnalyzing) {
                item(key = "analyzing_card") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Gemini AI is reading rough bill...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Extracting customer, plant items, checking multiplication...",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (editorState.analysisError != null) {
                item(key = "error_card") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = editorState.analysisError ?: "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            if (!editorState.aiValidationNote.isNullOrBlank() && !editorState.isAnalyzing) {
                item(key = "validation_card") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF81C784))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = editorState.aiValidationNote ?: "",
                                fontSize = 12.sp,
                                color = Color(0xFF1B5E20),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Customer & Memo Details Card
            item(key = "customer_memo_card") {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Customer & Memo Details",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Clean Memo Number Badge - NO text cutoff
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "नं० ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                CompactNumberInputBox(
                                    value = editorState.memoNumber,
                                    onValueChange = { viewModel.setMemoNumber(it) },
                                    placeholder = "501",
                                    modifier = Modifier.width(84.dp),
                                    testTag = "memo_number_input"
                                )
                            }
                        }

                        // Date field
                        OutlinedTextField(
                            value = editorState.date,
                            onValueChange = { viewModel.setDate(it) },
                            label = { Text("दिनांक (Date)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("date_input"),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )

                        // Customer Name
                        OutlinedTextField(
                            value = editorState.customerName,
                            onValueChange = { viewModel.setCustomerName(it) },
                            label = { Text("ग्राहक का नाम (Customer Name)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("customer_name_input"),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )

                        // Mobile Number
                        OutlinedTextField(
                            value = editorState.customerMobile,
                            onValueChange = { viewModel.setCustomerMobile(it) },
                            label = { Text("मो० (Mobile Number)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("customer_mobile_input"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )

                        // Address
                        OutlinedTextField(
                            value = editorState.customerAddress,
                            onValueChange = { viewModel.setCustomerAddress(it) },
                            label = { Text("पता (Address / City)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("customer_address_input"),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }

            // Items List Section Header
            item(key = "items_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "पौधा व सामग्री विवरण (Bill Items)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    FilledTonalButton(
                        onClick = { viewModel.addItem() },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_item_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Item", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Item", fontSize = 12.sp)
                    }
                }
            }

            // Dynamic Rows for each bill item with stable key and contentType for silky smooth scrolling
            itemsIndexed(
                items = editorState.items,
                key = { _, item -> item.id },
                contentType = { _, _ -> "bill_item" }
            ) { index, item ->
                BillItemCard(
                    index = index,
                    item = item,
                    canDelete = editorState.items.size > 1,
                    onUpdate = { desc, q, r -> viewModel.updateItem(index, desc, q, r) },
                    onRemove = { viewModel.removeItem(index) }
                )
            }

            // Calculations & Discounts card - Includes Dues tile!
            item(key = "calculations_card") {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "गणना और छूट (Summary & Totals)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Subtotal
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Subtotal (कुल योग):", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = subtotalFormatted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        // Discount Row - Uses custom CompactNumberInputBox
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Discount (छूट):", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                            CompactNumberInputBox(
                                value = if (editorState.discount == 0.0) "" else {
                                    if (editorState.discount % 1.0 == 0.0) editorState.discount.toInt().toString() else editorState.discount.toString()
                                },
                                onValueChange = {
                                    val sanitized = it.replace(',', '.').trim()
                                    val d = sanitized.toDoubleOrNull() ?: 0.0
                                    viewModel.setDiscount(d)
                                },
                                placeholder = "0.0",
                                prefix = "₹",
                                modifier = Modifier.width(125.dp),
                                testTag = "discount_input"
                            )
                        }

                        // Tax / GST Row - Uses custom CompactNumberInputBox
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "GST / Tax (%):", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                            CompactNumberInputBox(
                                value = if (editorState.taxPercentage == 0.0) "" else {
                                    if (editorState.taxPercentage % 1.0 == 0.0) editorState.taxPercentage.toInt().toString() else editorState.taxPercentage.toString()
                                },
                                onValueChange = {
                                    val sanitized = it.replace(',', '.').trim()
                                    val t = sanitized.toDoubleOrNull() ?: 0.0
                                    viewModel.setTaxPercentage(t)
                                },
                                placeholder = "0%",
                                suffix = "%",
                                modifier = Modifier.width(125.dp),
                                testTag = "tax_input"
                            )
                        }

                        // Previous Dues Row (Tile of Dues)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Previous Dues (पिछला बकाया):", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                            CompactNumberInputBox(
                                value = if (editorState.previousDues == 0.0) "" else {
                                    if (editorState.previousDues % 1.0 == 0.0) editorState.previousDues.toInt().toString() else editorState.previousDues.toString()
                                },
                                onValueChange = {
                                    val sanitized = it.replace(',', '.').trim()
                                    val dues = sanitized.toDoubleOrNull() ?: 0.0
                                    viewModel.setPreviousDues(dues)
                                },
                                placeholder = "0.0",
                                prefix = "₹",
                                modifier = Modifier.width(125.dp),
                                testTag = "dues_input"
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Current Bill Total Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Current Bill Total:",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.5.sp
                            )
                            Text(
                                text = grandTotalFormatted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        // Final Net Payable with Dues
                        if (editorState.previousDues > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Payable (कुल देय):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = totalPayableFormatted,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Grand Total:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = grandTotalFormatted,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // In words
                        Text(
                            text = grandTotalWords,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Rough text input dialog
    if (showTextInputDialog) {
        AlertDialog(
            onDismissRequest = { showTextInputDialog = false },
            title = { Text("Paste Rough Notes / Bill Text") },
            text = {
                Column {
                    Text(
                        text = "Enter raw notes (e.g. '10 rose @ 40, 2 pot @ 100, Ramesh 9876543210')",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = roughTextPrompt,
                        onValueChange = { roughTextPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .testTag("rough_text_input"),
                        placeholder = { Text("Type or paste rough bill notes...") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (roughTextPrompt.isNotBlank()) {
                            viewModel.analyzeRoughBillText(roughTextPrompt)
                            showTextInputDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                ) {
                    Text("Analyze with AI")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextInputDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Demo samples dialog
    if (showDemoPicker) {
        AlertDialog(
            onDismissRequest = { showDemoPicker = false },
            title = { Text("Try Sample Rough Bills") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select a sample rough bill to test automatic item detection and calculation validation:", fontSize = 13.sp)

                    FilledTonalButton(
                        onClick = {
                            viewModel.loadSampleRoughBill(1)
                            showDemoPicker = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("1. Ramesh Kumar (Plants & Vermicompost)", fontWeight = FontWeight.Bold)
                            Text("Rose, Marigold, Croton, 12\" Pot, Compost", fontSize = 11.sp)
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            viewModel.loadSampleRoughBill(2)
                            showDemoPicker = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("2. Anita Devi (Fruit Trees & Planters)", fontWeight = FontWeight.Bold)
                            Text("Mango Amrapali, Guava, Hibiscus, Planter", fontSize = 11.sp)
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            viewModel.loadSampleRoughBill(3)
                            showDemoPicker = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("3. Math Error Correction Test", fontWeight = FontWeight.Bold)
                            Text("Has 5 x 60 = 350 mistake. AI fixes to 300!", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDemoPicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}

/**
 * Isolated Composable for individual Bill Item to prevent recomposition cascades during scrolling.
 */
@Composable
private fun BillItemCard(
    index: Int,
    item: CashMemoItem,
    canDelete: Boolean,
    onUpdate: (String, Double, Double) -> Unit,
    onRemove: () -> Unit
) {
    val formattedAmount = remember(item.amount) {
        String.format(Locale.ENGLISH, "₹ %.2f", item.amount)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Serial number, Calculated Amount Chip, Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "#${item.serialNo}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Dedicated, prominent Amount Display
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "रकम: ",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formattedAmount,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp),
                    enabled = canDelete
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Remove Item",
                        tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Description Field
            OutlinedTextField(
                value = item.description,
                onValueChange = { newDesc ->
                    onUpdate(newDesc, item.quantity, item.rate)
                },
                label = { Text("पौधा / सामग्री विवरण (Item Description)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Qty and Rate in a spacious Row using high-performance CompactNumberInputBox
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quantity
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "संख्या (Qty)",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    CompactNumberInputBox(
                        value = if (item.quantity == 0.0) "" else if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString(),
                        onValueChange = { qtyStr ->
                            val sanitized = qtyStr.replace(',', '.').trim()
                            val q = sanitized.toDoubleOrNull() ?: 0.0
                            onUpdate(item.description, q, item.rate)
                        },
                        placeholder = "1",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Rate
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "दाम (Rate ₹)",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    CompactNumberInputBox(
                        value = if (item.rate == 0.0) "" else if (item.rate % 1.0 == 0.0) item.rate.toInt().toString() else item.rate.toString(),
                        onValueChange = { rateStr ->
                            val sanitized = rateStr.replace(',', '.').trim()
                            val r = sanitized.toDoubleOrNull() ?: 0.0
                            onUpdate(item.description, item.quantity, r)
                        },
                        placeholder = "0.00",
                        prefix = "₹ ",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Compact numeric input box using BasicTextField with a dedicated Surface container.
 * This guarantees the number is centered vertically, NEVER clipped or cut down in half,
 * and maintains consistent clean height across all font scales.
 */
@Composable
private fun CompactNumberInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    suffix: String? = null,
    testTag: String? = null
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            if (prefix != null && value.isNotEmpty()) {
                Text(
                    text = prefix,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.End,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (suffix != null && value.isNotEmpty()) {
                Text(
                    text = suffix,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }
}
