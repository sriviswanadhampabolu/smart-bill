package com.grocer.billing

import android.app.Application
import com.grocer.billing.core.data.local.AppDatabase
import com.grocer.billing.core.data.remote.RetrofitClient
import com.grocer.billing.core.data.repository.AuthRepository
import com.grocer.billing.core.data.repository.BillingRepository
import com.grocer.billing.core.data.repository.ItemRepository
import com.grocer.billing.core.data.repository.ShopRepository

class BillingApplication : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var retrofitClient: RetrofitClient
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var shopRepository: ShopRepository
        private set
    lateinit var itemRepository: ItemRepository
        private set
    lateinit var billingRepository: BillingRepository
        private set
    lateinit var imageEmbedder: com.grocer.billing.core.vision.ImageEmbedder
        private set
    lateinit var vectorCache: com.grocer.billing.core.vision.VectorCache
        private set
    lateinit var imageQualityChecker: com.grocer.billing.core.vision.ImageQualityChecker
        private set
    lateinit var embeddingRepository: com.grocer.billing.core.data.repository.EmbeddingRepository
        private set
    lateinit var onboardingRepository: com.grocer.billing.core.data.repository.OnboardingRepository
        private set
    lateinit var reportsRepository: com.grocer.billing.core.data.repository.ReportsRepository
        private set
    lateinit var syncManager: com.grocer.billing.core.data.sync.SyncManager
        private set
    lateinit var localBackupManager: com.grocer.billing.core.data.backup.LocalBackupManager
        private set

    override fun onCreate() {
        super.onCreate()
        com.grocer.billing.core.notification.StockNotificationManager.initChannel(this)
        com.grocer.billing.core.lang.AppLanguageManager.init(this)
        database = AppDatabase.getInstance(this)
        retrofitClient = RetrofitClient(this)

        imageEmbedder = com.grocer.billing.core.vision.MobileNetV3Embedder(this)
        vectorCache = com.grocer.billing.core.vision.VectorCache()
        imageQualityChecker = com.grocer.billing.core.vision.ImageQualityChecker()

        shopRepository = ShopRepository(database.shopDao())

        localBackupManager = com.grocer.billing.core.data.backup.LocalBackupManager(
            context = this,
            database = database,
            shopDao = database.shopDao(),
            itemDao = database.itemDao(),
            embeddingDao = database.embeddingDao(),
            billDao = database.billDao()
        )

        embeddingRepository = com.grocer.billing.core.data.repository.EmbeddingRepository(
            this,
            database,
            database.embeddingDao(),
            database.itemDao(),
            database.syncQueueDao(),
            imageEmbedder,
            vectorCache,
            localBackupManager
        )

        syncManager = com.grocer.billing.core.data.sync.SyncManager(
            syncQueueDao = database.syncQueueDao(),
            billDao = database.billDao(),
            itemDao = database.itemDao(),
            shopDao = database.shopDao(),
            embeddingDao = database.embeddingDao(),
            retrofitClient = retrofitClient,
            embeddingRepository = embeddingRepository
        )

        authRepository = AuthRepository(
            shopDao = database.shopDao(),
            context = this,
            retrofitClient = retrofitClient,
            syncManager = syncManager
        )

        itemRepository = ItemRepository(
            database = database,
            itemDao = database.itemDao(),
            stockLogDao = database.stockLogDao(),
            localBackupManager = localBackupManager,
            syncQueueDao = database.syncQueueDao(),
            syncManager = syncManager,
            vectorCache = vectorCache,
            context = this
        )

        billingRepository = BillingRepository(
            database = database,
            billDao = database.billDao(),
            itemDao = database.itemDao(),
            stockLogDao = database.stockLogDao(),
            syncQueueDao = database.syncQueueDao(),
            localBackupManager = localBackupManager,
            syncManager = syncManager,
            context = this
        )

        onboardingRepository = com.grocer.billing.core.data.repository.OnboardingRepository(
            this,
            itemRepository
        )
        reportsRepository = com.grocer.billing.core.data.repository.ReportsRepository(database)

        // Ensure store counter starts locked on app opening if user logged in
        if (authRepository.isUserLoggedIn()) {
            authRepository.lockApp()
            val activeShopId = authRepository.getActiveShopId() ?: ""
            if (activeShopId.isNotBlank()) {
                syncManager.triggerAutoSync(activeShopId)
            }
        }
    }
}
