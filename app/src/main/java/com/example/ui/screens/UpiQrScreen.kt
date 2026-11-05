package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.CashMemoViewModel
import com.example.ui.theme.*
import com.example.util.QrCodeGenerator
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Dedicated UPI Dynamic QR Code Generator Tab & Full-Screen QR Display Modal.
 * Fixed VPA: dangerousop@ybl | Payee Name: Vishal Nursery
 * URI Scheme: upi://pay?pa=dangerousop@ybl&pn=Vishal%20Nursery&am={AMOUNT}&cu=INR&tn=Plant%20Purchase
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpiQrScreen(
    viewModel: CashMemoViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val editorState by viewModel.editorState.collectAsState()

    val activeBillPayable = editorState.totalPayable
    val activeBillBalance = if (editorState.hasCustomPayment && editorState.balanceDue > 0.009) {
        editorState.balanceDue
    } else {
        activeBillPayable
    }

    var amountInput by remember(activeBillBalance) {
        mutableStateOf(
            if (activeBillBalance > 0.0) {
                if (activeBillBalance % 1.0 == 0.0) {
                    activeBillBalance.toInt().toString()
                } else {
                    String.format(Locale.ENGLISH, "%.2f", activeBillBalance)
                }
            } else {
                ""
            }
        )
    }

    val numericAmount = remember(amountInput) {
        amountInput.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    val upiUri = remember(numericAmount) {
        QrCodeGenerator.buildUpiUri(
            amount = numericAmount,
            vpa = QrCodeGenerator.DEFAULT_VPA,
            payeeName = QrCodeGenerator.DEFAULT_PAYEE_NAME,
            transactionNote = QrCodeGenerator.DEFAULT_TRANSACTION_NOTE
        )
    }

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showFullScreenQrModal by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }

    // Generate QR bitmap asynchronously on Dispatchers.Default whenever UPI URI changes
    LaunchedEffect(upiUri) {
        qrBitmap = QrCodeGenerator.generateQrBitmap(upiUri, sizePx = 720)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "UPI Payment QR Generator",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = MaroonPrimary
                        )
                        Text(
                            text = "${QrCodeGenerator.DEFAULT_PAYEE_NAME} • ${QrCodeGenerator.DEFAULT_VPA}",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showFullScreenQrModal = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .testTag("open_fullscreen_qr_top_btn")
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Full Screen QR", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Full Screen", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. Dynamic Amount Input Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Enter Payment Amount (भुगतान राशि ₹)",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.5.sp,
                        color = MaroonPrimary
                    )

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() || it == '.' }) {
                                amountInput = newValue
                            }
                        },
                        label = { Text("Custom Rupee Amount (₹)") },
                        placeholder = { Text("Enter amount e.g. 800") },
                        prefix = {
                            Text(
                                text = "₹ ",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = MaroonPrimary
                            )
                        },
                        trailingIcon = {
                            if (amountInput.isNotEmpty()) {
                                IconButton(onClick = { amountInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear Amount")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upi_amount_input")
                    )

                    // Quick amount presets & active bill total chip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (activeBillPayable > 0.0) {
                            val billAmtStr = if (activeBillPayable % 1.0 == 0.0) {
                                activeBillPayable.toInt().toString()
                            } else {
                                String.format(Locale.ENGLISH, "%.2f", activeBillPayable)
                            }
                            SuggestionChip(
                                onClick = { amountInput = billAmtStr },
                                label = {
                                    Text(
                                        text = "Bill: ₹$billAmtStr",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaroonContainer,
                                    labelColor = MaroonPrimaryDark
                                )
                            )
                        }

                        listOf("100", "200", "500", "1000").forEach { preset ->
                            SuggestionChip(
                                onClick = { amountInput = preset },
                                label = { Text("₹$preset", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }
                }
            }

            // 2. High-Contrast QR Code Display Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = BorderStroke(2.dp, MaroonPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showFullScreenQrModal = true }
                    .testTag("upi_qr_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Badge
                    Surface(
                        color = MaroonPrimary,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "विशाल नर्सरी • VISHAL NURSERY",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.5.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Scan with Google Pay • PhonePe • Paytm • BHIM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4A3B3E),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Crisp QR Bitmap Box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Color(0xFFE0D4D6)),
                        modifier = Modifier.size(250.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = qrBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "UPI Payment QR Code for Vishal Nursery",
                                    filterQuality = FilterQuality.None,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("upi_qr_bitmap_image")
                                )
                            } else {
                                CircularProgressIndicator(color = MaroonPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dynamic Amount Pill
                    if (numericAmount != null && numericAmount > 0.0) {
                        Surface(
                            color = BotanicalGreenContainer,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BotanicalGreen.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = String.format(
                                    Locale.ENGLISH,
                                    "Payable Amount: ₹ %s",
                                    if (numericAmount % 1.0 == 0.0) String.format(Locale.ENGLISH, "%.0f", numericAmount)
                                    else String.format(Locale.ENGLISH, "%.2f", numericAmount)
                                ),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = BotanicalGreen,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Fixed VPA Info Row with Copy button
                    Surface(
                        color = Color(0xFFF6F1F2),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboardManager.setText(AnnotatedString(QrCodeGenerator.DEFAULT_VPA))
                                Toast.makeText(context, "Copied UPI ID: ${QrCodeGenerator.DEFAULT_VPA}", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.QrCode2,
                                contentDescription = null,
                                tint = MaroonPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "UPI ID: ${QrCodeGenerator.DEFAULT_VPA}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp,
                                color = Color(0xFF1E191A)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy UPI ID",
                                tint = MaroonPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Payee: ${QrCodeGenerator.DEFAULT_PAYEE_NAME} • Note: ${QrCodeGenerator.DEFAULT_TRANSACTION_NOTE}",
                        fontSize = 11.5.sp,
                        color = Color(0xFF5C4346),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 3. Action Buttons Row: Full-Screen Popup & Share QR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val bmp = qrBitmap ?: return@OutlinedButton
                        isSharing = true
                        coroutineScope.launch {
                            try {
                                val shareIntent = QrCodeGenerator.saveAndGetShareableQrCard(
                                    context = context,
                                    qrBitmap = bmp,
                                    amount = numericAmount
                                )
                                context.startActivity(Intent.createChooser(shareIntent, "Share UPI Payment QR"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Unable to share QR: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSharing = false
                            }
                        }
                    },
                    enabled = !isSharing && qrBitmap != null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("share_qr_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share QR", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                }

                Button(
                    onClick = { showFullScreenQrModal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("fullscreen_qr_button")
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Full-Screen QR", fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                }
            }

            // Encoded UPI URI Verification Card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Encoded UPI String Scheme:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = upiUri,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaroonPrimaryDark,
                        modifier = Modifier.testTag("encoded_upi_uri_text")
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Full-Screen QR Display Popup / Modal
    if (showFullScreenQrModal) {
        FullScreenUpiQrDialog(
            initialAmount = numericAmount,
            onDismiss = { showFullScreenQrModal = false }
        )
    }
}

/**
 * Clean, High-Contrast Full-Screen UPI QR Modal for customer scanning (Google Pay, PhonePe, Paytm).
 * Includes custom amount adjustment, "Share QR", and "Done" buttons.
 */
@Composable
fun FullScreenUpiQrDialog(
    initialAmount: Double?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var amountText by remember(initialAmount) {
        mutableStateOf(
            if (initialAmount != null && initialAmount > 0.0) {
                if (initialAmount % 1.0 == 0.0) initialAmount.toInt().toString()
                else String.format(Locale.ENGLISH, "%.2f", initialAmount)
            } else {
                ""
            }
        )
    }

    val amountVal = remember(amountText) {
        amountText.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    val upiUri = remember(amountVal) {
        QrCodeGenerator.buildUpiUri(
            amount = amountVal,
            vpa = QrCodeGenerator.DEFAULT_VPA,
            payeeName = QrCodeGenerator.DEFAULT_PAYEE_NAME,
            transactionNote = QrCodeGenerator.DEFAULT_TRANSACTION_NOTE
        )
    }

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(upiUri) {
        qrBitmap = QrCodeGenerator.generateQrBitmap(upiUri, sizePx = 800)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("fullscreen_upi_qr_modal"),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaroonContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = MaroonPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "विशाल नर्सरी (Vishal Nursery)",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = MaroonPrimary
                            )
                            Text(
                                text = "Mob: 7631873743 • सासाराम (रोहतास)",
                                fontSize = 12.sp,
                                color = Color(0xFF4A3B3E)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("fullscreen_qr_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black)
                    }
                }

                // Center High-Contrast QR Area
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = MaroonPrimary,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "SCAN & PAY WITH ANY UPI APP",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Google Pay • PhonePe • Paytm • BHIM",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E2627)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Large High-Contrast QR Frame
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(3.dp, Color.Black),
                        modifier = Modifier.size(290.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = qrBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Full-Screen UPI QR Code",
                                    filterQuality = FilterQuality.None,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(color = MaroonPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Editable / Displayed Amount in Fullscreen Modal
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { v ->
                            if (v.all { it.isDigit() || it == '.' }) {
                                amountText = v
                            }
                        },
                        label = { Text("Amount (राशि ₹)") },
                        placeholder = { Text("Any Amount") },
                        prefix = {
                            Text(
                                text = "₹ ",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                color = MaroonPrimary
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            color = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(240.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "UPI ID: ${QrCodeGenerator.DEFAULT_VPA}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = Color.Black
                    )
                    Text(
                        text = "Payee: ${QrCodeGenerator.DEFAULT_PAYEE_NAME}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4A3B3E)
                    )
                }

                // Bottom Action Bar: Share QR & Done
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val bmp = qrBitmap ?: return@OutlinedButton
                            coroutineScope.launch {
                                try {
                                    val shareIntent = QrCodeGenerator.saveAndGetShareableQrCard(
                                        context = context,
                                        qrBitmap = bmp,
                                        amount = amountVal
                                    )
                                    context.startActivity(Intent.createChooser(shareIntent, "Share UPI Payment QR"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Unable to share QR: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, MaroonPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("modal_share_qr_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = MaroonPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Share QR",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.5.sp,
                            color = MaroonPrimary
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("modal_done_qr_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Done",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.5.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
