package com.hermes.portal

import android.content.Context
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import android.util.Log
import java.util.concurrent.CompletableFuture

class HermesHttpServer(private val context: Context) {

    companion object {
        private const val TAG = "HermesHttpServer"
        private const val API_VERSION = "v1"
        private val SCREENSHOT_TIMEOUT_SECONDS = 5L
        private var lastAckStateId = -1L
        
        private fun jsonMap(vararg pairs: Pair<String, Any?>): JsonObject {
            return JsonObject(pairs.associate { (key, value) ->
                key to when (value) {
                    is String -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    is Boolean -> JsonPrimitive(value)
                    is JsonObject -> value
                    else -> JsonPrimitive(value.toString())
                }
            })
        }
    }
    
    private val notificationHelper by lazy { NotificationHelper(context) }
    private var server: ApplicationEngine? = null

    fun start() {
        if (server != null) {
            Log.d(TAG, "Server already running")
            return
        }
        
        server = embeddedServer(CIO, port = 8089) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                })
            }

            routing {
                route("/$API_VERSION") {
                    
                    // ==================== Health Check ====================
                    get("/health") {
                        call.respond(ApiResponse(true, jsonMap(
                            "status" to "healthy",
                            "version" to "0.0.1"
                        )))
                    }

                    // ==================== Window State Id ====================
                    get("/stateId") {
                        call.respond(ApiResponse(true, HermesAccessibilityService.windowStateId.get()))
                    }

                    // ==================== Displays Resource ====================
                    route("/displays/{displayId}") {
                        
                        // GET /v1/displays/{displayId}
                        get {
                            val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                            val currentId = HermesAccessibilityService.windowStateId.get()
                            val hasChanged = currentId != lastAckStateId
                            lastAckStateId = currentId
                            
                            call.respond(ApiResponse(true, jsonMap(
                                "displayId" to displayId,
                                "stateId" to currentId,
                                "hasChanged" to hasChanged
                            )))
                        }

                        // GET /v1/displays/{displayId}/hierarchy
                        get("/hierarchy") {
                            try {
                                val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                                val format = call.request.queryParameters["format"] ?: "json"
                                Log.d(TAG, "GET /displays/$displayId/hierarchy?format=$format")
                                
                                val service = HermesAccessibilityService.instance
                                if (service == null) {
                                    call.respond(
                                        HttpStatusCode.ServiceUnavailable,
                                        ApiResponse(false, "Accessibility service not available")
                                    )
                                    return@get
                                }

                                when (format) {
                                    "xml" -> {
                                        val componentTree = service.getXmlHierarchy(displayId)
                                        if (componentTree != null) {
                                            call.respondBytes(
                                                componentTree.toByteArray(Charsets.UTF_8),
                                                ContentType.Application.Xml,
                                                HttpStatusCode.OK
                                            )
                                        } else {
                                            call.respond(
                                                HttpStatusCode.NotFound,
                                                ApiResponse(false, "Display not found")
                                            )
                                        }
                                    }
                                    else -> {
                                        val componentTree = service.getJsonHierarchy(displayId)
                                        if (componentTree != null) {
                                            call.respond(ApiResponse(true, componentTree))
                                        } else {
                                            call.respond(
                                                HttpStatusCode.NotFound,
                                                ApiResponse(false, "Display not found")
                                            )
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error handling hierarchy: ${e.message}", e)
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    ApiResponse(false, "Error: ${e.message}")
                                )
                            }
                        }

                        // GET /v1/displays/{displayId}/capture
                        get("/capture") {
                            try {
                                val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                                Log.d(TAG, "GET /displays/$displayId/capture")
                                
                                val service = HermesAccessibilityService.instance
                                if (service == null) {
                                    call.respond(
                                        HttpStatusCode.ServiceUnavailable,
                                        ApiResponse(false, "Accessibility service not available")
                                    )
                                    return@get
                                }

                                val future = CompletableFuture<ByteArray>()
                                service.captureScreenAndGetBytes(displayId, future)
                                val result = future.get(SCREENSHOT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                                
                                call.respondBytes(
                                    result,
                                    ContentType.Image.PNG,
                                    HttpStatusCode.OK
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error handling capture: ${e.message}", e)
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    ApiResponse(false, "Capture failed: ${e.message}")
                                )
                            }
                        }

                        // ==================== Actions (Gesture Operations) ====================
                        route("/actions") {
                            
                            // GET /v1/displays/{displayId}/actions/tap?x=100&y=200&duration=100
                            get("/tap") {
                                try {
                                    val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                                    val x = call.request.queryParameters["x"]?.toFloatOrNull()
                                    val y = call.request.queryParameters["y"]?.toFloatOrNull()
                                    val duration = call.request.queryParameters["duration"]?.toLongOrNull() ?: 100L
                                    
                                    if (x == null || y == null) {
                                        call.respond(
                                            HttpStatusCode.BadRequest,
                                            ApiResponse(false, "Missing required parameters: x, y")
                                        )
                                        return@get
                                    }
                                    
                                    Log.d(TAG, "GET /displays/$displayId/actions/tap?x=$x&y=$y&duration=$duration")
                                    
                                    val service = HermesAccessibilityService.instance
                                    if (service == null) {
                                        call.respond(
                                            HttpStatusCode.ServiceUnavailable,
                                            ApiResponse(false, "Accessibility service not available")
                                        )
                                        return@get
                                    }

                                    val success = service.performTap(displayId, x, y, duration)
                                    
                                    if (success) {
                                        call.respond(ApiResponse(true, jsonMap(
                                            "action" to "tap",
                                            "displayId" to displayId,
                                            "x" to x,
                                            "y" to y,
                                            "duration" to duration
                                        )))
                                    } else {
                                        call.respond(
                                            HttpStatusCode.InternalServerError,
                                            ApiResponse(false, "Failed to execute tap action")
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error handling tap: ${e.message}", e)
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        ApiResponse(false, "Error: ${e.message}")
                                    )
                                }
                            }

                            // GET /v1/displays/{displayId}/actions/longPress?x=100&y=200&duration=1000
                            get("/longPress") {
                                try {
                                    val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                                    val x = call.request.queryParameters["x"]?.toFloatOrNull()
                                    val y = call.request.queryParameters["y"]?.toFloatOrNull()
                                    val duration = call.request.queryParameters["duration"]?.toLongOrNull() ?: 1000L
                                    
                                    if (x == null || y == null) {
                                        call.respond(
                                            HttpStatusCode.BadRequest,
                                            ApiResponse(false, "Missing required parameters: x, y")
                                        )
                                        return@get
                                    }
                                    
                                    Log.d(TAG, "GET /displays/$displayId/actions/longPress")
                                    
                                    val service = HermesAccessibilityService.instance
                                    if (service == null) {
                                        call.respond(
                                            HttpStatusCode.ServiceUnavailable,
                                            ApiResponse(false, "Accessibility service not available")
                                        )
                                        return@get
                                    }

                                    val success = service.performLongPress(displayId, x, y, duration)
                                    
                                    if (success) {
                                        call.respond(ApiResponse(true, jsonMap(
                                            "action" to "longPress",
                                            "displayId" to displayId,
                                            "x" to x,
                                            "y" to y,
                                            "duration" to duration
                                        )))
                                    } else {
                                        call.respond(
                                            HttpStatusCode.InternalServerError,
                                            ApiResponse(false, "Failed to execute longPress action")
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error handling longPress: ${e.message}", e)
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        ApiResponse(false, "Error: ${e.message}")
                                    )
                                }
                            }

                            // GET /v1/displays/{displayId}/actions/swipe?startX=100&startY=200&endX=300&endY=400&duration=500
                            get("/swipe") {
                                try {
                                    val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                                    val startX = call.request.queryParameters["startX"]?.toFloatOrNull()
                                    val startY = call.request.queryParameters["startY"]?.toFloatOrNull()
                                    val endX = call.request.queryParameters["endX"]?.toFloatOrNull()
                                    val endY = call.request.queryParameters["endY"]?.toFloatOrNull()
                                    val duration = call.request.queryParameters["duration"]?.toLongOrNull() ?: 500L
                                    
                                    if (startX == null || startY == null || endX == null || endY == null) {
                                        call.respond(
                                            HttpStatusCode.BadRequest,
                                            ApiResponse(false, "Missing required parameters: startX, startY, endX, endY")
                                        )
                                        return@get
                                    }
                                    
                                    Log.d(TAG, "GET /displays/$displayId/actions/swipe")
                                    
                                    val service = HermesAccessibilityService.instance
                                    if (service == null) {
                                        call.respond(
                                            HttpStatusCode.ServiceUnavailable,
                                            ApiResponse(false, "Accessibility service not available")
                                        )
                                        return@get
                                    }

                                    val success = service.performSwipe(displayId, startX, startY, endX, endY, duration)
                                    
                                    if (success) {
                                        call.respond(ApiResponse(true, jsonMap(
                                            "action" to "swipe",
                                            "displayId" to displayId,
                                            "startX" to startX,
                                            "startY" to startY,
                                            "endX" to endX,
                                            "endY" to endY,
                                            "duration" to duration
                                        )))
                                    } else {
                                        call.respond(
                                            HttpStatusCode.InternalServerError,
                                            ApiResponse(false, "Failed to execute swipe action")
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error handling swipe: ${e.message}", e)
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        ApiResponse(false, "Error: ${e.message}")
                                    )
                                }
                            }

                            // GET /v1/displays/{displayId}/actions/zoom?type=in
                            get("/zoom") {
                                try {
                                    val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                                    val type = call.request.queryParameters["type"] ?: "in"
                                    
                                    Log.d(TAG, "GET /displays/$displayId/actions/zoom?type=$type")
                                    
                                    val service = HermesAccessibilityService.instance
                                    if (service == null) {
                                        call.respond(
                                            HttpStatusCode.ServiceUnavailable,
                                            ApiResponse(false, "Accessibility service not available")
                                        )
                                        return@get
                                    }

                                    val success = service.performZoom(displayId, type == "in")
                                    
                                    if (success) {
                                        call.respond(ApiResponse(true, jsonMap(
                                            "action" to "zoom",
                                            "displayId" to displayId,
                                            "type" to type
                                        )))
                                    } else {
                                        call.respond(
                                            HttpStatusCode.InternalServerError,
                                            ApiResponse(false, "Failed to execute zoom action")
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error handling zoom: ${e.message}", e)
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        ApiResponse(false, "Error: ${e.message}")
                                    )
                                }
                            }

                            // POST /v1/displays/{displayId}/actions/customZoom (复杂手势需要POST)
                            post("/customZoom") {
                                try {
                                    val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                                    Log.d(TAG, "POST /displays/$displayId/actions/customZoom")
                                    
                                    val service = HermesAccessibilityService.instance
                                    if (service == null) {
                                        call.respond(
                                            HttpStatusCode.ServiceUnavailable,
                                            ApiResponse(false, "Accessibility service not available")
                                        )
                                        return@post
                                    }

                                    val request = call.receive<CustomZoomRequest>()
                                    val success = service.performCustomGesture(request)
                                    
                                    if (success) {
                                        call.respond(ApiResponse(true, jsonMap(
                                            "action" to "customZoom",
                                            "displayId" to request.displayId
                                        )))
                                    } else {
                                        call.respond(
                                            HttpStatusCode.InternalServerError,
                                            ApiResponse(false, "Failed to execute customZoom action")
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error handling customZoom: ${e.message}", e)
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        ApiResponse(false, "Error: ${e.message}")
                                    )
                                }
                            }
                        }

                        // ==================== Input Operations ====================
                        route("/input") {
                            
                            // POST /v1/displays/{displayId}/input/text
                            post("/text") {
                                try {
                                    val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                                    val request = call.receive<TextInputRequest>()
                                    val text = request.text
                                    
                                    if (text.isEmpty()) {
                                        call.respond(
                                            HttpStatusCode.BadRequest,
                                            ApiResponse(false, "Missing required parameter: text")
                                        )
                                        return@post
                                    }
                                    
                                    Log.d(TAG, "POST /displays/$displayId/input/text")
                                    
                                    val service = HermesAccessibilityService.instance
                                    if (service == null) {
                                        call.respond(
                                            HttpStatusCode.ServiceUnavailable,
                                            ApiResponse(false, "Accessibility service not available")
                                        )
                                        return@post
                                    }

                                    val inputRequest = TextInputRequest(displayId, text)
                                    val success = service.inputText(inputRequest)
                                    
                                    if (success) {
                                        call.respond(ApiResponse(true, jsonMap(
                                            "action" to "inputText",
                                            "displayId" to displayId
                                        )))
                                    } else {
                                        call.respond(
                                            HttpStatusCode.NotFound,
                                            ApiResponse(false, "Target input not found or not editable")
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error handling input text: ${e.message}", e)
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        ApiResponse(false, "Error: ${e.message}")
                                    )
                                }
                            }

                            // GET /v1/displays/{displayId}/input/clear
                            get("/clear") {
                                try {
                                    val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                                    Log.d(TAG, "GET /displays/$displayId/input/clear")
                                    
                                    val service = HermesAccessibilityService.instance
                                    if (service == null) {
                                        call.respond(
                                            HttpStatusCode.ServiceUnavailable,
                                            ApiResponse(false, "Accessibility service not available")
                                        )
                                        return@get
                                    }

                                    val success = service.clearInputText(displayId)
                                    if (success) {
                                        call.respond(ApiResponse(true, jsonMap(
                                            "action" to "clearInput",
                                            "displayId" to displayId
                                        )))
                                    } else {
                                        call.respond(
                                            HttpStatusCode.NotFound,
                                            ApiResponse(false, "Target input not found or not editable")
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error handling input clear: ${e.message}", e)
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        ApiResponse(false, "Error: ${e.message}")
                                    )
                                }
                            }
                        }

                        // ==================== Search Operations ====================
                        
                        // POST /v1/displays/{displayId}/search (复杂搜索需要POST)
                        post("/search") {
                            try {
                                val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                                Log.d(TAG, "POST /displays/$displayId/search")
                                
                                val service = HermesAccessibilityService.instance
                                if (service == null) {
                                    call.respond(
                                        HttpStatusCode.ServiceUnavailable,
                                        ApiResponse(false, "Accessibility service not available")
                                    )
                                    return@post
                                }

                                var request = call.receive<ScrollSearchRequest>()
                                if (request.displayId == 0) {
                                    request = request.copy(displayId = displayId)
                                }
                                
                                val result = service.scrollSearch(request)
                                if (result != null) {
                                    call.respond(ApiResponse(true, result))
                                } else {
                                    call.respond(
                                        HttpStatusCode.NotFound,
                                        ApiResponse(false, "Node not found after search")
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error handling search: ${e.message}", e)
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    ApiResponse(false, "Error: ${e.message}")
                                )
                            }
                        }
                    }

                    // ==================== Notifications Resource ====================
                    route("/notifications") {
                        
                        // GET /v1/notifications
                        get {
                            Log.d(TAG, "GET /notifications")
                            
                            val service = HermesAccessibilityService.instance
                            if (service == null) {
                                call.respond(
                                    HttpStatusCode.ServiceUnavailable,
                                    ApiResponse(false, "Accessibility service not available")
                                )
                                return@get
                            }

                            val windowsList = service.getNotificationWindowsJson()
                            if (windowsList.isNotEmpty()) {
                                call.respond(ApiResponse(true, windowsList))
                            } else {
                                call.respond(
                                    HttpStatusCode.NotFound,
                                    ApiResponse(false, "No active notification windows found")
                                )
                            }
                        }

                        // GET /v1/notifications/trigger?title=Test&content=Hello&duration=30
                        get("/trigger") {
                            try {
                                Log.d(TAG, "GET /notifications/trigger")
                                
                                val title = call.request.queryParameters["title"] ?: "Test Notification"
                                val content = call.request.queryParameters["content"] ?: "This is a test notification"
                                val durationSeconds = call.request.queryParameters["duration"]?.toLongOrNull() ?: 30L

                                notificationHelper.showTestNotification(
                                    title,
                                    content,
                                    durationSeconds * 1000
                                )
                                
                                call.respond(ApiResponse(true, jsonMap(
                                    "action" to "triggerNotification",
                                    "title" to title,
                                    "durationSeconds" to durationSeconds
                                )))
                            } catch (e: Exception) {
                                Log.e(TAG, "Error handling notifications trigger: ${e.message}", e)
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    ApiResponse(false, "Error: ${e.message}")
                                )
                            }
                        }
                    }
                }
            }
        }
        
        server?.start(wait = false)
        Log.d(TAG, "Server started successfully on port 8089")
    }
    
    fun stop() {
        server?.stop(1000, 1000)
        server = null
        Log.d(TAG, "Server stopped successfully")
    }
}
