package com.grocer.billing.feature.account

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.grocer.billing.core.data.model.UpiQrCode
import com.grocer.billing.core.data.repository.AuthRepository
import com.grocer.billing.core.security.BiometricAuthManager
import com.grocer.billing.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDashboardScreen(
    authRepository: AuthRepository,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeShop by authRepository.observeActiveShop().collectAsState(initial = null)

    var qrCodes by remember { mutableStateOf<List<UpiQrCode>>(emptyList()) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAddQrDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var qrToDelete by remember { mutableStateOf<UpiQrCode?>(null) }
    var previewQrCode by remember { mutableStateOf<UpiQrCode?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var isBiometricEnabled by remember { mutableStateOf(authRepository.isBiometricEnabled()) }
    val isBiometricHardwareAvailable = remember { BiometricAuthManager.isBiometricAvailable(context) }

    fun refreshQrCodes() {
        coroutineScope.launch {
            qrCodes = authRepository.getQrCodes()
        }
    }

    LaunchedEffect(Unit) {
        refreshQrCodes()
    }

    // Image Picker for QR Code File Upload
    var pendingQrImageUri by remember { mutableStateOf<Uri?>(null) }
    var newQrLabel by remember { mutableStateOf("Payment QR") }
    var newQrUpiId by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingQrImageUri = uri
            showAddQrDialog = true
        }
    }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Account Dashboard",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 20.sp
                            )
                            Text(
                                text = "Store profile & UPI QR management",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showLogoutConfirmDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary.copy(alpha = 0.92f))
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Store Profile Card
                val shop = activeShop
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = Color.White.copy(alpha = 0.92f),
                    elevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(GreenLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = GreenPrimary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = shop?.name ?: "Store Name",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Shopkeeper: ${shop?.ownerName ?: "Shopkeeper"}",
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = { showEditProfileDialog = true }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = GreenPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.Black.copy(alpha = 0.06f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Detail items
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProfileInfoRow(
                            icon = Icons.Default.Phone,
                            title = "Phone Number",
                            value = shop?.phone?.takeIf { !it.startsWith("g_") } ?: "Not Set"
                        )
                        ProfileInfoRow(
                            icon = Icons.Default.LocationOn,
                            title = "Store Address",
                            value = shop?.address?.takeIf { it.isNotBlank() } ?: "Not Set"
                        )
                        ProfileInfoRow(
                            icon = Icons.Default.AccountBalanceWallet,
                            title = "Primary UPI ID",
                            value = shop?.upiId ?: "Not Set"
                        )
                        if (!shop?.email.isNullOrBlank()) {
                            ProfileInfoRow(
                                icon = Icons.Default.Email,
                                title = "Google / Email Account",
                                value = shop?.email ?: ""
                            )
                        }
                    }
                }

                // Neon Database Status Card
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = Color(0xFFF0FDF4).copy(alpha = 0.90f),
                    elevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(GreenPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = GreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Neon Online Database Connected",
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "All store profile details, items, and billing sync to cloud",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Security & Biometric Card
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = Color.White.copy(alpha = 0.90f),
                    elevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEEF2FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFF4338CA))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Fingerprint / Biometric Unlock",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (isBiometricHardwareAvailable) "Fast one-touch counter unlock" else "Sensor unavailable on this device",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Switch(
                            checked = isBiometricEnabled && isBiometricHardwareAvailable,
                            onCheckedChange = { enabled ->
                                isBiometricEnabled = enabled
                                authRepository.setBiometricEnabled(enabled)
                            },
                            enabled = isBiometricHardwareAvailable,
                            colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary, checkedTrackColor = GreenLight)
                        )
                    }
                }

                // UPI QR Code Section (Up to 6 QR files upload & delete)
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = Color.White.copy(alpha = 0.90f),
                    elevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "UPI QR Codes for Payment",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "${qrCodes.size} of 6 QR codes added",
                                fontSize = 12.sp,
                                color = if (qrCodes.size >= 6) AlertRed else TextSecondary
                            )
                        }

                        Button(
                            onClick = {
                                if (qrCodes.size >= 6) {
                                    statusMessage = "Maximum 6 QR codes reached. Delete one first."
                                } else {
                                    newQrLabel = "QR Code #${qrCodes.size + 1}"
                                    newQrUpiId = shop?.upiId ?: ""
                                    pendingQrImageUri = null
                                    showAddQrDialog = true
                                }
                            },
                            enabled = qrCodes.size < 6,
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Upload QR", fontSize = 13.sp)
                        }
                    }

                    if (statusMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = statusMessage ?: "",
                            fontSize = 12.sp,
                            color = AlertRed
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (qrCodes.isEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF9FAFB),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(42.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No UPI QR Codes Uploaded",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Upload up to 6 payment QR codes (GPay, PhonePe, Paytm, BharatPe) or generate from your UPI ID.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            qrCodes.forEach { qr ->
                                QrCodeItemRow(
                                    qr = qr,
                                    onView = { previewQrCode = qr },
                                    onSetDefault = {
                                        coroutineScope.launch {
                                            authRepository.setDefaultQrCode(qr.id)
                                            refreshQrCodes()
                                        }
                                    },
                                    onDelete = { qrToDelete = qr }
                                )
                            }
                        }
                    }
                }

                // Logout Button
                OutlinedButton(
                    onClick = { showLogoutConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AlertRed.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = AlertRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Logout From Store",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AlertRed
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Dialog: Edit Store Profile
    if (showEditProfileDialog) {
        val currentShop = activeShop
        var editName by remember { mutableStateOf(currentShop?.name ?: "") }
        var editOwner by remember { mutableStateOf(currentShop?.ownerName ?: "") }
        var editPhone by remember { mutableStateOf(currentShop?.phone?.takeIf { !it.startsWith("g_") } ?: "") }
        var editAddress by remember { mutableStateOf(currentShop?.address ?: "") }
        var editUpi by remember { mutableStateOf(currentShop?.upiId ?: "") }
        var editError by remember { mutableStateOf<String?>(null) }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Store Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editOwner,
                        onValueChange = { editOwner = it },
                        label = { Text("Shopkeeper Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Store Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { if (it.length <= 10) editPhone = it },
                        label = { Text("Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editAddress,
                        onValueChange = { editAddress = it },
                        label = { Text("Store Address") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editUpi,
                        onValueChange = { editUpi = it },
                        label = { Text("Primary UPI ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (editError != null) {
                        Text(editError ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editOwner.isBlank() || editName.isBlank() || editPhone.length < 10) {
                            editError = "Please enter valid shopkeeper, store, and 10-digit phone."
                            return@Button
                        }
                        isSaving = true
                        coroutineScope.launch {
                            try {
                                authRepository.updateShopProfileDetails(
                                    shopName = editName,
                                    ownerName = editOwner,
                                    phone = editPhone,
                                    address = editAddress,
                                    upiId = editUpi
                                )
                                showEditProfileDialog = false
                            } catch (e: Exception) {
                                editError = e.localizedMessage
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    enabled = !isSaving
                ) {
                    Text(if (isSaving) "Saving..." else "Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Add / Upload UPI QR Code
    if (showAddQrDialog) {
        var qrLabelInput by remember { mutableStateOf(newQrLabel) }
        var qrUpiInput by remember { mutableStateOf(newQrUpiId.ifBlank { activeShop?.upiId ?: "" }) }
        var isSavingQr by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddQrDialog = false },
            title = { Text("Add UPI QR Code", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Add up to 6 payment QR codes. You can select an image from gallery or generate one from UPI ID.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = qrLabelInput,
                        onValueChange = { qrLabelInput = it },
                        label = { Text("QR Name / Label (e.g. Google Pay, Paytm)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = qrUpiInput,
                        onValueChange = { qrUpiInput = it },
                        label = { Text("UPI ID") },
                        placeholder = { Text("store@upi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (pendingQrImageUri != null) "Change File" else "Pick Image", fontSize = 12.sp)
                        }
                    }

                    if (pendingQrImageUri != null) {
                        Text(
                            text = "✓ Image selected from gallery",
                            fontSize = 12.sp,
                            color = GreenPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (qrLabelInput.isBlank()) qrLabelInput = "Payment QR"
                        isSavingQr = true
                        coroutineScope.launch {
                            val savedPath = pendingQrImageUri?.let { uri ->
                                copyUriToInternalStorage(context, uri)
                            }
                            val newQr = UpiQrCode(
                                label = qrLabelInput.trim(),
                                upiId = qrUpiInput.trim(),
                                imagePath = savedPath,
                                isDefault = qrCodes.isEmpty()
                            )
                            authRepository.addQrCode(newQr)
                            refreshQrCodes()
                            isSavingQr = false
                            showAddQrDialog = false
                            pendingQrImageUri = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    enabled = !isSavingQr
                ) {
                    Text(if (isSavingQr) "Saving..." else "Add QR Code")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAddQrDialog = false
                    pendingQrImageUri = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Confirm Delete QR Code
    if (qrToDelete != null) {
        AlertDialog(
            onDismissRequest = { qrToDelete = null },
            title = { Text("Delete QR Code?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${qrToDelete?.label}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val id = qrToDelete!!.id
                        qrToDelete = null
                        coroutineScope.launch {
                            authRepository.deleteQrCode(id)
                            refreshQrCodes()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { qrToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Preview QR Code
    if (previewQrCode != null) {
        val qr = previewQrCode!!
        AlertDialog(
            onDismissRequest = { previewQrCode = null },
            title = { Text(qr.label, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    QrImageView(qr = qr, size = 220)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(qr.upiId, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GreenPrimary)
                    if (qr.isDefault) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("(Default Counter Payment QR)", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { previewQrCode = null },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("Close")
                }
            }
        )
    }

    // Dialog: Logout Confirmation
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text("Logout From Store?", fontWeight = FontWeight.Bold) },
            text = {
                Text("You will need to login again to open the billing counter. Your local stock and sales data remain safely preserved.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        authRepository.logout()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Yes, Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, fontSize = 11.sp, color = TextSecondary)
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
    }
}

@Composable
fun QrCodeItemRow(
    qr: UpiQrCode,
    onView: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (qr.isDefault) Color(0xFFF0FDF4) else Color(0xFFFAFAFA),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (qr.isDefault) GreenPrimary.copy(alpha = 0.5f) else Color(0xFFE5E7EB)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onView() }
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    QrImageView(qr = qr, size = 40)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = qr.label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        if (qr.isDefault) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = GreenLight
                            ) {
                                Text(
                                    text = "DEFAULT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GreenPrimary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = qr.upiId,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!qr.isDefault) {
                    IconButton(onClick = onSetDefault) {
                        Icon(Icons.Default.StarOutline, contentDescription = "Set Default", tint = TextSecondary)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = AlertRed)
                }
            }
        }
    }
}

@Composable
fun QrImageView(qr: UpiQrCode, size: Int = 100) {
    val context = LocalContext.current
    val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = qr) {
        value = withContext(Dispatchers.IO) {
            if (!qr.imagePath.isNullOrBlank()) {
                val file = File(qr.imagePath)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else null
            } else {
                // Generate QR bitmap from UPI payment payload
                val upiPayload = "upi://pay?pa=${qr.upiId}&pn=KiranaStore&cu=INR"
                generateQrBitmap(upiPayload, 300)
            }
        }
    }

    if (bitmapState.value != null) {
        Image(
            bitmap = bitmapState.value!!.asImageBitmap(),
            contentDescription = qr.label,
            modifier = Modifier.size(size.dp)
        )
    } else {
        Icon(
            imageVector = Icons.Default.QrCode2,
            contentDescription = null,
            modifier = Modifier.size(size.dp),
            tint = GreenPrimary
        )
    }
}

private fun generateQrBitmap(content: String, size: Int = 300): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}

private suspend fun copyUriToInternalStorage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val dir = File(context.filesDir, "qr_codes")
        if (!dir.exists()) dir.mkdirs()
        val targetFile = File(dir, "qr_${UUID.randomUUID()}.png")
        val outputStream = FileOutputStream(targetFile)
        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        targetFile.absolutePath
    } catch (_: Exception) {
        null
    }
}
