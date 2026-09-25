package com.grocer.billing.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.grocer.billing.core.data.local.entities.BillEntity
import com.grocer.billing.core.data.local.entities.BillItemEntity
import com.grocer.billing.core.data.local.entities.StockLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Query("SELECT * FROM bills WHERE shop_id = :shopId ORDER BY created_at DESC")
    fun observeBills(shopId: String): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE id = :billId LIMIT 1")
    suspend fun getBillById(billId: String): BillEntity?

    @Query("SELECT * FROM bill_items WHERE bill_id = :billId")
    suspend fun getBillItems(billId: String): List<BillItemEntity>

    @Query("SELECT * FROM bills WHERE shop_id = :shopId")
    suspend fun getAllBills(shopId: String): List<BillEntity>

    @Query("SELECT * FROM bill_items")
    suspend fun getAllBillItems(): List<BillItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBills(bills: List<BillEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreBillItems(items: List<BillItemEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBill(bill: BillEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBillItems(items: List<BillItemEntity>)

    @Query("SELECT COUNT(*) FROM bills WHERE shop_id = :shopId")
    suspend fun getBillCount(shopId: String): Int

    @Query("SELECT COALESCE(SUM(total), 0.0) FROM bills WHERE shop_id = :shopId AND created_at >= :startOfDayEpoch")
    fun observeTodaySalesTotal(shopId: String, startOfDayEpoch: Long): Flow<Double>

    @Query("SELECT COUNT(*) FROM bills WHERE shop_id = :shopId AND created_at >= :startOfDayEpoch")
    fun observeTodayBillsCount(shopId: String, startOfDayEpoch: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(total), 0.0) FROM bills WHERE shop_id = :shopId AND created_at >= :startOfMonthEpoch")
    fun observeMonthSalesTotal(shopId: String, startOfMonthEpoch: Long): Flow<Double>

    @Query("SELECT COUNT(*) FROM bills WHERE shop_id = :shopId AND created_at >= :startOfMonthEpoch")
    fun observeMonthBillsCount(shopId: String, startOfMonthEpoch: Long): Flow<Int>

    @Query("SELECT * FROM bills WHERE shop_id = :shopId AND created_at >= :startOfMonthEpoch ORDER BY created_at DESC")
    fun observeMonthBills(shopId: String, startOfMonthEpoch: Long): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE shop_id = :shopId AND created_at >= :startEpoch AND created_at < :endEpoch")
    suspend fun getBillsInRange(shopId: String, startEpoch: Long, endEpoch: Long): List<BillEntity>

    @Query("UPDATE bills SET synced_at = :syncedAt WHERE id = :billId")
    suspend fun markBillSynced(billId: String, syncedAt: Long = System.currentTimeMillis())

    @Query("""
        SELECT bi.name_snapshot AS name,
               SUM(bi.qty) AS totalQty,
               SUM(bi.line_total) AS totalRevenue,
               bi.unit_type AS unitType
        FROM bill_items bi
        INNER JOIN bills b ON bi.bill_id = b.id
        WHERE b.shop_id = :shopId
        GROUP BY bi.name_snapshot, bi.unit_type
        ORDER BY totalRevenue DESC
        LIMIT :limit
    """)
    suspend fun getTopSellingItems(shopId: String, limit: Int = 5): List<TopSellingItemTuple>
}

data class TopSellingItemTuple(
    val name: String,
    val totalQty: Double,
    val totalRevenue: Double,
    val unitType: String
)

