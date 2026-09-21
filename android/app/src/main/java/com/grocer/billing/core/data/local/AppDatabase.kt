package com.grocer.billing.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.grocer.billing.core.data.local.dao.BillDao
import com.grocer.billing.core.data.local.dao.ItemDao
import com.grocer.billing.core.data.local.dao.ShopDao
import com.grocer.billing.core.data.local.dao.StockLogDao
import com.grocer.billing.core.data.local.dao.SyncQueueDao
import com.grocer.billing.core.data.local.entities.BillEntity
import com.grocer.billing.core.data.local.entities.BillItemEntity
import com.grocer.billing.core.data.local.entities.EmbeddingEntity
import com.grocer.billing.core.data.local.entities.ItemEntity
import com.grocer.billing.core.data.local.entities.ShopEntity
import com.grocer.billing.core.data.local.entities.StockLogEntity
import com.grocer.billing.core.data.local.entities.SyncQueueEntity

@Database(
    entities = [
        ShopEntity::class,
        ItemEntity::class,
        EmbeddingEntity::class,
        BillEntity::class,
        BillItemEntity::class,
        StockLogEntity::class,
        SyncQueueEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shopDao(): ShopDao
    abstract fun itemDao(): ItemDao
    abstract fun billDao(): BillDao
    abstract fun stockLogDao(): StockLogDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun embeddingDao(): com.grocer.billing.core.data.local.dao.EmbeddingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kirana_counter.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
