package com.hermes.portal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class StatusService : Service() {
    private val CHANNEL_ID = "status_channel"
    private val NOTIFICATION_ID = 1
    private var httpServerJob: CompletableDeferred<Unit>? = null
    private val _httpServerRunning = MutableStateFlow(false)
    val httpServerRunning: StateFlow<Boolean> = _httpServerRunning.asStateFlow()
    private var statusCheckJob: kotlinx.coroutines.Job? = null
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onCreate() {
        super.onCreate()
        // 提前创建通知渠道
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("StatusService", "onStartCommand called")
        
        // 确保在5秒内调用startForeground()，这对于使用startForegroundService()启动的服务是必需的
        startForegroundWithNotification()
        
        // 启动HTTP服务
        startHttpServer()
        
        // 显示服务启动成功的通知
        showServiceStartedNotification()
        
        // 定期检查无障碍服务状态并更新通知
        statusCheckJob?.cancel() // 取消之前的任务
        statusCheckJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                delay(5000) // 每5秒检查一次
                updateNotification()
            }
        }
        
        return START_STICKY
    }
    
    /**
     * 显示服务启动成功的通知
     */
    private fun showServiceStartedNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hermes Portal")
            .setContentText("服务已成功启动")
            .setSmallIcon(android.R.drawable.ic_notification_clear_all)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true) // 设置为自动取消，用户可以点击移除
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification) // 使用不同的ID避免覆盖前台通知
    }
    
    private fun updateNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hermes Portal")
            .setContentText(if (isAccessibilityServiceEnabled()) "应用正在运行中" else "请在设置中启用无障碍服务")
            .setSmallIcon(android.R.drawable.ic_notification_clear_all)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // 设置为持续通知，用户不能通过滑动移除
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hermes Portal")
            .setContentText(if (isAccessibilityServiceEnabled()) "应用正在运行中" else "请在设置中启用无障碍服务")
            .setSmallIcon(android.R.drawable.ic_notification_clear_all)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // 设置为持续通知，用户不能通过滑动移除
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val accessibilityManager = getSystemService(android.view.accessibility.AccessibilityManager::class.java)
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val expectedId = "${packageName}/.HermesAccessibilityService"
            
            // 检查服务是否在启用列表中
            for (service in enabledServices) {
                if (service.id == expectedId ||
                    service.id.contains(packageName) ||
                    service.id.contains("HermesAccessibilityService")) {
                    return true
                }
            }
            
            // 检查服务实例是否存在
            return HermesAccessibilityService.instance != null
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    private fun startHttpServer() {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                _httpServerRunning.value = true
                
                // 先完成之前的任务，确保旧服务器停止
                httpServerJob?.complete(Unit)
                // 等待一段时间，确保旧服务器完全释放端口
                delay(1000)
                
                httpServerJob = CompletableDeferred()
                val server = HermesHttpServer(httpServerJob!!)
                server.server.start(wait = false)
                
                // 等待停止信号
                httpServerJob!!.await()
                
                // 停止服务器
                server.server.stop(500, 1000)
            } catch (e: Exception) {
                e.printStackTrace()
                // 检查是否是端口占用错误
                if (e is java.net.BindException) {
                    // 端口占用时，等待更长时间后重试
                    delay(3000)
                } else {
                    // 其他异常，等待2秒后重试
                    delay(2000)
                }
                startHttpServer()
            } finally {
                _httpServerRunning.value = false
            }
        }
    }
    
    private fun createNotificationChannel() {
        try {
            // 由于minSdk=36，总是支持通知渠道
            val name = "状态通知"
            val descriptionText = "显示应用运行状态"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 停止HTTP服务
        httpServerJob?.complete(Unit)
        // 取消状态检查任务
        statusCheckJob?.cancel()
        // 由于minSdk=36，总是可以使用现代API
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
    }
}