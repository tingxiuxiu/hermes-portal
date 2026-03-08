package com.hermes.portal
import kotlinx.serialization.Serializable

@Serializable
data class Point(val x: Float, val y: Float)

@Serializable
data class FingerPath(
    val start: Point,
    val end: Point
)

@Serializable
data class CustomZoomRequest(
    val displayId: Int = 0,
    val finger1: FingerPath,
    val finger2: FingerPath,
    val duration: Long = 500L // 手势持续时间
)

@Serializable
data class TextInputRequest(
    val displayId: Int = 0,
    val text: String                // 要填充的中文内容
)

@Serializable
data class TextInputClearRequest(
    val displayId: Int = 0
)

@Serializable
data class TapRequest(
    val displayId: Int = 0,
    val x: Float,
    val y: Float,
    val duration: Long = 100L // 点击持续时间
)

@Serializable
data class LongPressRequest(
    val displayId: Int = 0,
    val x: Float,
    val y: Float,
    val duration: Long = 1000L // 长按持续时间
)

@Serializable
data class SwipeRequest(
    val displayId: Int = 0,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val duration: Long = 500L // 滑动持续时间
)

@Serializable
data class ScrollSearchRequest(
    val displayId: Int = 0,
    // 目标元素搜索
    val resourceId: String? = null,
    val className: String? = null,
    val text: String? = null,
    val description: String? = null,

    // 容器搜索
    val containerResourceId: String? = null,
//    val containerClassName: String? = null,

    // 方向：up, down, left, right
    val direction: String = "down",
    val maxRetries: Int = 5
)

@Serializable
data class FoundNodeInfo(
    val text: String?,
    val resourceId: String?,
    val className: String?,
    val bounds: NodeBounds, // 使用之前定义的 [left, top, right, bottom]
    val displayId: Int
)

@Serializable
data class UiNodeJson(
    val key: String,
    val text: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val contentDesc: String? = null,
    val bounds: NodeBounds,
    val visible: Boolean,
    val checkable: Boolean,
    val checked: Boolean,
    val clickable: Boolean,
    val enabled: Boolean,
    val focusable: Boolean,
    val focused: Boolean,
    val scrollable: Boolean,
    val longClickable: Boolean,
    val password: Boolean,
    val selected: Boolean,
    val drawingOrder: Int,
    val children: List<UiNodeJson> = emptyList()
)

@Serializable
data class NodeBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

@Serializable
data class NotificationTriggerRequest(
    val title: String? = null,
    val content: String? = null,
    val durationSeconds: Long? = null
)