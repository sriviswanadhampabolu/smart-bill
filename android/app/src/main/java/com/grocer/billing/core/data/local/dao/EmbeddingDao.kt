package com.grocer.billing.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grocer.billing.core.data.local.entities.EmbeddingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmbeddingDao {
    @Query("SELECT * FROM embeddings WHERE item_id = :itemId")
    suspend fun getEmbeddingsForItem(itemId: String): List<EmbeddingEntity>

    @Query("SELECT * FROM embeddings WHERE item_id = :itemId")
    fun observeEmbeddingsForItem(itemId: String): Flow<List<EmbeddingEntity>>

    @Query("SELECT * FROM embeddings")
    suspend fun getAllEmbeddings(): List<EmbeddingEntity>

    @Query("SELECT item_id, vector FROM embeddings")
    suspend fun getAllVectors(): List<ItemVectorTuple>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbedding(embedding: EmbeddingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbeddings(embeddings: List<EmbeddingEntity>)

    @Query("DELETE FROM embeddings WHERE item_id = :itemId")
    suspend fun deleteEmbeddingsForItem(itemId: String)

    @Query("SELECT COUNT(*) FROM embeddings WHERE item_id = :itemId")
    suspend fun getEmbeddingCountForItem(itemId: String): Int
}

data class ItemVectorTuple(
    val item_id: String,
    val vector: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ItemVectorTuple
        return item_id == other.item_id && vector.contentEquals(other.vector)
    }

    override fun hashCode(): Int {
        var result = item_id.hashCode()
        result = 31 * result + vector.contentHashCode()
        return result
    }
}
