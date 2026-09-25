package com.grocer.billing.core.data.repository

import com.grocer.billing.core.data.local.AppDatabase
import com.grocer.billing.core.data.local.entities.ItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailySalesPoint(
    val dayLabel: String,
    val dateLabel: String,
    val amount: Double
)

data class TopItemSummary(
    val name: String,
    val totalQty: Double,
    val totalRevenue: Double,
    val unitType: String
)

data class ExecutiveReport(
    val todaySales: Double,
    val todayBillsCount: Int,
    val monthSales: Double,
    val monthBillsCount: Int,
    val weeklySales: Double,
    val totalStockValuation: Double,
    val outOfStockCount: Int,
    val lowStockCount: Int,
    val dailyTrend: List<DailySalesPoint>,
    val topSellingItems: List<TopItemSummary>,
    val outOfStockItems: List<ItemEntity>
)

class ReportsRepository(
    private val database: AppDatabase
) {
    suspend fun generateExecutiveReport(shopId: String): ExecutiveReport = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = calendar.timeInMillis

        // Month start
        val monthCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = monthCal.timeInMillis

        calendar.timeInMillis = startOfToday
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        val sevenDaysAgo = calendar.timeInMillis

        // 1. Fetch relevant bills
        val monthBills = database.billDao().getBillsInRange(shopId, startOfMonth, Long.MAX_VALUE)
        val todayBills = monthBills.filter { it.createdAt >= startOfToday }
        val weekBills = database.billDao().getBillsInRange(shopId, sevenDaysAgo, Long.MAX_VALUE)

        val todaySales = todayBills.sumOf { it.total }
        val todayCount = todayBills.size

        val monthSales = monthBills.sumOf { it.total }
        val monthCount = monthBills.size

        val weeklySales = weekBills.sumOf { it.total }

        // 2. Inventory Metrics
        val items = database.itemDao().searchItems(shopId, "")
        val stockValuation = items.sumOf { it.price * it.stockQty.coerceAtLeast(0.0) }
        val outOfStock = items.filter { it.stockQty <= 0 }
        val lowStock = items.filter { it.stockQty > 0 && it.stockQty <= it.lowStockThreshold }

        // 3. 7-Day Daily Points with actual sales
        val dailyFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
        val dailyPoints = mutableListOf<DailySalesPoint>()

        for (i in 0 until 7) {
            val dayCal = Calendar.getInstance().apply {
                timeInMillis = sevenDaysAgo
                add(Calendar.DAY_OF_YEAR, i)
            }
            val dayStart = dayCal.timeInMillis
            dayCal.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = dayCal.timeInMillis

            val dayTotal = weekBills
                .filter { it.createdAt in dayStart until dayEnd }
                .sumOf { it.total }

            dailyPoints.add(
                DailySalesPoint(
                    dayLabel = dailyFormat.format(Date(dayStart)),
                    dateLabel = dateFormat.format(Date(dayStart)),
                    amount = dayTotal
                )
            )
        }

        // Query actual top selling items from real customer bills
        val topItems = database.billDao().getTopSellingItems(shopId, limit = 5).map { tuple ->
            TopItemSummary(
                name = tuple.name,
                totalQty = tuple.totalQty,
                totalRevenue = tuple.totalRevenue,
                unitType = tuple.unitType
            )
        }

        ExecutiveReport(
            todaySales = todaySales,
            todayBillsCount = todayCount,
            monthSales = monthSales,
            monthBillsCount = monthCount,
            weeklySales = weeklySales,
            totalStockValuation = stockValuation,
            outOfStockCount = outOfStock.size,
            lowStockCount = lowStock.size,
            dailyTrend = dailyPoints,
            topSellingItems = topItems,
            outOfStockItems = outOfStock
        )
    }
}
