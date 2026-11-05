package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.model.BusinessProfile
import com.example.ui.CashMemoViewModel
import com.example.ui.theme.MaroonPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessProfileScreen(
    viewModel: CashMemoViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val currentProfile by viewModel.businessProfile.collectAsState()

    var shopNameHindi by remember(currentProfile) { mutableStateOf(currentProfile.shopNameHindi) }
    var shopNameEnglish by remember(currentProfile) { mutableStateOf(currentProfile.shopNameEnglish) }
    var signerName by remember(currentProfile) { mutableStateOf(currentProfile.signerName) }
    var tagline by remember(currentProfile) { mutableStateOf(currentProfile.tagline) }
    var phone by remember(currentProfile) { mutableStateOf(currentProfile.phone) }
    var addressLine1 by remember(currentProfile) { mutableStateOf(currentProfile.addressLine1) }
    var addressLine2 by remember(currentProfile) { mutableStateOf(currentProfile.addressLine2) }
    var nextMemoNumber by remember(currentProfile) { mutableStateOf(currentProfile.nextMemoNumber.toString()) }
    var selectedProvider by remember(currentProfile) { mutableStateOf(currentProfile.selectedAiProvider) }
    var selectedModel by remember(currentProfile) { mutableStateOf(currentProfile.selectedAiModel) }
    var customApiKey by remember(currentProfile) { mutableStateOf(currentProfile.customApiKey) }
    var showApiKey by remember { mutableStateOf(false) }

    var showModelDropdown by remember { mutableStateOf(false) }
    var showProviderDropdown by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportPasteDialog by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }
    var exportDialogTitle by remember { mutableStateOf("Export Rates (JSON)") }
    var exportFileNamePrefix by remember { mutableStateOf("VishalNursery_Rates") }
    var exportItemsSummary by remember { mutableStateOf("") }
    var importJsonInput by remember { mutableStateOf("") }

    // File Picker for importing rate list or backup JSON
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val content = withContext(Dispatchers.IO) {
                        val stream: InputStream? = context.contentResolver.openInputStream(uri)
                        stream?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                    if (content.isNotBlank()) {
                        val (rateCount, memoCount) = viewModel.importFullBackupJson(content)
                        val msg = buildString {
                            if (rateCount > 0) append("Imported $rateCount plants! ")
                            if (memoCount > 0) append("Imported $memoCount memos!")
                        }
                        Toast.makeText(context, if (msg.isNotBlank()) msg else "Imported successfully!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error importing file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Provider models mapping
    val providerModels = remember(selectedProvider) {
        when (selectedProvider.lowercase()) {
            "openai" -> listOf("gpt-4o-mini (Free / Cheap)", "gpt-4o", "gpt-3.5-turbo")
            "claude" -> listOf("claude-3-5-haiku-20241022 (Fast / Free tier)", "claude-3-5-sonnet-20241022", "claude-3-haiku-20240307")
            else -> listOf(
                "gemini-2.5-flash (Recommended & Stable)",
                "gemini-2.0-flash",
                "gemini-2.0-flash-lite (Ultra Fast)",
                "gemini-1.5-flash",
                "gemini-1.5-pro (High Quality)"
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Business Profile & Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
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
                Button(
                    onClick = {
                        val num = nextMemoNumber.toIntOrNull() ?: 1001
                        val cleanModel = selectedModel.split(" ").first()
                        val updated = currentProfile.copy(
                            shopNameHindi = shopNameHindi,
                            shopNameEnglish = shopNameEnglish,
                            signerName = signerName.ifBlank { shopNameEnglish },
                            tagline = tagline,
                            phone = phone,
                            addressLine1 = addressLine1,
                            addressLine2 = addressLine2,
                            nextMemoNumber = num,
                            selectedAiProvider = selectedProvider,
                            selectedAiModel = cleanModel,
                            customApiKey = customApiKey
                        )
                        viewModel.updateBusinessProfile(updated)
                        Toast.makeText(context, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .height(48.dp)
                        .testTag("save_business_profile_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save All Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // Shop Details Section
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Shop & Header Information",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = shopNameHindi,
                        onValueChange = { shopNameHindi = it },
                        label = { Text("दुकान का नाम - हिंदी (Shop Name Hindi)") },
                        modifier = Modifier.fillMaxWidth().testTag("shop_name_hindi_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = shopNameEnglish,
                        onValueChange = { shopNameEnglish = it },
                        label = { Text("Shop Name English") },
                        modifier = Modifier.fillMaxWidth().testTag("shop_name_english_input"),
                        singleLine = true
                    )

                    // Authorized Signer Name for the digital signature stamp (Matching 1.jpg)
                    OutlinedTextField(
                        value = signerName,
                        onValueChange = { signerName = it },
                        label = { Text("अधिकृत हस्ताक्षरकर्ता (Authorized Signer Name)") },
                        placeholder = { Text("e.g. VISHAL NURSERY or BINOD KUMAR SINGH") },
                        supportingText = { Text("Printed on cash memos as 'Digitally signed by [Signer Name]'") },
                        modifier = Modifier.fillMaxWidth().testTag("signer_name_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("मोबाइल नंबर (Mobile Phone)") },
                        modifier = Modifier.fillMaxWidth().testTag("shop_phone_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )

                    OutlinedTextField(
                        value = addressLine1,
                        onValueChange = { addressLine1 = it },
                        label = { Text("पता पंक्ति 1 (Address Line 1)") },
                        modifier = Modifier.fillMaxWidth().testTag("address_line1_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = addressLine2,
                        onValueChange = { addressLine2 = it },
                        label = { Text("पता पंक्ति 2 (Address Line 2 / City)") },
                        modifier = Modifier.fillMaxWidth().testTag("address_line2_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = tagline,
                        onValueChange = { tagline = it },
                        label = { Text("विशेषता / विवरण टैगलाइन (Tagline / Description)") },
                        modifier = Modifier.fillMaxWidth().testTag("shop_tagline_input"),
                        maxLines = 3
                    )
                }
            }

            // Numbering & Sequence Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Cash Memo Numbering",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = nextMemoNumber,
                        onValueChange = { nextMemoNumber = it },
                        label = { Text("अगला मेमो नंबर (Next Memo Number)") },
                        modifier = Modifier.fillMaxWidth().testTag("next_memo_number_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text("Auto-increments with each generated cash memo (starts at 1001)") }
                    )
                }
            }

            // AI Provider & Model Selection Box (Requirement 2 & 3)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Model & Provider Selection",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Select any Free Tier or Standard AI Model for rough bill scanning. Supports Google Gemini, OpenAI, and Anthropic Claude.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Provider Picker Box
                    ExposedDropdownMenuBox(
                        expanded = showProviderDropdown,
                        onExpandedChange = { showProviderDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = when (selectedProvider.lowercase()) {
                                "openai" -> "OpenAI (GPT)"
                                "claude" -> "Anthropic Claude"
                                else -> "Google Gemini (Default)"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("AI Service Provider") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showProviderDropdown) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )

                        ExposedDropdownMenu(
                            expanded = showProviderDropdown,
                            onDismissRequest = { showProviderDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Google Gemini (Default)") },
                                onClick = {
                                    selectedProvider = "gemini"
                                    selectedModel = "gemini-2.5-flash"
                                    showProviderDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("OpenAI (ChatGPT / GPT-4o)") },
                                onClick = {
                                    selectedProvider = "openai"
                                    selectedModel = "gpt-4o-mini"
                                    showProviderDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Anthropic Claude") },
                                onClick = {
                                    selectedProvider = "claude"
                                    selectedModel = "claude-3-5-haiku-20241022"
                                    showProviderDropdown = false
                                }
                            )
                        }
                    }

                    // Model Selection Dropdown (Free Tier Models)
                    ExposedDropdownMenuBox(
                        expanded = showModelDropdown,
                        onExpandedChange = { showModelDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Choose Free / Standard AI Model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showModelDropdown) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )

                        ExposedDropdownMenu(
                            expanded = showModelDropdown,
                            onDismissRequest = { showModelDropdown = false }
                        ) {
                            providerModels.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        selectedModel = m.split(" ").first()
                                        showModelDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Status pill
                    val hasInjectedGeminiKey = BuildConfig.GEMINI_API_KEY.isNotBlank() && !BuildConfig.GEMINI_API_KEY.startsWith("MY_")
                    Surface(
                        color = if ((selectedProvider == "gemini" && hasInjectedGeminiKey) || customApiKey.isNotBlank()) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if ((selectedProvider == "gemini" && hasInjectedGeminiKey) || customApiKey.isNotBlank()) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if ((selectedProvider == "gemini" && hasInjectedGeminiKey) || customApiKey.isNotBlank()) Color(0xFF2E7D32) else Color(0xFFE65100),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedProvider == "gemini" && hasInjectedGeminiKey && customApiKey.isBlank()) {
                                    "Gemini Secret API Key is active ($selectedModel ready)."
                                } else if (customApiKey.isNotBlank()) {
                                    "Custom API Key configured for $selectedProvider ($selectedModel)."
                                } else {
                                    "Enter your ${selectedProvider.uppercase()} API key below to scan bills with this model."
                                },
                                fontSize = 12.sp,
                                color = if ((selectedProvider == "gemini" && hasInjectedGeminiKey) || customApiKey.isNotBlank()) Color(0xFF1B5E20) else Color(0xFFE65100)
                            )
                        }
                    }

                    // Custom API Key field
                    OutlinedTextField(
                        value = customApiKey,
                        onValueChange = { customApiKey = it },
                        label = { Text("Custom API Key (${selectedProvider.replaceFirstChar { it.uppercase() }})") },
                        placeholder = { Text(if (selectedProvider == "openai") "sk-..." else if (selectedProvider == "claude") "sk-ant-..." else "AIzaSy...") },
                        modifier = Modifier.fillMaxWidth().testTag("custom_api_key_input"),
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle visibility"
                                )
                            }
                        }
                    )
                }
            }

            // Export & Import Rate List Card (Requirement 8)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Data Backup & Restore (JSON)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Backup your plant catalog and invoices to a JSON file. Transfer to another phone or restore anytime.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Primary Export and Import buttons with CORRECT icons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Export Rates Button (Arrow UP - FileUpload)
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    val json = viewModel.exportRatesJson()
                                    val count = try { org.json.JSONArray(json).length() } catch (_: Exception) { 0 }
                                    exportDialogTitle = "Export Plant Rates ($count Plants)"
                                    exportFileNamePrefix = "VishalNursery_Rates"
                                    exportItemsSummary = "Ready to export $count plant rate entries from catalog:"
                                    exportedJsonText = json
                                    showExportDialog = true
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Export", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Rates", fontSize = 13.sp)
                        }

                        // Import Button (Arrow DOWN - FileDownload)
                        OutlinedButton(
                            onClick = {
                                showImportPasteDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Import", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import / Restore", fontSize = 13.sp)
                        }
                    }

                    // Secondary backup options: Full Backup and Memos Export
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val json = viewModel.exportAllMemosJson()
                                    val count = try { org.json.JSONArray(json).length() } catch (_: Exception) { 0 }
                                    exportDialogTitle = "Export Invoices ($count Bills)"
                                    exportFileNamePrefix = "VishalNursery_Invoices"
                                    exportItemsSummary = "Ready to export $count saved cash memos/invoices:"
                                    exportedJsonText = json
                                    showExportDialog = true
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export Bills", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val json = viewModel.exportFullBackupJson()
                                    exportDialogTitle = "Full Backup (Rates + Bills)"
                                    exportFileNamePrefix = "VishalNursery_FullBackup"
                                    exportItemsSummary = "Complete backup with all rates, invoices, and records:"
                                    exportedJsonText = json
                                    showExportDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Full Backup", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // --- EXPORT DIALOG WITH SHARE / COPY ---
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(exportDialogTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = exportItemsSummary.ifBlank { "Your data backup is ready. Share this file or copy the backup text:" },
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = exportedJsonText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    val dir = File(context.cacheDir, "backups").apply { mkdirs() }
                                    val f = File(dir, "${exportFileNamePrefix}_${System.currentTimeMillis()}.json")
                                    FileOutputStream(f).use { out ->
                                        out.write(exportedJsonText.toByteArray())
                                    }
                                    f
                                }
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share JSON Backup"))
                                showExportDialog = false
                            } catch (e: Exception) {
                                Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Backup File")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(exportedJsonText))
                        Toast.makeText(context, "Backup copied to clipboard!", Toast.LENGTH_SHORT).show()
                        showExportDialog = false
                    }
                ) {
                    Text("Copy JSON")
                }
            }
        )
    }

    // --- IMPORT DIALOG (FILE OR PASTE) ---
    if (showImportPasteDialog) {
        AlertDialog(
            onDismissRequest = { showImportPasteDialog = false },
            title = { Text("Import Backup (JSON)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Pick a backup JSON file from storage, or paste the JSON text below (supports plant rates or full backup):",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FilledTonalButton(
                        onClick = {
                            showImportPasteDialog = false
                            importFileLauncher.launch("*/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Backup File (.json)")
                    }

                    Text("Or paste JSON directly:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                    OutlinedTextField(
                        value = importJsonInput,
                        onValueChange = { importJsonInput = it },
                        placeholder = { Text("[{\"name\": \"गुलाब\", \"retailPrice\": 30, \"wholesalePrice\": 18}, ...]") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importJsonInput.isNotBlank()) {
                            coroutineScope.launch {
                                try {
                                    val (rateCount, memoCount) = viewModel.importFullBackupJson(importJsonInput)
                                    val msg = buildString {
                                        if (rateCount > 0) append("Restored $rateCount plants! ")
                                        if (memoCount > 0) append("Restored $memoCount bills!")
                                    }
                                    Toast.makeText(context, if (msg.isNotBlank()) msg else "Data restored successfully!", Toast.LENGTH_LONG).show()
                                    showImportPasteDialog = false
                                    importJsonInput = ""
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please paste JSON or pick a file", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary)
                ) {
                    Text("Restore Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportPasteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
