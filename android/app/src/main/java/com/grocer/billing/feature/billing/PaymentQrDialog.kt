package com.grocer.billing.feature.billing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.grocer.billing.core.data.model.UpiQrCode
import com.grocer.billing.ui.theme.GreenDark
import com.grocer.billing.ui.theme.GreenLight
import com.grocer.billing.ui.theme.GreenPrimary
import com.grocer.billing.ui.theme.TextPrimary
import com.grocer.billing.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun PaymentQrDialog(
    shopName: String,
    totalAmount: Double,
    currencySymbol: String = "₹",
    qrCodes: List<UpiQrCode>,
    defaultUpiId: String? = null,
    onDismiss: () -> Unit,
    onPaymentConfirmed: () -> Unit
) {
    val context = LocalContext.current
    val effectiveQrList = remember(qrCodes, defaultUpiId) {
        if (qrCodes.isNotEmpty()) {
            qrCodes
        } else if (!defaultUpiId.isNullOrBlank()) {
            listOf(
                UpiQrCode(
                    id = "default",
                    label = "Store UPI QR",
                    upiId = defaultUpiId,
                    isDefault = true
                )
            )
        } else {
            emptyList()
        }
    }

    var selectedIndex by remember {
        val defIdx = effectiveQrList.indexOfFirst { it.isDefault }
        mutableStateOf(if (defIdx >= 0) defIdx else 0)
    }

    val activeQr = effectiveQrList.getOrNull(selectedIndex)

    // Generate or load QR bitmap
    val qrBitmapState = produceState<Bitmap?>(initialValue = null, key1 = activeQr, key2 = totalAmount) {
        value = withContext(Dispatchers.IO) {
            if (activeQr != null && !activeQr.imagePath.isNullOrBlank()) {
                val file = File(activeQr.imagePath)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else null
            } else if (activeQr != null && activeQr.upiId.isNotBlank()) {
                val upiPayload = "upi://pay?pa=${activeQr.upiId}&pn=${shopName.replace(" ", "%20")}&am=${String.format(Locale.US, "%.2f", totalAmount)}&cu=INR"
                generateQrBitmap(upiPayload, 400)
            } else if (!defaultUpiId.isNullOrBlank()) {
                val upiPayload = "upi://pay?pa=$defaultUpiId&pn=${shopName.replace(" ", "%20")}&am=${String.format(Locale.US, "%.2f", totalAmount)}&cu=INR"
                generateQrBitmap(upiPayload, 400)
            } else {
                null
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Scan & Pay $currencySymbol%.2f".format(totalAmount),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = GreenDark
                    )
                    Text(
                        text = shopName,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                // If multiple QR codes exist, show selector chips
                if (effectiveQrList.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        effectiveQrList.forEachIndexed { index, qr ->
                            FilterChip(
                                selected = selectedIndex == index,
                                onClick = { selectedIndex = index },
                                label = { Text(qr.label, fontSize = 11.sp, fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // QR Code Image / Generated Box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF8FAF9),
                    modifier = Modifier
                        .size(240.dp)
                        .border(1.5.dp, GreenPrimary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(10.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmapState.value != null) {
                            Image(
                                bitmap = qrBitmapState.value!!.asImageBitmap(),
                                contentDescription = "Payment QR Code",
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = GreenPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (activeQr?.upiId.isNullOrBlank() && defaultUpiId.isNullOrBlank())
                                        "No UPI ID or QR uploaded yet.\nSet up in Account Dashboard."
                                    else
                                        "Generating QR Code...",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // UPI Details Card
                val upiDisplay = activeQr?.upiId?.ifBlank { null } ?: defaultUpiId ?: "store@upi"
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GreenLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("UPI ID", fontSize = 10.sp, color = TextSecondary)
                            Text(upiDisplay, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GreenDark)
                        }
                        Text(
                            text = "Instant 0% Fee",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GreenPrimary
                        )
                    }
                }

                Text(
                    text = "Customer can scan with Google Pay, PhonePe, Paytm, or BHIM.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close")
                    }

                    Button(
                        onClick = onPaymentConfirmed,
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Payment Received", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun generateQrBitmap(content: String, size: Int = 400): Bitmap? {
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
