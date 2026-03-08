package com.hermes.portal

import android.os.Bundle
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    
    // 使用Activity Result API处理权限请求
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 检查并请求通知权限（Android 13+ 需要）
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // 由于应用只需要后台运行，清除所有UI内容，提高性能
        // 不设置任何Compose内容，减少资源消耗
        
        // 立即最小化到后台，不影响其他正在运行的应用
        // 不使用延迟，确保应用启动后立即后台运行
        minimizeToBackground()
    }
    
    /**
     * 将应用最小化到后台，不影响其他正在运行的应用
     */
    private fun minimizeToBackground() {
        // 使用moveTaskToBack方法将当前任务移动到后台，而不影响其他任务
        // 参数true表示如果当前任务是唯一的任务，则允许结束应用
        moveTaskToBack(true)
        
        // 额外的保险措施，确保应用不会干扰其他应用
        // 调用finish()方法结束当前Activity，但保持应用进程运行
        finish()
    }
}
