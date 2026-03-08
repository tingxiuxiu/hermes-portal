package com.hermes.portal

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.uiautomator.uiAutomator
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.request.receive
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import kotlin.text.toIntOrNull

/**
 * Service that encapsulates the Ktor HTTP server for UI Automator dumping.
 * This class is decoupled from the test runner to improve maintainability.
 */
class HermesPortalService(
    private val port: Int
) {

    companion object {
        private const val TAG = "AutomatorService"
        private val jsonFormat = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }

    private var server: EmbeddedServer<io.ktor.server.cio.CIOApplicationEngine, io.ktor.server.cio.CIOApplicationEngine.Configuration>? =
        null
    private val stopLatch = CountDownLatch(1)
    private var isStopping = false

    fun start() {
        Log.d(TAG, "Starting HermesPortalService on port $port")

        try {
            server = embeddedServer(CIO, port = port) {
                install(ContentNegotiation) {
                    json(jsonFormat)
                }

                routing {
                    get("/status") {
                        Log.d(TAG, "Received /status request")
                        val response = JSONObject()
                            .put("success", true)
                            .put("result", "Server is running on port $port")
                            .toString()
                        call.respondText(response, ContentType.Application.Json, HttpStatusCode.OK)
                    }

                    get("/stop") {
                        Log.d(TAG, "Received /stop request")
                        val response = JSONObject()
                            .put("success", true)
                            .put("result", "Stopping automator server...")
                            .toString()
                        call.respondText(response, ContentType.Application.Json, HttpStatusCode.OK)

                        if (!isStopping) {
                            isStopping = true
                            stopLatch.countDown()
                        }
                    }

                    route("/displays/{displayId}") {
                        get("/hierarchy") {
                            Log.d(TAG, "Received /v1/displays/{displayId}/hierarchy request")
                            try {
                                val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                                val format = call.request.queryParameters["format"] ?: "json"

                                when (format) {
                                    "xml" -> {
                                        val xml = getXmlHierarchy(displayId)
                                        if (xml != null) {
                                            call.respondBytes(
                                                xml.toByteArray(Charsets.UTF_8),
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
                                        val childrenList = mutableListOf<UiNodeJson>()
                                        uiAutomator {
                                            for (window in windows()) {
                                                if (window.displayId == displayId) {
                                                    val rootNode = window.root
                                                    if (rootNode != null) {
                                                        childrenList.add(
                                                            nodeToJson(
                                                                rootNode,
                                                                "d$displayId-${childrenList.size + 1}"
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        val rootJson = UiNodeJson(
                                            key = "root",
                                            text = null,
                                            resourceId = null,
                                            className = "hierarchy",
                                            packageName = null,
                                            contentDesc = null,
                                            bounds = NodeBounds(0, 0, 0, 0),
                                            visible = true,
                                            checkable = false,
                                            checked = false,
                                            clickable = false,
                                            enabled = true,
                                            focusable = false,
                                            focused = false,
                                            scrollable = false,
                                            longClickable = false,
                                            password = false,
                                            selected = false,
                                            drawingOrder = 0,
                                            children = childrenList
                                        )

                                        call.respond(ApiResponse(true, rootJson))
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in /v1/displays/{displayId}/hierarchy", e)
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    ApiResponse(false, "Error: ${e.message}")
                                )
                            }
                        }

                        get("/capture") {
                            Log.d(TAG, "Received /v1/displays/{displayId}/capture request")
                            try {
                                val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                                var bitmap: Bitmap? = null
                                uiAutomator {
                                    for (window in windows()) {
                                        if (window.displayId == displayId) {
                                            bitmap = device.takeScreenshot()
                                        }
                                    }
                                }
                                if (bitmap != null) {
                                    val outputStream = ByteArrayOutputStream()
                                    bitmap!!.compress(
                                        Bitmap.CompressFormat.PNG,
                                        100,
                                        outputStream
                                    )
                                    val byteArray = outputStream.toByteArray()

                                    call.respondBytes(
                                        byteArray,
                                        ContentType.Image.PNG,
                                        HttpStatusCode.OK
                                    )
                                    bitmap!!.recycle()
                                } else {
                                    Log.e(TAG, "Failed to capture screenshot: bitmap is null")
                                    call.respond(
                                        HttpStatusCode.InternalServerError,
                                        ApiResponse(false, "Failed to capture screenshot")
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in /v1/displays/{displayId}/capture", e)
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    ApiResponse(false, "Capture failed: ${e.message}")
                                )
                            }
                        }

                        route("/uiselector") {
                            post("/locator") {
                                Log.d(TAG, "Received /v1/displays/{displayId}/uiselector/locator request")
                                try {
                                    val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                                    val selector = call.receive<UiSelectorModel>()

                                    var foundNode: UiNodeJson? = null
                                    uiAutomator {
                                        for (window in windows()) {
                                            if (window.displayId == displayId) {
                                                val rootNode = window.root
                                                if (rootNode != null) {
                                                    foundNode = findNodeBySelector(rootNode, selector, displayId)
                                                    if (foundNode != null) {
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    call.respond(ApiResponse(true, foundNode))
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error in /v1/displays/{displayId}/uiselector/locator", e)
                                    call.respond(
                                        HttpStatusCode.InternalServerError,
                                        ApiResponse(false, null as UiNodeJson?)
                                    )
                                }
                            }
                        }

                        route("/actions") {
                            get("/tap") {
                                try {
                                    val displayId = call.parameters["displayId"]?.toIntOrNull() ?: 0
                                    val x = call.request.queryParameters["x"]?.toIntOrNull()
                                    val y = call.request.queryParameters["y"]?.toIntOrNull()
                                    val duration = call.request.queryParameters["duration"]?.toLongOrNull() ?: 100L

                                    if (x == null || y == null) {
                                        call.respond(
                                            HttpStatusCode.BadRequest,
                                            ApiResponse(false, "Missing required parameters: x, y")
                                        )
                                        return@get
                                    }

                                    Log.d(TAG, "GET /displays/$displayId/actions/tap?x=$x&y=$y&duration=$duration")

                                    uiAutomator {
                                        if (duration < 1000L) {
                                            device.click(x, y)
                                        } else {
                                            val steps = duration / 500 * 100
                                            device.swipe(x, y, x, y, steps.toInt())
                                        }
                                    }
                                    call.respond(ApiResponse(true, "action: tap [$x, $y, $duration]"))
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error handling tap: ${e.message}", e)
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        ApiResponse(false, "Error: ${e.message}")
                                    )
                                }
                            }

                        }

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
                                        call.respond(ApiResponse(true, "input text: $text"))
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
                                        call.respond(ApiResponse(true, "clear text"))
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
                    }
                }
            }

            server?.start(wait = false)
            Log.d(TAG, "AutomatorService started successfully on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AutomatorService", e)
            throw e
        }
    }

    fun awaitStop() {
        Log.d(TAG, "Awaiting stop signal...")
        stopLatch.await()
        Log.d(TAG, "Stop signal received")
    }

    fun stop() {
        if (isStopping) {
            Log.d(TAG, "Service is already stopping")
            return
        }

        Log.d(TAG, "Stopping AutomatorService...")
        isStopping = true
        stopLatch.countDown()

        server?.let {
            try {
                it.stop(1000, 2000)
                Log.d(TAG, "AutomatorService stopped successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error while stopping AutomatorService", e)
            }
        }
        server = null
    }

    private fun nodeToJson(
        node: AccessibilityNodeInfo,
        key: String
    ): UiNodeJson {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val childrenList = mutableListOf<UiNodeJson>()
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val childKey = "$key-$i"
                childrenList.add(nodeToJson(child, childKey))
                // child.recycle()
            }
        }

        val isChecked =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val state = node.stateDescription?.toString()?.lowercase()
                state == "checked" || state == "selected" || state == "on"
            } else {
                @Suppress("DEPRECATION")
                node.isChecked
            }

        return UiNodeJson(
            key = key,
            text = node.text?.toString(),
            resourceId = node.viewIdResourceName,
            className = node.className?.toString(),
            packageName = node.packageName?.toString(),
            contentDesc = node.contentDescription?.toString(),
            bounds = NodeBounds(rect.left, rect.top, rect.right, rect.bottom),
            visible = node.isVisibleToUser,
            checkable = node.isCheckable,
            checked = isChecked,
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

    private fun appendAttribute(sb: StringBuilder, name: String, value: String) {
        val escapedValue = value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
        sb.append("$name=\"$escapedValue\" ")
    }

    private fun serializeNode(
        node: AccessibilityNodeInfo,
        sb: StringBuilder,
        key: String
    ) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val className = node.className ?: "node"

        val isChecked =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val state = node.stateDescription?.toString()?.lowercase()
                state == "checked" || state == "selected" || state == "on"
            } else {
                @Suppress("DEPRECATION")
                node.isChecked
            }

        sb.append("<$className ")
        appendAttribute(sb, "key", key)
        appendAttribute(
            sb,
            "bounds",
            "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
        )
        appendAttribute(sb, "text", node.text?.toString() ?: "")
        appendAttribute(sb, "resource-id", node.viewIdResourceName ?: "")
        appendAttribute(sb, "content-desc", node.contentDescription?.toString() ?: "")
        appendAttribute(sb, "class", node.className?.toString() ?: "")
        appendAttribute(sb, "visible", node.isVisibleToUser.toString())
        appendAttribute(sb, "checkable", node.isCheckable.toString())
        appendAttribute(sb, "checked", isChecked.toString())
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

        if (node.childCount > 0) {
            sb.append(">\n")
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val childIndex = "$key-$i"
                    serializeNode(child, sb, childIndex)
                }
            }
            sb.append("  </$className>\n")
        } else {
            sb.append(" />\n")
        }
    }

    private fun getXmlHierarchy(displayId: Int): String? {
        var foundDisplay = false
        val sb = StringBuilder()
        var windowIndex = 1
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>\n")
        sb.append("<hierarchy rotation=\"$displayId\" key=\"0\">\n")
        uiAutomator {
            for (window in uiAutomation.windows) {
                if (window.displayId == displayId) {
                    val rootNode = window.root
                    if (rootNode != null) {
                        serializeNode(rootNode, sb, "win-$windowIndex")
                        windowIndex++
                    }
                    foundDisplay = true
                }
            }
        }
        sb.append("</hierarchy>")
        return if (foundDisplay) sb.toString() else null
    }

    private fun findNodeBySelector(
        rootNode: AccessibilityNodeInfo,
        selector: UiSelectorModel,
        displayId: Int
    ): UiNodeJson? {
        val queue = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
        queue.add(Pair(rootNode, "root"))

        while (queue.isNotEmpty()) {
            val (currentNode, currentKey) = queue.removeAt(0)
            if (matchesSelector(currentNode, selector)) {
                if (selector.child != null) {
                    for (i in 0 until currentNode.childCount) {
                        val childNode = currentNode.getChild(i)
                        if (childNode != null) {
                            val childResult = findNodeBySelector(
                                childNode,
                                selector.child,
                                displayId
                            )
                            if (childResult != null) {
                                return childResult
                            }
                        }
                    }
                    return null
                }
                return nodeToJson(currentNode, currentKey)
            }

            for (i in 0 until currentNode.childCount) {
                val childNode = currentNode.getChild(i)
                if (childNode != null) {
                    queue.add(Pair(childNode, "$currentKey-$i"))
                }
            }
        }
        return null
    }

    private fun matchesSelector(
        node: AccessibilityNodeInfo,
        selector: UiSelectorModel
    ): Boolean {
        if (selector.text != null && node.text?.toString() != selector.text) {
            return false
        }
        if (selector.resourceId != null && node.viewIdResourceName != selector.resourceId) {
            return false
        }
        if (selector.className != null && node.className?.toString() != selector.className) {
            return false
        }
        return true
    }
}
