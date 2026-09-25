package com.grocer.billing.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.grocer.billing.MainActivity
import com.grocer.billing.R

object StockNotificationManager {
    private const val CHANNEL_ID = "low_stock_channel"
    private const val CHANNEL_NAME = "Low Stock Alerts"
    private const val CHANNEL_DESC = "Notifications for items that are running low on stock"

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun sendLowStockNotification(
        context: Context,
        itemId: String,
        itemName: String,
        currentStock: Double,
        threshold: Double,
        unitType: String
    ) {
        initChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "inventory")
            putExtra("highlight_item_id", itemId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            itemId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val unitLabel = if (unitType == "piece") "pcs" else "kg"
        val stockFormatted = if (currentStock % 1.0 == 0.0) currentStock.toInt().toString() else "%.2f".format(currentStock)
        val thresholdFormatted = if (threshold % 1.0 == 0.0) threshold.toInt().toString() else "%.2f".format(threshold)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Low Stock Alert: $itemName")
            .setContentText("Stock is running low: $stockFormatted $unitLabel left (alert limit: $thresholdFormatted $unitLabel). Tap to restock.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "\"$itemName\" has reached low stock level with only $stockFormatted $unitLabel remaining (alert threshold: $thresholdFormatted $unitLabel). Please restock soon to avoid running out!"
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(itemId.hashCode(), builder.build())
            }
        } catch (_: SecurityException) {
            // Permission was not granted or denied by user
        }
    }
}
