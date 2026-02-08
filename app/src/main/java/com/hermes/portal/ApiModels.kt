package com.hermes.portal

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(val success: Boolean, val result: T)

@Serializable
data class ZoomRequest(val scale: Float, val x: Float, val y: Float)

@Serializable
data class WindowStatus(val changed: Boolean, val stateId: Long)
