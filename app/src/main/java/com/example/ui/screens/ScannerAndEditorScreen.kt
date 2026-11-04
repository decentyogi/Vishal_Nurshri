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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.data.model.CashMemoItem
import com.example.data.model.CustomerProfileEntity
import com.example.ui.CashMemoViewModel
import com.example.ui.components.AddOrEditCustomerDialog
import com.example.ui.components.CustomerBalanceHistoryDialog
import com.example.ui.components.CustomerPickerDialog
import com.example.ui.components.PaymentCalculatorDialog
import com.example.ui.theme.*
import com.example.util.BitmapUtils
import com.example.util.NumberToWordsConverter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
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
    val savedCustomers by viewModel.allCustomersUnfiltered.collectAsState()

    var showTextInputDialog by remember { mutableStateOf(false) }
    var roughTextPrompt by remember { mutableStateOf("") }
    var showDemoPicker by remember { mutableStateOf(false) }
    var showPaymentCalculatorDialog by remember { mutableStateOf(false) }
    var showCustomerPickerDialog by remember { mutableStateOf(false) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var customerForHistoryDialog by remember { mutableStateOf<CustomerProfileEntity?>(null) }

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

    // Memoized computations
    val subtotalFormatted = remember(editorState.subtotal) {
        String.format(Locale.ENGLISH, "₹ %.2f", editorState.subtotal)
    }
    val grandTotalFormatted = remember(editorState.grandTotal) {
        String.format(Locale.ENGLISH, "₹ %.2f", editorState.grandTotal)
    }
    val totalPayableFormatted = remember(editorState.totalPayable) {
        String.format(Locale.ENGLISH, "₹ %.2f", editorState.totalPayable)
    }
    val paidAmountFormatted = remember(editorState.effectivePaidAmount) {
        String.format(Locale.ENGLISH, "₹ %.2f", editorState.effectivePaidAmount)
    }
    val balanceDueFormatted = remember(editorState.balanceDue) {
        String.format(Locale.ENGLISH, "₹ %.2f", editorState.balanceDue)
    }
    val grandTotalWords = remember(editorState.totalPayable) {
        NumberToWordsConverter.convertToIndianRupees(editorState.totalPayable)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaroonPrimary.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.LocalFlorist,
                                    contentDescription = null,
                                    tint = MaroonPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = businessProfile.shopNameHindi,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                color = MaroonPrimary
                            )
                            Text(
                                text = "Digital Cash Memo & Billing Studio",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showPaymentCalculatorDialog = true },
                        modifier = Modifier.testTag("top_payment_calc_button")
                    ) {
                        Icon(
                            Icons.Default.Calculate,
                            contentDescription = "Payment & Balance Calculator",
                            tint = MaroonPrimary
                        )
                    }

                    IconButton(
                        onClick = {
                            val itemsJson = viewModel.exportCurrentMemoItemsJson()
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("CashMemoItems", itemsJson))
                            Toast.makeText(context, "Copied active memo items JSON to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = "Export Active Items",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    FilledTonalButton(
                        onClick = { viewModel.resetEditor() },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .testTag("reset_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Form", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                shadowElevation = 10.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tappable Summary & Due Status in Bottom Bar
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showPaymentCalculatorDialog = true }
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (editorState.previousDues > 0) "Total Payable (inc. Dues)" else "Total Bill",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Calculate,
                                contentDescription = "Calculate Payment",
                                tint = MaroonPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = totalPayableFormatted,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MaroonPrimary
                        )
                        if (editorState.balanceDue > 0.009) {
                            Text(
                                text = "Paid: $paidAmountFormatted • Due: $balanceDueFormatted",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DueCrimson,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (editorState.hasCustomPayment) {
                            Text(
                                text = "✓ Paid Full: $paidAmountFormatted",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BotanicalGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            viewModel.saveAndPreviewMemo {
                                onNavigateToPreview()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("generate_memo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generate Bill",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.5.sp
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. PREMIUM HERO AI SCANNER BANNER
            item(key = "scanner_card") {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaroonContainer.copy(alpha = 0.45f),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaroonPrimary,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.DocumentScanner,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Smart Rough Bill Scanner",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.5.sp,
                                        color = MaroonPrimaryDark
                                    )
                                    Text(
                                        text = "Scan handwritten slip or type notes to auto-fill",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = GoldContainer,
                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .clickable { showDemoPicker = true }
                                    .testTag("demo_samples_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = GoldAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Demo",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7A4F00)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Camera Button
                            Button(
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
                                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("camera_scan_button"),
                                shape = RoundedCornerShape(11.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Camera", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }

                            // Gallery Button
                            FilledTonalButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("gallery_picker_button"),
                                shape = RoundedCornerShape(11.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Gallery", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }

                            // Text Note Button
                            OutlinedButton(
                                onClick = { showTextInputDialog = true },
                                modifier = Modifier
                                    .weight(0.85f)
                                    .height(42.dp)
                                    .testTag("paste_text_button"),
                                shape = RoundedCornerShape(11.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.EditNote, contentDescription = "Text Note", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Type", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // AI Analyzing / Validation Feedback Cards
            if (editorState.isAnalyzing) {
                item(key = "analyzing_card") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GoldContainer),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaroonPrimary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Reading rough bill & verifying math...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = InkTextPrimary
                                )
                                Text(
                                    text = "Extracting customer, plant items, and totals automatically",
                                    fontSize = 11.5.sp,
                                    color = InkTextSecondary
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
                        shape = RoundedCornerShape(14.dp)
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
                        colors = CardDefaults.cardColors(containerColor = BotanicalGreenContainer),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, BotanicalGreen.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BotanicalGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = editorState.aiValidationNote ?: "",
                                fontSize = 12.sp,
                                color = OnBotanicalGreenContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 2. CUSTOMER & MEMO DETAILS CARD
            item(key = "customer_memo_card") {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Section Header + Memo Number Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PersonOutline,
                                    contentDescription = null,
                                    tint = MaroonPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Customer & Bill Info (ग्राहक विवरण)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = MaroonPrimary
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "नं० ",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = MaroonPrimary
                                )
                                CompactNumberInputBox(
                                    value = editorState.memoNumber,
                                    onValueChange = { viewModel.setMemoNumber(it) },
                                    placeholder = "501",
                                    modifier = Modifier.width(82.dp),
                                    testTag = "memo_number_input"
                                )
                            }
                        }

                        // Saved Customer Quick Selector & Auto-Complete Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { showCustomerPickerDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("select_customer_profile_button")
                            ) {
                                Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Saved Customers (${savedCustomers.size})",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (savedCustomers.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    savedCustomers.take(6).forEach { cust ->
                                        val isSelected = editorState.customerName == cust.name && editorState.customerMobile == cust.phoneNumber
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) MaroonContainer else if (cust.hasOutstandingBalance) DueCrimsonContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surfaceVariant,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) MaroonPrimary else if (cust.hasOutstandingBalance) DueCrimson.copy(alpha = 0.4f) else Color.Transparent
                                            ),
                                            modifier = Modifier.clickable {
                                                viewModel.selectCustomerProfileForEditor(cust)
                                                Toast.makeText(
                                                    context,
                                                    if (cust.hasOutstandingBalance)
                                                        "Loaded ${cust.name} + Previous Due ₹${String.format(Locale.ENGLISH, "%.0f", cust.outstandingBalance)}"
                                                    else
                                                        "Loaded ${cust.name}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = cust.name.substringBefore("(").trim(),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (cust.hasOutstandingBalance) DueCrimson else InkTextPrimary
                                                )
                                                if (cust.hasOutstandingBalance) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "• ₹${String.format(Locale.ENGLISH, "%.0f", cust.outstandingBalance)} Due",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = DueCrimson
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Customer Name
                        OutlinedTextField(
                            value = editorState.customerName,
                            onValueChange = { viewModel.setCustomerName(it) },
                            label = { Text("ग्राहक का नाम (Customer Name)") },
                            placeholder = { Text("Enter customer full name") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("customer_name_input"),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(19.dp))
                            }
                        )

                        // Two-column row for Mobile & Date
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = editorState.customerMobile,
                                onValueChange = { viewModel.setCustomerMobile(it) },
                                label = { Text("मो० (Mobile No.)") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .testTag("customer_mobile_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                leadingIcon = {
                                    Icon(Icons.Default.Phone, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(18.dp))
                                }
                            )

                            OutlinedTextField(
                                value = editorState.date,
                                onValueChange = { viewModel.setDate(it) },
                                label = { Text("दिनांक (Date)") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(0.95f)
                                    .testTag("date_input"),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(17.dp))
                                }
                            )
                        }

                        // Address
                        OutlinedTextField(
                            value = editorState.customerAddress,
                            onValueChange = { viewModel.setCustomerAddress(it) },
                            label = { Text("पता / शहर (Address / Village / City)") },
                            placeholder = { Text("e.g. सासाराम (रोहतास)") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("customer_address_input"),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaroonPrimary, modifier = Modifier.size(19.dp))
                            }
                        )
                    }
                }
            }

            // 3. BILL ITEMS SECTION HEADER
            item(key = "items_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Spa,
                            contentDescription = null,
                            tint = BotanicalGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "पौधा व सामग्री विवरण (Bill Items)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.5.sp,
                            color = MaroonPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaroonContainer
                        ) {
                            Text(
                                text = "${editorState.items.size}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Black,
                                color = MaroonPrimaryDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.addItem() },
                        colors = ButtonDefaults.buttonColors(containerColor = BotanicalGreen),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("add_item_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Item", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Item", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 4. BILL ITEM CARDS
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
                    onIncrementQty = { viewModel.incrementItemQty(index) },
                    onDecrementQty = { viewModel.decrementItemQty(index) },
                    onRemove = { viewModel.removeItem(index) }
                )
            }

            // 5. SUMMARY & TOTALS SECTION + PAYMENT & NEXT-DAY BALANCE CALCULATOR
            item(key = "calculations_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = BorderStroke(1.2.dp, MaroonPrimary.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = MaroonPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "गणना और कुल योग (Summary & Totals)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.5.sp,
                                    color = MaroonPrimary
                                )
                            }
                        }

                        // Subtotal Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Subtotal (कुल योग):",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = subtotalFormatted,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Discount Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Discount (छूट ₹):",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium
                            )
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

                        // Tax / GST Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "GST / Tax (%):",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium
                            )
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

                        // Previous Dues Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Previous Dues (पिछला बकाया):",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium
                            )
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

                        // GRAND TOTAL / TOTAL PAYABLE HIGHLIGHT BANNER
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaroonContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaroonPrimary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (editorState.previousDues > 0) "Total Payable (कुल देय):" else "Grand Total (कुल बिल राशि):",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.5.sp,
                                            color = MaroonPrimaryDark
                                        )
                                        if (editorState.previousDues > 0) {
                                            Text(
                                                text = "Current Bill: $grandTotalFormatted + Prev Dues",
                                                fontSize = 11.sp,
                                                color = InkTextSecondary
                                            )
                                        }
                                    }

                                    Text(
                                        text = totalPayableFormatted,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 21.sp,
                                        color = MaroonPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "शब्दों में: $grandTotalWords",
                                    fontSize = 11.5.sp,
                                    color = MaroonPrimaryDark.copy(alpha = 0.85f),
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // NEW: DEDICATED PAID & NEXT-DAY BALANCE SECTION INSIDE SUMMARY & TOTALS
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (editorState.balanceDue > 0.009) {
                                DueCrimsonContainer.copy(alpha = 0.5f)
                            } else {
                                BotanicalGreenContainer.copy(alpha = 0.5f)
                            },
                            border = BorderStroke(
                                1.5.dp,
                                if (editorState.balanceDue > 0.009) DueCrimson.copy(alpha = 0.45f)
                                else BotanicalGreen.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPaymentCalculatorDialog = true }
                                .testTag("summary_payment_status_card")
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (editorState.balanceDue > 0.009) Icons.Default.EventRepeat else Icons.Default.Payments,
                                            contentDescription = null,
                                            tint = if (editorState.balanceDue > 0.009) DueCrimson else BotanicalGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "जमा और शेष बकाया (Paid & Balance)",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp,
                                                color = if (editorState.balanceDue > 0.009) DueCrimson else OnBotanicalGreenContainer
                                            )
                                            Text(
                                                text = "Tap to calculate partial payment (e.g. ₹1000 bill, ₹800 paid)",
                                                fontSize = 11.sp,
                                                color = InkTextSecondary
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (editorState.balanceDue > 0.009) DueCrimson else BotanicalGreen
                                    ) {
                                        Text(
                                            text = if (editorState.balanceDue > 0.009) "PARTIAL DUE" else "FULL PAID",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                // 3-Column Visual Breakdown (Total Bill | Paid Now | Balance Due)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Total
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Total Bill (कुल)",
                                            fontSize = 11.sp,
                                            color = InkTextSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = totalPayableFormatted,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = InkTextPrimary
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(32.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )

                                    // Paid Now
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Paid Now (जमा)",
                                            fontSize = 11.sp,
                                            color = BotanicalGreen,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = paidAmountFormatted,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Black,
                                            color = BotanicalGreen,
                                            modifier = Modifier.testTag("summary_paid_amount_text")
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(32.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )

                                    // Balance Due
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Rest Due (बकाया)",
                                            fontSize = 11.sp,
                                            color = if (editorState.balanceDue > 0.009) DueCrimson else InkTextSecondary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = balanceDueFormatted,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (editorState.balanceDue > 0.009) DueCrimson else BotanicalGreen,
                                            modifier = Modifier.testTag("summary_balance_due_text")
                                        )
                                    }
                                }

                                // Promise Date / Next Day Banner when balance > 0
                                if (editorState.balanceDue > 0.009) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = DueCrimson.copy(alpha = 0.1f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(
                                                    Icons.Default.Schedule,
                                                    contentDescription = null,
                                                    tint = DueCrimson,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Rest $balanceDueFormatted will be paid: ${editorState.paymentDueNote}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DueCrimson
                                                )
                                            }
                                            Text(
                                                text = editorState.paymentMethod,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaroonPrimary
                                            )
                                        }
                                    }
                                }

                                // Button to Open Payment & Balance Calculator Dialog
                                Button(
                                    onClick = { showPaymentCalculatorDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (editorState.balanceDue > 0.009) DueCrimson else MaroonPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("open_payment_calculator_button")
                                ) {
                                    Icon(
                                        Icons.Default.Calculate,
                                        contentDescription = null,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Calculate Paid & Next-Day Balance (जमा / बकाया डायलॉग)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // PAYMENT & NEXT-DAY BALANCE CALCULATOR DIALOG
    if (showPaymentCalculatorDialog) {
        PaymentCalculatorDialog(
            initialTotalBill = editorState.totalPayable,
            initialPaidAmount = editorState.effectivePaidAmount,
            hasExistingCustomPayment = editorState.hasCustomPayment,
            initialPaymentMethod = editorState.paymentMethod,
            initialPaymentDueNote = editorState.paymentDueNote,
            allowEditingTotalBill = true,
            onDismiss = { showPaymentCalculatorDialog = false },
            onApply = { totalOverride, paid, method, dueNote ->
                viewModel.applyPaymentCalculation(
                    billTotalOverride = totalOverride,
                    paidAmount = paid,
                    paymentMethod = method,
                    paymentDueNote = dueNote
                )
                Toast.makeText(
                    context,
                    "Updated Summary: Paid ₹${String.format(Locale.ENGLISH, "%.0f", paid)}",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onResetToFullPaid = {
                viewModel.resetCustomPayment()
            }
        )
    }

    // CUSTOMER PROFILE PICKER DIALOG
    if (showCustomerPickerDialog) {
        CustomerPickerDialog(
            customers = savedCustomers,
            onDismiss = { showCustomerPickerDialog = false },
            onSelectCustomer = { customer ->
                viewModel.selectCustomerProfileForEditor(customer)
                Toast.makeText(
                    context,
                    if (customer.hasOutstandingBalance)
                        "Auto-filled ${customer.name} & Previous Due ₹${String.format(Locale.ENGLISH, "%.0f", customer.outstandingBalance)}"
                    else
                        "Auto-filled ${customer.name}",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onViewHistory = { customer ->
                showCustomerPickerDialog = false
                customerForHistoryDialog = customer
            },
            onAddNewCustomer = {
                showCustomerPickerDialog = false
                showAddCustomerDialog = true
            }
        )
    }

    // ADD NEW CUSTOMER PROFILE DIALOG
    if (showAddCustomerDialog) {
        AddOrEditCustomerDialog(
            initialCustomer = null,
            onDismiss = { showAddCustomerDialog = false },
            onSave = { id, name, phone, addr, bal, note ->
                viewModel.saveOrUpdateCustomer(id, name, phone, addr, bal, note)
                viewModel.setCustomerName(name)
                viewModel.setCustomerMobile(phone)
                viewModel.setCustomerAddress(addr)
                viewModel.setPreviousDues(bal)
                Toast.makeText(context, "Saved customer $name & loaded into bill!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // CUSTOMER OUTSTANDING BALANCE HISTORY DIALOG
    if (customerForHistoryDialog != null) {
        val targetCust = customerForHistoryDialog!!
        val historyFlow = remember(targetCust.id) {
            viewModel.getBalanceHistoryForCustomer(targetCust.id)
        }
        val historyList by historyFlow.collectAsState(initial = emptyList())

        CustomerBalanceHistoryDialog(
            customer = targetCust,
            historyList = historyList,
            onDismiss = { customerForHistoryDialog = null },
            onCreateBillForCustomer = { cust ->
                viewModel.selectCustomerProfileForEditor(cust)
                customerForHistoryDialog = null
                Toast.makeText(context, "Loaded ${cust.name} into bill!", Toast.LENGTH_SHORT).show()
            },
            onSettleBalance = { cust ->
                viewModel.selectCustomerProfileForEditor(cust)
                customerForHistoryDialog = null
                showPaymentCalculatorDialog = true
            }
        )
    }

    // Rough text input dialog
    if (showTextInputDialog) {
        AlertDialog(
            onDismissRequest = { showTextInputDialog = false },
            title = { Text("Paste Rough Notes / Bill Text", fontWeight = FontWeight.Bold) },
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
            title = { Text("Try Sample Rough Bills", fontWeight = FontWeight.Bold) },
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
 * Isolated Composable for individual Bill Item with sleek modern layout and stepper controls.
 */
@Composable
private fun BillItemCard(
    index: Int,
    item: CashMemoItem,
    canDelete: Boolean,
    onUpdate: (String, Double, Double) -> Unit,
    onIncrementQty: () -> Unit,
    onDecrementQty: () -> Unit,
    onRemove: () -> Unit
) {
    val formattedAmount = remember(item.amount) {
        String.format(Locale.ENGLISH, "₹ %.2f", item.amount)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Serial number badge, Calculated Amount Pill, Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaroonPrimary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "#${item.serialNo}",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (item.description.isNotBlank()) item.description else "Plant / Nursery Item",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 150.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = BotanicalGreenContainer,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BotanicalGreen.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "रकम: ",
                                fontSize = 11.5.sp,
                                color = OnBotanicalGreenContainer
                            )
                            Text(
                                text = formattedAmount,
                                fontWeight = FontWeight.Black,
                                color = BotanicalGreen,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(32.dp),
                        enabled = canDelete
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Remove Item",
                            tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Description Field
            OutlinedTextField(
                value = item.description,
                onValueChange = { newDesc ->
                    onUpdate(newDesc, item.quantity, item.rate)
                },
                label = { Text("पौधा / सामग्री विवरण (Item Name)") },
                placeholder = { Text("e.g. गुलाब का पौधा (Red Rose Plant)") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.LocalFlorist, contentDescription = null, tint = BotanicalGreen, modifier = Modifier.size(18.dp))
                }
            )

            // Qty (with +/- buttons) and Rate Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Quantity with quick [-] and [+] buttons
                Column(modifier = Modifier.weight(1.1f)) {
                    Text(
                        text = "संख्या (Qty)",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(42.dp)
                                .clickable { onDecrementQty() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease Qty", modifier = Modifier.size(18.dp))
                            }
                        }

                        CompactNumberInputBox(
                            value = if (item.quantity == 0.0) "" else if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString(),
                            onValueChange = { qtyStr ->
                                val sanitized = qtyStr.replace(',', '.').trim()
                                val q = sanitized.toDoubleOrNull() ?: 0.0
                                onUpdate(item.description, q, item.rate)
                            },
                            placeholder = "1",
                            modifier = Modifier.weight(1f)
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaroonContainer,
                            modifier = Modifier
                                .size(42.dp)
                                .clickable { onIncrementQty() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, contentDescription = "Increase Qty", tint = MaroonPrimaryDark, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Rate
                Column(modifier = Modifier.weight(0.95f)) {
                    Text(
                        text = "दाम (Rate ₹)",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
 * Guarantees numbers are centered vertically and never clipped.
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
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.height(42.dp)
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
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 3.dp)
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.End,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
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
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }
}
