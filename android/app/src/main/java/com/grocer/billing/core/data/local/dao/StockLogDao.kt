package com.grocer.billing.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grocer.billing.core.data.local.entities.StockLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockLogDao {
    @Query("SELECT * FROM stock_log WHERE item_id = :itemId ORDER BY created_at DESC")
    fun observeStockLogsForItem(itemId: String): Flow<List<StockLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: StockLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<StockLogEntity>)
}
