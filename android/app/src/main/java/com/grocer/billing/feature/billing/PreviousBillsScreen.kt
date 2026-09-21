package com.grocer.billing.feature.billing

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.grocer.billing.core.data.local.entities.BillEntity
import com.grocer.billing.core.data.local.entities.BillItemEntity
import com.grocer.billing.core.data.repository.BillingRepository
import com.grocer.billing.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviousBillsScreen(
    shopId: String,
    shopName: String,
    currencySymbol: String = "₹",
    billingRepository: BillingRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val allBills by billingRepository.observeBills(shopId).collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var selectedPaymentFilter by remember { mutableStateOf("All") }

    var selectedBillForDetails by remember { mutableStateOf<BillEntity?>(null) }
    var billItemsForDetails by remember { mutableStateOf<List<BillItemEntity>>(emptyList()) }
    var isLoadingDetails by remember { mutableStateOf(false) }

    val filteredBills = remember(allBills, searchQuery, selectedPaymentFilter) {
        allBills.filter { bill ->
            val matchesPayment = (selectedPaymentFilter == "All" || bill.paymentMethod.equals(selectedPaymentFilter, ignoreCase = true))
            val matchesSearch = searchQuery.isBlank() ||
                    bill.billNumber.contains(searchQuery, ignoreCase = true) ||
                    "%.2f".format(bill.total).contains(searchQuery)
            matchesPayment && matchesSearch
        }
    }

    val totalRevenue = remember(allBills) { allBills.sumOf { it.total } }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Previous Bills", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 20.sp)
                            Text("${allBills.size} receipts generated", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = GreenPrimary.copy(alpha = 0.90f)
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Summary KPI Hero Card
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = Color.White.copy(alpha = 0.85f),
                    elevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TOTAL ALL-TIME SALES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("$currencySymbol%.2f".format(totalRevenue), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = GreenPrimary)
                        }
                        LiquidGlassBadge(tint = GreenPrimary) {
                            Text("${allBills.size} Total Bills", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GreenPrimary)
                        }
                    }
                }

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by bill number (e.g. BILL-...)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Payment Method Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "cash", "upi", "credit").forEach { method ->
                        val label = when (method) {
                            "cash" -> "Cash"
                            "upi" -> "UPI QR"
                            "credit" -> "Khata"
                            else -> "All Bills"
                        }
                        FilterChip(
                            selected = selectedPaymentFilter == method,
                            onClick = { selectedPaymentFilter = method },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                // Bills List
                if (filteredBills.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(52.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isBlank()) "No bills generated yet" else "No bills match '$searchQuery'",
                                color = TextSecondary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredBills, key = { it.id }) { bill ->
                            BillItemRow(
                                bill = bill,
                                currencySymbol = currencySymbol,
                                onClick = {
                                    selectedBillForDetails = bill
                                    isLoadingDetails = true
                                    coroutineScope.launch {
                                        billItemsForDetails = billingRepository.getBillItems(bill.id)
                                        isLoadingDetails = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Interactive Bill Details / Receipt Dialog
    if (selectedBillForDetails != null) {
        val bill = selectedBillForDetails!!
        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(bill.createdAt))

        Dialog(onDismissRequest = { selectedBillForDetails = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(shopName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TextPrimary)
                            Text(bill.billNumber, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GreenPrimary)
                            Text(dateStr, fontSize = 12.sp, color = TextSecondary)
                        }
                        IconButton(onClick = { selectedBillForDetails = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    HorizontalDivider()

                    // Payment Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Payment Method:", fontSize = 13.sp, color = TextSecondary)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (bill.paymentMethod.lowercase()) {
                                "upi" -> Color(0xFFE0F2FE)
                                "credit" -> Color(0xFFFFF7ED)
                                else -> GreenLight
                            }
                        ) {
                            Text(
                                text = bill.paymentMethod.uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = when (bill.paymentMethod.lowercase()) {
                                    "upi" -> Color(0xFF0369A1)
                                    "credit" -> OrangeAccent
                                    else -> GreenPrimary
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider()

                    // Items List
                    Text("Purchased Items (${billItemsForDetails.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    if (isLoadingDetails) {
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = GreenPrimary)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            billItemsForDetails.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.nameSnapshot, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            text = "${if (item.unitType == "weight") "%.2f kg".format(item.qty) else "${item.qty.toInt()} pcs"} @ $currencySymbol%.2f".format(item.unitPriceSnapshot),
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    Text(
                                        text = "$currencySymbol%.2f".format(item.lineTotal),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Bill Summary Totals
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal", fontSize = 13.sp, color = TextSecondary)
                            Text("$currencySymbol%.2f".format(bill.subtotal), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        if (bill.discount > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Discount", fontSize = 13.sp, color = TextSecondary)
                                Text("-$currencySymbol%.2f".format(bill.discount), fontSize = 13.sp, color = AlertRed)
                            }
                        }
                        if (bill.tax > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Tax", fontSize = 13.sp, color = TextSecondary)
                                Text("+$currencySymbol%.2f".format(bill.tax), fontSize = 13.sp)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Grand Total", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                            Text("$currencySymbol%.2f".format(bill.total), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = GreenPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Action buttons: Share Receipt & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val receiptText = billingRepository.buildReceiptText(
                                    shopName = shopName,
                                    billNumber = bill.billNumber,
                                    dateMillis = bill.createdAt,
                                    lines = billItemsForDetails,
                                    subtotal = bill.subtotal,
                                    discount = bill.discount,
                                    tax = bill.tax,
                                    total = bill.total,
                                    paymentMethod = bill.paymentMethod,
                                    currencySymbol = currencySymbol
                                )
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, receiptText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Receipt via"))
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }

                        Button(
                            onClick = { selectedBillForDetails = null },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BillItemRow(
    bill: BillEntity,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(bill.createdAt))
    val isSynced = bill.syncedAt != null

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = Color.White.copy(alpha = 0.85f),
        elevation = 2.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = bill.billNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (bill.paymentMethod.lowercase()) {
                            "upi" -> Color(0xFFE0F2FE)
                            "credit" -> Color(0xFFFFF7ED)
                            else -> GreenLight
                        }
                    ) {
                        Text(
                            text = bill.paymentMethod.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (bill.paymentMethod.lowercase()) {
                                "upi" -> Color(0xFF0369A1)
                                "credit" -> OrangeAccent
                                else -> GreenPrimary
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = dateStr,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text("•", fontSize = 12.sp, color = TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isSynced) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = if (isSynced) GreenPrimary else TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isSynced) "Synced" else "Local",
                            fontSize = 11.sp,
                            color = if (isSynced) GreenPrimary else TextSecondary
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$currencySymbol%.2f".format(bill.total),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = GreenPrimary
                )
                Text(
                    text = "View receipt ›",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
