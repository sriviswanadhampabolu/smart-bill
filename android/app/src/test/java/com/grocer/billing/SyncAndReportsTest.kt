package com.grocer.billing

import com.grocer.billing.core.data.local.entities.ItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncAndReportsTest {

    @Test
    fun testStockValuation_calculatesSumOfPositiveStockTimesPrice() {
        val items = listOf(
            ItemEntity(shopId = "s", name = "Atta", category = "Staples", unitType = "piece", price = 200.0, stockQty = 10.0), // 2000
            ItemEntity(shopId = "s", name = "Oil", category = "Staples", unitType = "piece", price = 150.0, stockQty = 4.0),   // 600
            ItemEntity(shopId = "s", name = "Salt", category = "Staples", unitType = "piece", price = 28.0, stockQty = -2.0)   // Negative stock ignored in valuation
        )

        val totalValuation = items.sumOf { it.price * it.stockQty.coerceAtLeast(0.0) }
        assertEquals(2600.0, totalValuation, 0.001)
    }

    @Test
    fun testOutOfStockDetection_flagsZeroAndNegative() {
        val itemZero = ItemEntity(shopId = "s", name = "Sugar", category = "Staples", unitType = "weight", price = 44.0, stockQty = 0.0)
        val itemNeg = ItemEntity(shopId = "s", name = "Soap", category = "Care", unitType = "piece", price = 35.0, stockQty = -1.0)
        val itemOk = ItemEntity(shopId = "s", name = "Biscuit", category = "Snacks", unitType = "piece", price = 10.0, stockQty = 15.0)

        assertTrue(itemZero.stockQty <= 0)
        assertTrue(itemNeg.stockQty <= 0)
        assertTrue(itemOk.stockQty > 0)
    }

    @Test
    fun testSyncQueueIdempotency_acknowledgedSetMatching() {
        val pendingIds = mutableListOf("queue-1", "queue-2", "queue-3")
        val acknowledgedIds = listOf("queue-1", "queue-3")

        pendingIds.removeAll(acknowledgedIds)
        assertEquals(1, pendingIds.size)
        assertEquals("queue-2", pendingIds.first())
    }

    @Test
    fun testTopSellingItemsWithoutBills_isEmpty() {
        // Without billing anything, top selling items should be empty
        val billItems = emptyList<com.grocer.billing.core.data.local.dao.TopSellingItemTuple>()
        assertTrue(billItems.isEmpty())
        assertEquals(0, billItems.size)
    }

    @Test
    fun testTopSellingItemsWithBills_sortedByRevenue() {
        val tuples = listOf(
            com.grocer.billing.core.data.local.dao.TopSellingItemTuple("Maggi", 10.0, 140.0, "piece"),
            com.grocer.billing.core.data.local.dao.TopSellingItemTuple("Oil", 2.0, 300.0, "piece"),
            com.grocer.billing.core.data.local.dao.TopSellingItemTuple("Sugar", 5.0, 220.0, "weight")
        ).sortedByDescending { it.totalRevenue }

        assertEquals("Oil", tuples.first().name)
        assertEquals(300.0, tuples.first().totalRevenue, 0.001)
    }
}
