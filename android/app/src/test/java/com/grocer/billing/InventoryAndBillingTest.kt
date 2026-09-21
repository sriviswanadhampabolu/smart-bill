package com.grocer.billing

import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.core.data.repository.CartLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryAndBillingTest {

    @Test
    fun testPieceCartLine_calculatesTotalCorrectly() {
        val biscuit = ItemEntity(
            shopId = "shop-1",
            name = "Parle-G 100g",
            category = "Snacks",
            unitType = "piece",
            price = 10.0,
            stockQty = 50.0,
            lowStockThreshold = 10.0
        )

        val cartLine = CartLine(item = biscuit, qty = 3.0)
        assertEquals(30.0, cartLine.lineTotal, 0.001)
    }

    @Test
    fun testWeightCartLine_calculatesFractionalWeightCorrectly() {
        val rice = ItemEntity(
            shopId = "shop-1",
            name = "Sona Masoori Rice",
            category = "Staples",
            unitType = "weight",
            price = 60.0,
            stockQty = 25.0,
            lowStockThreshold = 5.0
        )

        // 1.25 kg @ 60 = 75.00
        val cartLine = CartLine(item = rice, qty = 1.25)
        assertEquals(75.0, cartLine.lineTotal, 0.001)

        // 250 grams = 0.25 kg @ 60 = 15.00
        val cartLineQuarterKg = CartLine(item = rice, qty = 0.25)
        assertEquals(15.0, cartLineQuarterKg.lineTotal, 0.001)
    }

    @Test
    fun testLowStockThreshold_evaluatesAccurately() {
        val lowStockItem = ItemEntity(
            shopId = "shop-1",
            name = "Tata Salt",
            category = "Staples",
            unitType = "piece",
            price = 28.0,
            stockQty = 4.0,
            lowStockThreshold = 5.0
        )
        assertTrue(lowStockItem.stockQty <= lowStockItem.lowStockThreshold)

        val healthyStockItem = ItemEntity(
            shopId = "shop-1",
            name = "Tata Salt",
            category = "Staples",
            unitType = "piece",
            price = 28.0,
            stockQty = 12.0,
            lowStockThreshold = 5.0
        )
        assertFalse(healthyStockItem.stockQty <= healthyStockItem.lowStockThreshold)
    }

    @Test
    fun testCartMultiItemRunningSubtotal() {
        val soap = ItemEntity(shopId = "s1", name = "Soap", category = "Care", unitType = "piece", price = 35.0)
        val sugar = ItemEntity(shopId = "s1", name = "Sugar", category = "Staples", unitType = "weight", price = 44.0)

        val cart = listOf(
            CartLine(item = soap, qty = 2.0),   // 70.00
            CartLine(item = sugar, qty = 1.5)   // 66.00
        )

        val subtotal = cart.sumOf { it.lineTotal }
        assertEquals(136.0, subtotal, 0.001)
    }
}
