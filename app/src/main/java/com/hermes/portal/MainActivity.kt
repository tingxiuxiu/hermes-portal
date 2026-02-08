package com.hermes.portal

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.Manifest
import android.provider.Settings
import android.accessibilityservice.AccessibilityServiceInfo
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

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
    
    fun startStatusService() {
        // 启动前台服务（Android 8.0+ 必需）
        val serviceIntent = Intent(this, StatusService::class.java)
        startForegroundService(serviceIntent)
    }
    
    fun stopStatusService() {
        // 停止前台服务
        val serviceIntent = Intent(this, StatusService::class.java)
        stopService(serviceIntent)
    }
}

@Composable
fun HermesPortalApp(
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val context = LocalContext.current
    
    // 状态变量
    var notificationPermissionGranted by remember { mutableStateOf(false) }
    var accessibilityServiceEnabled by remember { mutableStateOf(false) }
    var httpServerRunning by remember { mutableStateOf(false) }
    var accessibilityCheckCompleted by remember { mutableStateOf(false) }
    
    // 检查状态
    LaunchedEffect(Unit) {
        checkStatus(
            context = context,
            onNotificationPermissionChecked = { granted -> notificationPermissionGranted = granted },
            onAccessibilityServiceChecked = { enabled -> 
                accessibilityServiceEnabled = enabled
                // 如果无障碍服务已启用，标记检查完成
                if (enabled) {
                    accessibilityCheckCompleted = true
                }
            },
            onHttpServerChecked = { running -> httpServerRunning = running }
        )
        
        // 每2秒检查一次状态，直到无障碍服务检查完成
        while (!accessibilityCheckCompleted) {
            delay(2000)
            checkStatus(
                context = context,
                onNotificationPermissionChecked = { granted -> notificationPermissionGranted = granted },
                onAccessibilityServiceChecked = { enabled -> 
                    accessibilityServiceEnabled = enabled
                    // 如果无障碍服务已启用，标记检查完成
                    if (enabled) {
                        accessibilityCheckCompleted = true
                    }
                },
                onHttpServerChecked = { running -> httpServerRunning = running }
            )
        }
        
        // 无障碍服务检查完成后，只检查一次通知权限和HTTP服务器状态
        delay(2000)
        checkStatus(
            context = context,
            onNotificationPermissionChecked = { granted -> notificationPermissionGranted = granted },
            onAccessibilityServiceChecked = { enabled -> accessibilityServiceEnabled = enabled },
            onHttpServerChecked = { running -> httpServerRunning = running }
        )
    }
    
    MaterialTheme {
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Hermes Portal",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 服务状态卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "服务状态",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        // 通知权限状态
                        StatusItem(
                            title = "通知权限",
                            status = if (notificationPermissionGranted) "已授予" else "未授予",
                            isSuccess = notificationPermissionGranted,
                            onClick = {
                                if (!notificationPermissionGranted) {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    intent.data = android.net.Uri.fromParts("package", context.packageName, null)
                                    context.startActivity(intent)
                                }
                            }
                        )
                        
                        // 无障碍服务状态
                        StatusItem(
                            title = "无障碍服务",
                            status = if (accessibilityServiceEnabled) "已启用" else "未启用",
                            isSuccess = accessibilityServiceEnabled,
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            }
                        )
                        
                        // HTTP服务器状态
                        StatusItem(
                            title = "HTTP服务器",
                            status = if (httpServerRunning) "运行中" else "未运行",
                            isSuccess = httpServerRunning,
                            onClick = {}
                        )
                    }
                }
                
                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onStartService()
                            Toast.makeText(context, "服务已启动", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(text = "启动服务")
                    }
                    
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onStopService()
                            Toast.makeText(context, "服务已停止", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(text = "停止服务")
                    }
                }
                
                // 服务信息
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "服务信息",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(text = "HTTP服务器端口: 8080")
                        Text(text = "API端点:")
                        Text(text = "  - GET /window-status (窗口状态)")
                        Text(text = "  - POST /gesture/easy-zoom (屏幕缩放)")
                        Text(text = "  - POST /gesture/zoom (自定义缩放)")
                        Text(text = "  - GET /nodes/{displayId} (组件树)")
                        Text(text = "  - POST /service/stop (停止服务)")
                        Text(text = "  - POST /input/text (填充文本)")
                        Text(text = "  - POST /input/clear (清除输入框文本)")
                        Text(text = "  - POST /gesture/tap (点击)")
                        Text(text = "  - POST /gesture/long-press (长按)")
                        Text(text = "  - POST /gesture/swipe (滑动)")
                        Text(text = "  - POST /gesture/scroll-query (滚动查询目标元素)")
                        Text(text = "  - GET /screenshot (截图)")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusItem(
    title: String,
    status: String,
    isSuccess: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = status,
                color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            if (!isSuccess) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Text(text = "设置")
                }
            }
        }
    }
}

private fun checkStatus(
    context: android.content.Context,
    onNotificationPermissionChecked: (Boolean) -> Unit,
    onAccessibilityServiceChecked: (Boolean) -> Unit,
    onHttpServerChecked: (Boolean) -> Unit
) {
    // 检查通知权限
    val notificationPermissionGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    onNotificationPermissionChecked(notificationPermissionGranted)
    
    // 检查无障碍服务
    val expectedId = "${context.packageName}/.HermesAccessibilityService"
    android.util.Log.d("MainActivity", "Expected service ID: $expectedId")
    
    // 方法1：使用无障碍服务管理器
    val accessibilityManager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
    val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    android.util.Log.d("MainActivity", "Method 1 - Enabled services count: ${enabledServices.size}")
    
    for (service in enabledServices) {
        android.util.Log.d("MainActivity", "Method 1 - Service ID: ${service.id}")
    }
    
    // 方法2：直接读取系统设置
    val enabledServicesSetting = android.provider.Settings.Secure.getString(
        context.contentResolver, 
        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    android.util.Log.d("MainActivity", "Method 2 - Enabled services setting: $enabledServicesSetting")
    
    // 方法3：检查服务实例是否存在
    val serviceInstanceExists = HermesAccessibilityService.instance != null
    android.util.Log.d("MainActivity", "Method 3 - Service instance exists: $serviceInstanceExists")
    
    // 综合判断
    val accessibilityServiceEnabled = 
        // 方法1：检查服务是否在启用列表中
        enabledServices.any {
            it.id == expectedId ||
            it.id.contains(context.packageName) ||
            it.id.contains("HermesAccessibilityService")
        } ||
        // 方法2：检查服务是否在系统设置中启用
        !enabledServicesSetting.isNullOrEmpty() && (
            enabledServicesSetting.contains(expectedId) ||
            enabledServicesSetting.contains(context.packageName) ||
            enabledServicesSetting.contains("HermesAccessibilityService")
        ) ||
        // 方法3：检查服务实例是否存在
        serviceInstanceExists
    
    android.util.Log.d("MainActivity", "Accessibility service enabled: $accessibilityServiceEnabled")
    onAccessibilityServiceChecked(accessibilityServiceEnabled)
    
    // 检查HTTP服务器状态
    // 注意：这里需要根据实际情况实现，暂时返回true
    onHttpServerChecked(true)
}