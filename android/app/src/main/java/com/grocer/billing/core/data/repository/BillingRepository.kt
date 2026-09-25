package com.grocer.billing.core.data.repository

import androidx.room.withTransaction
import com.grocer.billing.core.data.local.AppDatabase
import com.grocer.billing.core.data.local.dao.BillDao
import com.grocer.billing.core.data.local.dao.ItemDao
import com.grocer.billing.core.data.local.dao.StockLogDao
import com.grocer.billing.core.data.local.dao.SyncQueueDao
import com.grocer.billing.core.data.local.entities.BillEntity
import com.grocer.billing.core.data.local.entities.BillItemEntity
import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.core.data.local.entities.StockLogEntity
import com.grocer.billing.core.data.local.entities.SyncQueueEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CartLine(
    val item: ItemEntity,
    var qty: Double,
    val unitPriceSnapshot: Double = item.price
) {
    val lineTotal: Double
        get() = (qty * unitPriceSnapshot * 100).toLong() / 100.0
}

data class CompletedSale(
    val bill: BillEntity,
    val items: List<BillItemEntity>,
    val receiptText: String
)

class BillingRepository(
    private val database: AppDatabase,
    private val billDao: BillDao,
    private val itemDao: ItemDao,
    private val stockLogDao: StockLogDao,
    private val syncQueueDao: SyncQueueDao,
    private val localBackupManager: com.grocer.billing.core.data.backup.LocalBackupManager? = null,
    private var syncManager: com.grocer.billing.core.data.sync.SyncManager? = null,
    private val context: android.content.Context? = null
) {
    fun setSyncManager(manager: com.grocer.billing.core.data.sync.SyncManager) {
        this.syncManager = manager
    }
    fun observeBills(shopId: String): Flow<List<BillEntity>> = billDao.observeBills(shopId)

    fun observeTodaySales(shopId: String): Flow<Double> {
        val startOfDay = getStartOfDayEpoch()
        return billDao.observeTodaySalesTotal(shopId, startOfDay)
    }

    fun observeTodayBillsCount(shopId: String): Flow<Int> {
        val startOfDay = getStartOfDayEpoch()
        return billDao.observeTodayBillsCount(shopId, startOfDay)
    }

    fun observeMonthSales(shopId: String): Flow<Double> {
        val startOfMonth = getStartOfMonthEpoch()
        return billDao.observeMonthSalesTotal(shopId, startOfMonth)
    }

    fun observeMonthBillsCount(shopId: String): Flow<Int> {
        val startOfMonth = getStartOfMonthEpoch()
        return billDao.observeMonthBillsCount(shopId, startOfMonth)
    }

    fun observeMonthBills(shopId: String): Flow<List<BillEntity>> {
        val startOfMonth = getStartOfMonthEpoch()
        return billDao.observeMonthBills(shopId, startOfMonth)
    }

    suspend fun getBillItems(billId: String): List<BillItemEntity> = billDao.getBillItems(billId)

    suspend fun completeSale(
        shopId: String,
        shopName: String,
        cartLines: List<CartLine>,
        discount: Double = 0.0,
        tax: Double = 0.0,
        paymentMethod: String = "cash",
        currencySymbol: String = "₹"
    ): CompletedSale {
        require(cartLines.isNotEmpty()) { "Cannot complete a bill with no items" }

        val subtotal = cartLines.sumOf { it.lineTotal }
        val grandTotal = (subtotal - discount + tax).coerceAtLeast(0.0)
        val billId = UUID.randomUUID().toString()
        val billNumber = generateBillNumber(shopId)
        val now = System.currentTimeMillis()

        val billEntity = BillEntity(
            id = billId,
            shopId = shopId,
            billNumber = billNumber,
            subtotal = subtotal,
            discount = discount,
            tax = tax,
            total = grandTotal,
            paymentMethod = paymentMethod,
            createdAt = now,
            syncedAt = null
        )

        val billItemEntities = cartLines.map { line ->
            BillItemEntity(
                billId = billId,
                itemId = line.item.id,
                nameSnapshot = line.item.name,
                qty = line.qty,
                unitType = line.item.unitType,
                unitPriceSnapshot = line.unitPriceSnapshot,
                lineTotal = line.lineTotal
            )
        }

        val stockLogs = cartLines.map { line ->
            StockLogEntity(
                itemId = line.item.id,
                changeQty = -line.qty,
                reason = "sale",
                billId = billId,
                createdAt = now
            )
        }

        database.withTransaction {
            // 1. Insert bill
            billDao.insertBill(billEntity)

            // 2. Insert item snapshots
            billDao.insertBillItems(billItemEntities)

            // 3. Atomically decrement stock
            for (line in cartLines) {
                itemDao.adjustStock(line.item.id, -line.qty, now)
            }

            // 4. Record stock audit movement logs
            stockLogDao.insertLogs(stockLogs)

            // 5. Enqueue for background sync with full bill details and line items
            val itemsJson = billItemEntities.joinToString(separator = ",", prefix = "[", postfix = "]") { bi ->
                """{"item_id":"${bi.itemId}","name_snapshot":"${bi.nameSnapshot.replace("\"", "\\\"")}","qty":${bi.qty},"unit_type":"${bi.unitType}","unit_price_snapshot":${bi.unitPriceSnapshot},"line_total":${bi.lineTotal}}"""
            }
            val billPayload = """{"bill_number":"$billNumber","subtotal":$subtotal,"discount":$discount,"tax":$tax,"total":$grandTotal,"payment_method":"$paymentMethod","items":$itemsJson}"""

            syncQueueDao.enqueue(
                SyncQueueEntity(
                    entityType = "bill",
                    entityId = billId,
                    operation = "INSERT",
                    payloadJson = billPayload,
                    createdAt = now
                )
            )
        }

        // Keep offline backup in public storage updated
        localBackupManager?.triggerAutoBackup(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))

        // Trigger real-time cloud auto-sync
        syncManager?.triggerAutoSync(shopId)

        // Low-stock notification check for items sold
        context?.let { ctx ->
            for (line in cartLines) {
                val updatedItem = itemDao.getItemById(line.item.id)
                if (updatedItem != null && updatedItem.stockQty <= updatedItem.lowStockThreshold) {
                    com.grocer.billing.core.notification.StockNotificationManager.sendLowStockNotification(
                        context = ctx,
                        itemId = updatedItem.id,
                        itemName = updatedItem.name,
                        currentStock = updatedItem.stockQty,
                        threshold = updatedItem.lowStockThreshold,
                        unitType = updatedItem.unitType
                    )
                }
            }
        }

        val receiptText = buildReceiptText(
            shopName = shopName,
            billNumber = billNumber,
            dateMillis = now,
            lines = billItemEntities,
            subtotal = subtotal,
            discount = discount,
            tax = tax,
            total = grandTotal,
            paymentMethod = paymentMethod,
            currencySymbol = currencySymbol
        )

        return CompletedSale(
            bill = billEntity,
            items = billItemEntities,
            receiptText = receiptText
        )
    }

    private suspend fun generateBillNumber(shopId: String): String {
        val count = billDao.getBillCount(shopId) + 1
        val datePrefix = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        return "BILL-$datePrefix-%03d".format(count)
    }

    private fun getStartOfDayEpoch(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun getStartOfMonthEpoch(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun buildReceiptText(
        shopName: String,
        billNumber: String,
        dateMillis: Long,
        lines: List<BillItemEntity>,
        subtotal: Double,
        discount: Double,
        tax: Double,
        total: Double,
        paymentMethod: String,
        currencySymbol: String
    ): String {
        val dateStr = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(Date(dateMillis))
        val sb = StringBuilder()
        sb.appendLine("================================")
        sb.appendLine(shopName.uppercase(Locale.getDefault()).center(32))
        sb.appendLine("Bill: $billNumber")
        sb.appendLine("Date: $dateStr")
        sb.appendLine("Payment: ${paymentMethod.uppercase(Locale.getDefault())}")
        sb.appendLine("--------------------------------")
        sb.appendLine("Item            Qty   Rate Total")
        sb.appendLine("--------------------------------")
        for (item in lines) {
            val name = if (item.nameSnapshot.length > 14) item.nameSnapshot.take(13) + "…" else item.nameSnapshot.padEnd(14)
            val qtyStr = if (item.unitType == "weight") "%.2fkg".format(item.qty).padStart(6) else "%.0f pcs".format(item.qty).padStart(6)
            val rate = "%.0f".format(item.unitPriceSnapshot).padStart(4)
            val lineTotal = "%.2f".format(item.lineTotal).padStart(6)
            sb.appendLine("$name $qtyStr $rate $lineTotal")
        }
        sb.appendLine("--------------------------------")
        sb.appendLine("Subtotal:       $currencySymbol%.2f".format(subtotal).padStart(16))
        if (discount > 0) {
            sb.appendLine("Discount:      -$currencySymbol%.2f".format(discount).padStart(16))
        }
        if (tax > 0) {
            sb.appendLine("Tax:            $currencySymbol%.2f".format(tax).padStart(16))
        }
        sb.appendLine("GRAND TOTAL:    $currencySymbol%.2f".format(total).padStart(16))
        sb.appendLine("================================")
        sb.appendLine("Thank you! Visit again.".center(32))
        sb.appendLine("================================")
        return sb.toString()
    }

    private fun String.center(width: Int): String {
        if (this.length >= width) return this
        val left = (width - this.length) / 2
        val right = width - this.length - left
        return " ".repeat(left) + this + " ".repeat(right)
    }
}
