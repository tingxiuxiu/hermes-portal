package com.hermes.portal

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(val success: Boolean, val result: T)

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
