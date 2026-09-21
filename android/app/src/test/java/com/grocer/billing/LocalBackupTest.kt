package com.grocer.billing

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.grocer.billing.core.data.backup.EmbeddingBackupDto
import com.grocer.billing.core.data.backup.KiranaBackupDto
import com.grocer.billing.core.data.backup.SafeBase64
import com.grocer.billing.core.data.local.entities.BillEntity
import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.core.data.local.entities.ShopEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBackupTest {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    @Test
    fun testSafeBase64_roundTripPreservesBytes() {
        val originalBytes = byteArrayOf(10, 20, 30, -50, -100, 127, 0, 42)
        val encoded = SafeBase64.encode(originalBytes)
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())

        val decoded = SafeBase64.decode(encoded)
        assertArrayEquals(originalBytes, decoded)
    }

    @Test
    fun testKiranaBackupDto_serializationAndDeserialization() {
        val testShop = ShopEntity(
            id = "shop_test_123",
            name = "Balaji Provisions",
            phone = "9876543210",
            ownerName = "Ramesh Kumar",
            pinHash = "pin_hash_1234",
            upiId = "balaji@upi"
        )

        val testItems = listOf(
            ItemEntity(
                id = "item_1",
                shopId = testShop.id,
                name = "Aashirvaad Atta 5kg",
                category = "Staples",
                unitType = "piece",
                price = 245.0,
                stockQty = 15.0,
                barcode = "8901725131018"
            ),
            ItemEntity(
                id = "item_2",
                shopId = testShop.id,
                name = "Fortune Oil 1L",
                category = "Staples",
                unitType = "piece",
                price = 145.0,
                stockQty = 25.0,
                barcode = "8906007281014"
            )
        )

        val testBills = listOf(
            BillEntity(
                id = "bill_1",
                shopId = testShop.id,
                billNumber = "BILL-001",
                total = 390.0,
                subtotal = 390.0,
                paymentMethod = "cash"
            )
        )

        val vectorBytes = FloatArray(512) { 0.5f }.let { floats ->
            java.nio.ByteBuffer.allocate(floats.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
                asFloatBuffer().put(floats)
            }.array()
        }

        val testEmbeddings = listOf(
            EmbeddingBackupDto(
                id = "emb_1",
                itemId = "item_1",
                vectorBase64 = SafeBase64.encode(vectorBytes),
                source = "setup",
                qualityScore = 1.0f,
                createdAt = System.currentTimeMillis()
            )
        )

        val backupDto = KiranaBackupDto(
            version = 1,
            backupTime = System.currentTimeMillis(),
            backupDateFormatted = "20 Sep 2026, 03:30 PM",
            shop = testShop,
            items = testItems,
            embeddings = testEmbeddings,
            bills = testBills
        )

        // Serialize to JSON
        val json = gson.toJson(backupDto)
        assertTrue(json.contains("Balaji Provisions"))
        assertTrue(json.contains("Aashirvaad Atta 5kg"))
        assertTrue(json.contains("BILL-001"))

        // Deserialize back from JSON
        val restored = gson.fromJson(json, KiranaBackupDto::class.java)
        assertNotNull(restored)
        assertEquals("Balaji Provisions", restored.shop?.name)
        assertEquals("9876543210", restored.shop?.phone)
        assertEquals(2, restored.items.size)
        assertEquals(1, restored.bills.size)
        assertEquals(1, restored.embeddings.size)

        // Verify embedding bytes restored perfectly
        val restoredVectorBytes = SafeBase64.decode(restored.embeddings[0].vectorBase64)
        assertArrayEquals(vectorBytes, restoredVectorBytes)
    }
}
