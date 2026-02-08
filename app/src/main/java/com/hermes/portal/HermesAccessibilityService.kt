package com.hermes.portal

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicLong
import android.graphics.Rect
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.util.concurrent.CompletableFuture
import androidx.core.util.size

class HermesAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var TAG = "HermesAccessibilityService"
    companion object {
        var instance: HermesAccessibilityService? = null
        // 高效监测：原子计数器，窗口变化即自增
        val windowStateId = AtomicLong(0)
    }

    override fun onServiceConnected() {
        instance = this
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or 
                         AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                    AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        windowStateId.incrementAndGet()
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    // 获取屏幕 json
    fun getJsonHierarchy(displayId: Int): UiNodeJson? {
        // 找到对应屏幕的窗口
        val targetWindow = windows.find { it.displayId == displayId }
        val root = targetWindow?.root ?: if (displayId == 0) rootInActiveWindow else null
        
        return root?.let { 
            val result = nodeToJson(it, 0)
            // 记得回收根节点
            // it.recycle() // 取决于你的 Android 版本和 root 获取方式
            result
        }
    }

    private fun nodeToJson(node: AccessibilityNodeInfo, index: Int): UiNodeJson {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val childrenList = mutableListOf<UiNodeJson>()
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                childrenList.add(nodeToJson(child, i))
                child.recycle() // 重要：递归过程中回收子节点，防止内存溢出
            }
        }

        return UiNodeJson(
            index = index,
            text = node.text?.toString(),
            resourceId = node.viewIdResourceName,
            className = node.className?.toString(),
            packageName = node.packageName?.toString(),
            contentDesc = node.contentDescription?.toString(),
            bounds = NodeBounds(rect.left, rect.top, rect.right, rect.bottom),
            visible = node.isVisibleToUser,
            checkable = node.isCheckable,
            checked = node.isChecked,
            clickable = node.isClickable,
            enabled = node.isEnabled,
            focusable = node.isFocusable,
            focused = node.isFocused,
            scrollable = node.isScrollable,
            longClickable = node.isLongClickable,
            password = node.isPassword,
            selected = node.isSelected,
            drawingOrder = node.drawingOrder,
            children = childrenList
        )
    }

    /**
     * 根据指定屏幕获取元素树
     */
    fun getXmlForDisplay(displayId: Int): String {
        // 获取所有屏幕的窗口信息
        val allWindows = windowsOnAllDisplays
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>\n")
        sb.append("<hierarchy rotation=\"$displayId\">\n")
        // 按屏幕 ID 分组
        for (i in 0 until allWindows.size) {
            val mDisplayId = allWindows.keyAt(i)
            val windows = allWindows.valueAt(i)
            Log.i(TAG, "--- 正在扫描屏幕 ID: $mDisplayId (窗口数量: ${windows.size}) ---")
            if (mDisplayId == displayId) {
                // 查找指定屏幕的窗口
                for (window in windows) {
                    if (window.displayId == displayId) {
                        val rootNode = window.root
                        serializeNode(rootNode, sb, 0)
                        rootNode.recycle()
                    }
                }
                val windowsOnDisplay = allWindows.get(displayId)
                if (windowsOnDisplay.isNullOrEmpty()) {
                    return "<error>No windows found on display $displayId</error>"
                }
            }
        }
        sb.append("</hierarchy>")
        return sb.toString()
    }

    private fun appendAttribute(sb: StringBuilder, name: String, value: String) {
        val escapedValue = value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
        sb.append("$name=\"$escapedValue\" ")
    }

    private fun serializeNode(node: AccessibilityNodeInfo, sb: StringBuilder, index: Int) {
        // 1. 创建 Rect 实例
        val bounds = Rect()
        // 2. 将节点的坐标填充到 bounds 对象中
        node.getBoundsInScreen(bounds)

        // 格式化类名（去除包名，只留简名）
        val className = node.className?: "node"

        sb.append("<$className ")
        // 映射所有标准属性
        appendAttribute(sb, "index", index.toString())
        // 关键点：bounds 格式 left,top,right,bottom
        appendAttribute(sb, "bounds", "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}")
        appendAttribute(sb, "text", node.text?.toString() ?: "")
        appendAttribute(sb, "resource-id", node.viewIdResourceName ?: "")
        appendAttribute(sb, "content-desc", node.contentDescription?.toString() ?: "")
        appendAttribute(sb, "class", node.className?.toString() ?: "")
        appendAttribute(sb, "visible", node.isVisibleToUser.toString())
        appendAttribute(sb, "checkable", node.isCheckable.toString())
        appendAttribute(sb, "checked", node.isChecked.toString())
        appendAttribute(sb, "selected", node.isSelected.toString())
        appendAttribute(sb, "enabled", node.isEnabled.toString())
        appendAttribute(sb, "clickable", node.isClickable.toString())
        appendAttribute(sb, "focusable", node.isFocusable.toString())
        appendAttribute(sb, "focused", node.isFocused.toString())
        appendAttribute(sb, "scrollable", node.isScrollable.toString())
        appendAttribute(sb, "long-clickable", node.isLongClickable.toString())
        appendAttribute(sb, "password", node.isPassword.toString())
        appendAttribute(sb, "drawing-order", node.drawingOrder.toString())
        appendAttribute(sb, "package", node.packageName?.toString() ?: "")

        // 递归处理子节点
        if (node.childCount > 0) {
            sb.append(">\n")
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    serializeNode(child, sb, i)
                    // 注意：获取后的 child 需要释放以避免内存泄漏
                    child.recycle()
                }
            }
            sb.append("  </$className>\n")
        } else {
            sb.append(" />\n")
        }
    }

    // 实现缩放 (Pinch/Zoom)
    fun performZoom(displayId: Int, isZoomIn: Boolean): Boolean {
        val displayMetrics = resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        val centerY = displayMetrics.heightPixels / 2f
        
        val duration = 800L // 增加手势持续时间
        val startDistance = 100f // 起始距离
        val endDistance = if (isZoomIn) 300f else 50f // 结束距离

        // 计算两条手指的起始和结束位置
        val startX1 = centerX - startDistance / 2f
        val startY1 = centerY
        val startX2 = centerX + startDistance / 2f
        val startY2 = centerY
        
        val endX1 = centerX - endDistance / 2f
        val endY1 = centerY
        val endX2 = centerX + endDistance / 2f
        val endY2 = centerY

        // 构建两条手指滑动的路径
        val path1 = Path().apply {
            moveTo(startX1, startY1)
            lineTo(endX1, endY1)
        }
        val path2 = Path().apply {
            moveTo(startX2, startY2)
            lineTo(endX2, endY2)
        }

        val stroke1 = GestureDescription.StrokeDescription(path1, 0, duration)
        val stroke2 = GestureDescription.StrokeDescription(path2, 0, duration)
        
        val gestureBuilder = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)

        // Android 11+ 支持 setDisplayId，用于多屏操作
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            gestureBuilder.setDisplayId(displayId)
        }

        val gesture = gestureBuilder.build()
        val result = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d("HermesAccessibilityService", "Zoom gesture completed successfully")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.e("HermesAccessibilityService", "Zoom gesture cancelled")
            }
        }, null)

        Log.d("HermesAccessibilityService", "Zoom gesture dispatched: $result, Display ID: $displayId, Is Zoom In: $isZoomIn, Start Distance: $startDistance, End Distance: $endDistance")
        return result
    }

    fun performCustomGesture(request: CustomZoomRequest): Boolean {
        val path1 = Path().apply {
            moveTo(request.finger1.start.x, request.finger1.start.y)
            lineTo(request.finger1.end.x, request.finger1.end.y)
        }

        val path2 = Path().apply {
            moveTo(request.finger2.start.x, request.finger2.start.y)
            lineTo(request.finger2.end.x, request.finger2.end.y)
        }

        // 创建两个手指的描边
        val stroke1 = GestureDescription.StrokeDescription(path1, 0, request.duration)
        val stroke2 = GestureDescription.StrokeDescription(path2, 0, request.duration)

        val gestureBuilder = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)

        // Android 7.0+ 支持 setDisplayId，用于多屏操作
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            gestureBuilder.setDisplayId(request.displayId)
        }

        return dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
            }
        }, null)
    }

    fun inputText(request: TextInputRequest): Boolean {
        // 1. 获取目标屏幕的所有窗口
        val windows = getWindows().filter { it.displayId == request.displayId }

        var targetNode: AccessibilityNodeInfo? = null

        for (window in windows) {
            val root = window.root ?: continue
            targetNode = findFocusedInput(root)
            if (targetNode != null) break
        }

        // 2. 执行填充动作
        return targetNode?.let { node ->
            if (node.isEditable) {
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    request.text
                )
                val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                node.recycle() // 及时回收引用
                success
            } else {
                false
            }
        } ?: false
    }

    // 点击操作
    fun performTap(displayId: Int, x: Float, y: Float, duration: Long = 100L): Boolean {
        val path = android.graphics.Path().apply {
            moveTo(x, y)
            lineTo(x + 1, y + 1)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val builder = GestureDescription.Builder().addStroke(stroke)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            builder.setDisplayId(displayId)
        }

        val gesture = builder.build()
        return dispatchGesture(gesture, null, null)
    }

    // 长按操作
    fun performLongPress(displayId: Int, x: Float, y: Float, duration: Long = 1000L): Boolean {
        val path = android.graphics.Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val builder = GestureDescription.Builder().addStroke(stroke)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            builder.setDisplayId(displayId)
        }

        val gesture = builder.build()
        return dispatchGesture(gesture, null, null)
    }

    // 清除输入框文本
    fun clearInputText(displayId: Int): Boolean {
        val windows = getWindows().filter { it.displayId == displayId }

        var targetNode: AccessibilityNodeInfo? = null

        // 查找当前屏幕上获得焦点的输入框
        for (window in windows) {
            val root = window.root ?: continue
            targetNode = findFocusedInput(root)
            if (targetNode != null) break
        }

        // 执行清除动作
        return targetNode?.let { node ->
            if (node.isEditable) {
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    ""
                )
                val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                node.recycle()
                success
            } else {
                false
            }
        } ?: false
    }

    // 滑动操作
    fun performSwipe(displayId: Int, startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 500L): Boolean {
        val path = android.graphics.Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val builder = GestureDescription.Builder().addStroke(stroke)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            builder.setDisplayId(displayId)
        }

        val gesture = builder.build()
        return dispatchGesture(gesture, null, null)
    }

    // 滚动查询目标元素
    // --- 智能滚动搜索 ---
    suspend fun scrollSearch(req: ScrollSearchRequest): FoundNodeInfo? {
        repeat(req.maxRetries) {
            val root = getRoot(req.displayId) ?: return null
            val target = findMatch(root, req)
            if (target != null) {
                val r = Rect().apply { target.getBoundsInScreen(this) }
                val info = FoundNodeInfo(target.text?.toString(), target.viewIdResourceName, target.className?.toString(), NodeBounds(r.left, r.top, r.right, r.bottom), req.displayId)
                target.recycle(); root.recycle(); return info
            }
            // 查找滚动容器
            val container = findContainer(root, req)
            if (container != null) {
                val action = if (req.direction in listOf("down", "right"))
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                val scrolled = container.performAction(action)
                container.recycle(); root.recycle()
                if (!scrolled) return null
                kotlinx.coroutines.delay(700)
            } else { root.recycle(); return null }
        }
        return null
    }

    // 辅助：获取特定屏根节点
    private fun getRoot(displayId: Int) = windows.find { it.displayId == displayId }?.root ?: if(displayId==0) rootInActiveWindow else null

    // 辅助：匹配节点
    private fun findMatch(node: AccessibilityNodeInfo, req: ScrollSearchRequest): AccessibilityNodeInfo? {
        if ((req.text != null && node.text?.toString() == req.text) ||
            (req.resourceId != null && node.viewIdResourceName == req.resourceId)) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val res = findMatch(child, req)
            child.recycle()
            if (res != null) return res
        }
        return null
    }

    // 辅助：寻找最佳容器 (面积最大优先)
    private fun findContainer(root: AccessibilityNodeInfo, req: ScrollSearchRequest): AccessibilityNodeInfo? {
        val list = mutableListOf<AccessibilityNodeInfo>()
        fun find(n: AccessibilityNodeInfo) {
            if (n.isScrollable && (req.containerResourceId == null || n.viewIdResourceName == req.containerResourceId)) list.add(AccessibilityNodeInfo.obtain(n))
            for (i in 0 until n.childCount) n.getChild(i)?.let { find(it); it.recycle() }
        }
        find(root)
        val best = list.maxByOrNull { val r = Rect(); it.getBoundsInScreen(r); r.width() * r.height() }
        list.forEach { if (it != best) it.recycle() }
        return best
    }

    // 根据文本查找节点
    private fun findNodeByText(node: AccessibilityNodeInfo, query: String): Boolean {
        if (node.text?.toString()?.contains(query) == true ||
            node.contentDescription?.toString()?.contains(query) == true) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findNodeByText(child, query)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    // 递归查找已聚焦且可编辑的节点
    private fun findFocusedInput(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFocusedInput(child)
            if (found != null) return found
        }
        return null
    }

    fun captureScreenAndGetBytes(displayId: Int, future: CompletableFuture<ByteArray>) {
        try {
            takeScreenshot(
                displayId,
                mainHandler.looper.thread.contextClassLoader?.let {
                    java.util.concurrent.Executors.newSingleThreadExecutor()
                } ?: java.util.concurrent.Executors.newSingleThreadExecutor(),
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        try {
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                screenshotResult.hardwareBuffer,
                                screenshotResult.colorSpace
                            )

                            if (bitmap == null) {
                                Log.e(TAG, "Failed to create bitmap from hardware buffer")
                                screenshotResult.hardwareBuffer.close()
                                future.completeExceptionally(IllegalStateException("Failed to create bitmap from screenshot data"))
                                return
                            }

                            val byteArrayOutputStream = ByteArrayOutputStream()
                            val compressionSuccess = bitmap.compress(
                                Bitmap.CompressFormat.PNG,
                                100,
                                byteArrayOutputStream,
                            )

                            if (!compressionSuccess) {
                                Log.e(TAG, "Failed to compress bitmap to PNG")
                                bitmap.recycle()
                                screenshotResult.hardwareBuffer.close()
                                byteArrayOutputStream.close()
                                future.completeExceptionally(IllegalStateException("Failed to compress screenshot to PNG format"))
                                return
                            }

                            val byteArray = byteArrayOutputStream.toByteArray()

                            bitmap.recycle()
                            screenshotResult.hardwareBuffer.close()
                            byteArrayOutputStream.close()

                            future.complete(byteArray)
                            Log.d(
                                TAG,
                                "Screenshot captured successfully as bytes, size: ${byteArray.size} bytes",
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing screenshot", e)
                            try {
                                screenshotResult.hardwareBuffer.close()
                            } catch (closeException: Exception) {
                                Log.e(TAG, "Error closing hardware buffer", closeException)
                            }
                            future.completeExceptionally(e)
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        val errorMessage = when (errorCode) {
                            ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "Internal error occurred"
                            ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "Screenshot interval too short"
                            ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "Invalid display"
                            ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "No accessibility access"
                            ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "Secure window cannot be captured"
                            else -> "Unknown error (code: $errorCode)"
                        }
                        Log.e(TAG, "Screenshot failed: $errorMessage")
                        future.completeExceptionally(IllegalStateException("Screenshot failed: $errorMessage"))
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("HermesAccessibilityService", "Error capturing screen: ${e.message}", e)
        }
    }
}
