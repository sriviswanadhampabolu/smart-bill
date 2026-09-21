package com.grocer.billing.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grocer.billing.core.data.local.entities.SyncQueueEntity

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY created_at ASC LIMIT :limit")
    suspend fun getPendingSyncItems(limit: Int = 50): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entry: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE sync_queue SET retry_count = retry_count + 1, last_error = :error WHERE id = :id")
    suspend fun recordRetry(id: String, error: String)

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun getPendingCount(): Int
}
