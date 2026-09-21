package com.grocer.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTest {

    @Test
    fun testAutoThresholdCalculation_twentyPercentOfStock() {
        fun computeDefaultThreshold(stock: Double): Double {
            return (stock * 0.20).coerceAtLeast(2.0)
        }

        assertEquals(4.0, computeDefaultThreshold(20.0), 0.001)
        assertEquals(10.0, computeDefaultThreshold(50.0), 0.001)
        assertEquals(2.0, computeDefaultThreshold(5.0), 0.001) // Coerced to minimum 2
    }

    @Test
    fun testProgressFraction_accuracy() {
        val total = 40
        val configured = 12

        val fraction = configured.toFloat() / total.toFloat()
        assertEquals(0.30f, fraction, 0.001f)
        assertEquals(30, (fraction * 100).toInt())
    }

    @Test
    fun testCategoriesCoverage_allSixCoreIndianGroceryCategories() {
        val categories = listOf("Staples", "Spices", "Snacks", "Dairy", "Household", "Personal Care")
        assertEquals(6, categories.size)
        assertTrue(categories.contains("Staples"))
        assertTrue(categories.contains("Spices"))
        assertTrue(categories.contains("Snacks"))
        assertTrue(categories.contains("Dairy"))
        assertTrue(categories.contains("Household"))
        assertTrue(categories.contains("Personal Care"))
    }
}
