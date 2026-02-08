package com.hermes.portal

import android.graphics.Bitmap
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.concurrent.CompletableFuture

class HermesHttpServer(private val stopSignal: CompletableDeferred<Unit>) {
    private var lastAckStateId = -1L
    private var TAG = "HermesHttpServer"
    private val SCREENSHOT_TIMEOUT_SECONDS = 5L

    val server = embeddedServer(CIO, port = 8080) {
        // 配置 JSON 插件
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            })
        }

        routing {
            get("/ping") {
                call.respond(ApiResponse(true, "pong"))
            }
            get("/version") {
                call.respond(ApiResponse(true, "0.0.1"))
            }
            // 1. 获取窗口状态（使用Accessibility替代XML）
            get("/window-state") {
                try {
                    Log.d(TAG, "Received request for /window-state")
                    val currentId = HermesAccessibilityService.windowStateId.get()
                    Log.d(TAG, "Current window state ID: $currentId")
                    val hasChanged = currentId != lastAckStateId
                    lastAckStateId = currentId
                    val response = ApiResponse(true, WindowStatus(hasChanged, currentId))
                    Log.d(TAG, "Sending response: $response")
                    call.respond(response)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling /window-status: ${e.message}", e)
                    call.respond(ApiResponse(false, "Error: ${e.message}"))
                }
            }

            // 2. 屏幕缩放
            post("/gesture/easy-zoom") {
                try {
                    Log.d(TAG, "Received request for /gesture/easy-zoom")
                    val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                    val type = call.parameters["type"] ?: "in" // "in" or "out"
                    Log.d(TAG, "Display ID: $displayId, Type: $type")
                    val service = HermesAccessibilityService.instance
                    if (service == null) {
                        Log.e(TAG, "Accessibility service not available")
                        call.respond(ApiResponse(false, "Accessibility service not available"))
                        return@post
                    }
                    val result = service.performZoom(displayId, type == "in")
                    Log.d(TAG, "Gesture dispatched: $result")
                    if (result) {
                        call.respond(ApiResponse(true, "Gesture dispatched successfully"))
                    } else {
                        call.respond(ApiResponse(false, "Failed to dispatch gesture"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling /gesture/easy-zoom: ${e.message}", e)
                    call.respond(ApiResponse(false, "Error: ${e.message}"))
                }
            }

            // 自定义缩放/手势接口
            post("/gesture/zoom") {
                try {
                    Log.d(TAG, "Received request for /gesture/zoom")
                    val request = call.receive<CustomZoomRequest>()
                    val service = HermesAccessibilityService.instance

                    if (service == null) {
                        call.respond(ApiResponse(false, "Accessibility Service not active"))
                        return@post
                    }

                    val success = service.performCustomGesture(request)
                    if (success) {
                        call.respond(
                            ApiResponse(
                                true,
                                "Gesture dispatched to display ${request.displayId}"
                            )
                        )
                    } else {
                        call.respond(ApiResponse(false, "Failed to dispatch gesture"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling /gesture/zoom: ${e.message}", e)
                    call.respond(ApiResponse(false, "Invalid payload: ${e.message}"))
                }
            }

            // 3. 获取组件树 xml
            get("/nodes/xml/{displayId}") {
                try {
                    Log.d(TAG, "Received request for /nodes/xml/{displayId}")
                    val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                    Log.d(TAG, "Display ID: $displayId")
                    val componentTree =
                        HermesAccessibilityService.instance?.getXmlForDisplay(displayId)
                    if (componentTree != null) {
                        val bytes = componentTree.toByteArray(Charsets.UTF_8)
                        call.respondBytes(
                            bytes,
                            ContentType.Application.OctetStream,
                            HttpStatusCode.OK
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling /nodes/xml/{displayId}: ${e.message}", e)
                    call.respond(HttpStatusCode.NotFound,"Display not found or Service offline: ${e.message}")
                }
            }

            // 3. 获取组件树 json
            get("/nodes/json/{displayId}") {
                try {
                    Log.d(TAG, "Received request for /nodes/json/{displayId}")
                    val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                    Log.d(TAG, "Display ID: $displayId")
                    val service = HermesAccessibilityService.instance
                    if (service == null) {
                        Log.e(TAG, "Accessibility service not available")
                        call.respond(ApiResponse(
                            false,
                            "Accessibility service not available"
                        ))
                    }
                    val componentTree =
                        HermesAccessibilityService.instance?.getJsonHierarchy(displayId)
                    if (componentTree != null) {
                        call.respond(ApiResponse(
                            true,
                            componentTree
                        ))
                    } else {
                        call.respond(ApiResponse(
                            false,
                            "Display not found or Accessibility Service offline"
                        ))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling /nodes/json/{displayId}: ${e.message}", e)
                    call.respond(HttpStatusCode.NotFound,"Display not found or Service offline: ${e.message}")
                }
            }

            // 4. 停止服务
            post("/service/stop") {
                try {
                    Log.d(TAG, "Received request for /service/stop")
                    call.respond(ApiResponse(true, "Shutting down..."))
                    stopSignal.complete(Unit)
                    Log.d(TAG, "Stop signal sent")
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling /service/stop: ${e.message}", e)
                    call.respond(ApiResponse(false, "Error: ${e.message}"))
                }
            }

            // 填充文本接口
            post("/input/text") {
                try {
                    Log.d(TAG, "Received request for /input/text")
                    val request = call.receive<TextInputRequest>()
                    val service = HermesAccessibilityService.instance

                    if (service == null) {
                        call.respond(ApiResponse(false, "Accessibility Service not active"))
                        return@post
                    }

                    val success = service.inputText(request)
                    if (success) {
                        call.respond(ApiResponse(true, "Text set successfully"))
                    } else {
                        call.respond(ApiResponse(false, "Target input not found or not editable"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling /input/text: ${e.message}", e)
                    call.respond(ApiResponse(false, "Error: ${e.message}"))
                }
            }

            // 点击操作接口
            post("/gesture/tap") {
                try {
                    Log.d(TAG, "Received request for /gesture/tap")
                    val request = call.receive<TapRequest>()
                    val service = HermesAccessibilityService.instance

                    if (service == null) {
                        call.respond(ApiResponse(false, "Accessibility Service not active"))
                        return@post
                    }

                    val success = service.performTap(request.displayId, request.x, request.y, request.duration)
                    if (success) {
                        call.respond(ApiResponse(true, "Tap gesture dispatched successfully"))
                    } else {
                        call.respond(ApiResponse(false, "Failed to dispatch tap gesture"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling /gesture/tap: ${e.message}", e)
                    call.respond(ApiResponse(false, "Error: ${e.message}"))
                }
            }

            // 长按操作接口
            post("/gesture/long-press") {
                try {
                    Log.d(TAG, "Received request for /gesture/long-press")
                    val request = call.receive<LongPressRequest>()
                    val service = HermesAccessibilityService.instance

                    if (service == null) {
                        call.respond(ApiResponse(false, "Accessibility Service not active"))
                        return@post
                    }

                    val success = service.performLongPress(request.displayId, request.x, request.y, request.duration)
                    if (success) {
                        call.respond(ApiResponse(true, "Long press gesture dispatched successfully"))
                    } else {
                        call.respond(ApiResponse(false, "Failed to dispatch long press gesture"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling /gesture/long-press: ${e.message}", e)
                    call.respond(ApiResponse(false, "Error: ${e.message}"))
                }
            }

            // 清除输入框文本接口
            post("/input/clear") {
                try {
                    Log.d(TAG, "Received request for /input/clear")
                    val request = call.receive<TextInputClearRequest>()
                    val service = HermesAccessibilityService.instance

                    if (service == null) {
                        call.respond(ApiResponse(false, "Accessibility Service not active"))
                        return@post
                    }

                    val success = service.clearInputText(request.displayId)
                    if (success) {
                        call.respond(ApiResponse(true, "Input text cleared successfully"))
                    } else {
                        call.respond(ApiResponse(false, "Target input not found or not editable"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling /input/clear: ${e.message}", e)
                    call.respond(ApiResponse(false, "Error: ${e.message}"))
                }
            }

            // 滑动操作接口
            post("/gesture/swipe") {
                try {
                    Log.d(TAG, "Received request for /gesture/swipe")
                    val request = call.receive<SwipeRequest>()
                    val service = HermesAccessibilityService.instance

                    if (service == null) {
                        call.respond(ApiResponse(false, "Accessibility Service not active"))
                        return@post
                    }

                    val success = service.performSwipe(
                        request.displayId, 
                        request.startX, 
                        request.startY, 
                        request.endX, 
                        request.endY, 
                        request.duration
                    )
                    if (success) {
                        call.respond(ApiResponse(true, "Swipe gesture dispatched successfully"))
                    } else {
                        call.respond(ApiResponse(false, "Failed to dispatch swipe gesture"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling /gesture/swipe: ${e.message}", e)
                    call.respond(ApiResponse(false, "Error: ${e.message}"))
                }
            }

            // 滚动查询接口
            post("/action/scroll-search") {
                try {
                    val req = call.receive<ScrollSearchRequest>()
                    val res = HermesAccessibilityService.instance?.scrollSearch(req)
                    call.respond(ApiResponse(res != null, res))
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling /action/scroll-search: ${e.message}", e)
                    call.respond(ApiResponse(false, "Error: ${e.message}"))
                }
            }
            
            // 3.1 截图 (查询参数模式)
            get("/screenshot/{displayId}") {
                try {
                    Log.d(TAG, "Received request for /screenshot")
                    val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                    val service = HermesAccessibilityService.instance
                    if (service == null) {
                        Log.e(TAG, "Accessibility service not available")
                        call.respond(HttpStatusCode.NotFound, "Accessibility service not available")
                        return@get
                    }
                    val future = CompletableFuture<ByteArray>()
                    service.captureScreenAndGetBytes(displayId, future)
                    var result = future.get(SCREENSHOT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                    call.respondBytes(
                        result,
                        ContentType.Image.PNG,
                        HttpStatusCode.OK
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling /screenshot: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Capture Failed: ${e.message}"
                    )
                }
            }
        }
    }
}
