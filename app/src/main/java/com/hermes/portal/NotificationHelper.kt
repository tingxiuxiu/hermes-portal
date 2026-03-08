package com.hermes.portal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat

class NotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "test_automation_channel"
        private const val CHANNEL_NAME = "Automation Test"
        private const val NOTIFY_ID = 9999
    }

    fun showTestNotification(title: String, content: String, durationMs: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. 创建高优先级渠道 (Android 8.0+)
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH // 关键：HIGH 才能弹出顶部滑入通知
        ).apply {
            description = "Used for UI Automation testing"
            enableVibration(true)
            setAllowBubbles(false) // 禁用气泡通知，使用普通横幅通知
        }
        notificationManager.createNotificationChannel(channel)

        // 2. 构建通知
        // 创建一个空的 Intent，防止点击报错
        val intent = Intent()
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 使用系统图标
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 确保弹出顶部滑入通知
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // 3. 设置超时自动消失 (Android 8.0+)
        builder.setTimeoutAfter(durationMs)

        // 4. 发送
        notificationManager.notify(NOTIFY_ID, builder.build())
    }

    fun createForegroundServiceNotification(): Notification {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 确保通知渠道存在
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW // 前台服务通知使用低优先级
        ).apply {
            description = "Used for UI Automation testing"
        }
        notificationManager.createNotificationChannel(channel)

        // 创建一个空的 Intent，防止点击报错
        val intent = Intent()
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 使用系统图标
            .setContentTitle("Hermes HTTP Server")
            .setContentText("Running on port 8089")
            .setPriority(NotificationCompat.PRIORITY_LOW) // 前台服务通知使用低优先级
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 前台服务通知不可取消
            .build()
    }
}
