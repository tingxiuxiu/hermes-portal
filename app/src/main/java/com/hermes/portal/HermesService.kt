package com.hermes.portal

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class HermesService : Service() {
    companion object {
        private const val TAG = "HermesService"
        private const val NOTIFICATION_ID = 1001
    }

    private var httpServer: HermesHttpServer? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HermesService onCreate called")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "HermesService onStartCommand called")

        // 创建并启动 HTTP 服务器
        if (httpServer == null) {
            httpServer = HermesHttpServer(this)
            httpServer?.start()
            Log.d(TAG, "HermesService started")

            // 创建前台服务通知
            val notification: Notification = NotificationHelper(this).createForegroundServiceNotification()
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "HermesService started as foreground")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "HermesService onDestroy called")
        // 停止 HTTP 服务器
        httpServer?.stop()
        httpServer = null
        Log.d(TAG, "HermesService stopped")
    }
}
