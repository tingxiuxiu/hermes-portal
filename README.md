# Hermes Portal

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="Hermes Portal Logo" width="120">
</p>

<p align="center">
  <strong>Android Accessibility Service for Test Automation</strong>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#installation">Installation</a> •
  <a href="#api-reference">API Reference</a> •
  <a href="#development">Development</a>
</p>

---

## 📖 Overview

Hermes Portal is an Android application that provides a RESTful HTTP API for UI test automation. It supports two modes of operation: Android Accessibility Service and UI Automator (for environments where accessibility service is not available, such as some automotive systems). It enables automated testing frameworks to interact with Android devices programmatically, including gestures, text input, screen capture, UI hierarchy inspection, and smart UI element selection.

### Key Features

- 🖐️ **Gesture Control** - Tap, long press, swipe, zoom gestures
- 📝 **Text Input** - Input text to focused fields, clear input
- 📸 **Screen Capture** - Real-time screenshot capture
- 🌳 **UI Hierarchy** - JSON/XML format UI tree inspection
- 🔔 **Notification Handling** - Access and trigger notifications
- 🔍 **Smart Search** - Scroll search for UI elements
- 🎯 **UI Selector** - Advanced UI element selection using UiSelectorModel
- 📱 **Multi-Display Support** - Work with multiple displays
- 🚗 **UI Automator Mode** - Support for environments without accessibility service

---

## 🚀 Quick Start

### Prerequisites

- Android device (API 34+, Android 14+)
- USB debugging enabled
- ADB installed on host machine

### Installation

1. **Build and Install**
   ```bash
   # Clone the repository
   git clone https://github.com/your-repo/hermes-portal.git
   cd hermes-portal
   
   # Build and install
   ./gradlew installDebug
   ```

2. **Enable Accessibility Service**
   
   Go to: `Settings → Accessibility → Hermes Portal → Enable`

3. **Start the HTTP Server**
   ```bash
   adb shell content query --uri content://com.hermes.portal.provider/start-service
   ```

4. **Setup Port Forwarding**
   ```bash
   adb forward tcp:8089 tcp:8089
   ```

5. **Test Connection**
   ```bash
   curl http://localhost:8089/v1/health
   ```

---

### UI Automator Mode (Alternative)

For environments where accessibility service is not available (e.g., some automotive systems), use UI Automator mode:

1. **Build and Install Test APK**
   ```bash
   # Clone the repository
   git clone https://github.com/your-repo/hermes-portal.git
   cd hermes-portal
   
   # Build and install test APK
   ./gradlew installDebugAndroidTest
   ```

2. **Run UI Automator Server**
   ```bash
   adb shell am instrument -r -w -e class com.hermes.portal.HermesPortalServerTest#runAutomatorServer com.hermes.portal.test/androidx.test.runner.AndroidJUnitRunner
   ```

3. **Setup Port Forwarding**
   ```bash
   adb forward tcp:8089 tcp:8089
   ```

4. **Test Connection**
   ```bash
   curl http://localhost:8089/v1/health
   ```

---

## 📚 API Reference

### Base URL
```
http://localhost:8089/v1
```

### Response Format
All responses follow this format:
```json
{
  "success": true,
  "result": { ... }
}
```

---

### System APIs

#### Health Check
```http
GET /v1/health
```

**Response:**
```json
{
  "success": true,
  "result": {
    "status": "healthy",
    "version": "0.0.1"
  }
}
```

---

### Display APIs

#### Get Display Status
```http
GET /v1/displays/{displayId}
```

**Response:**
```json
{
  "success": true,
  "result": {
    "displayId": 0,
    "windowStateId": 12345,
    "hasChanged": true
  }
}
```

#### Get UI Hierarchy
```http
GET /v1/displays/{displayId}/hierarchy?format=json
```

**Parameters:**
| Parameter | Type   | Default | Description |
|-----------|--------|---------|-------------|
| format    | string | json    | Output format: `json` or `xml` |

**Example:**
```bash
# Get JSON format
curl http://localhost:8089/v1/displays/0/hierarchy

# Get XML format
curl http://localhost:8089/v1/displays/0/hierarchy?format=xml
```

#### Capture Screenshot
```http
GET /v1/displays/{displayId}/capture
```

**Example:**
```bash
curl http://localhost:8089/v1/displays/0/capture -o screenshot.png
```

---

### Gesture APIs

#### Tap
```http
GET /v1/displays/{displayId}/actions/tap?x={x}&y={y}&duration={duration}
```

**Parameters:**
| Parameter | Type   | Required | Default | Description |
|-----------|--------|----------|---------|-------------|
| x         | float  | Yes      | -       | X coordinate |
| y         | float  | Yes      | -       | Y coordinate |
| duration  | long   | No       | 100     | Duration in ms |

**Example:**
```bash
curl http://localhost:8089/v1/displays/0/actions/tap?x=500&y=1000&duration=100
```

#### Long Press
```http
GET /v1/displays/{displayId}/actions/longPress?x={x}&y={y}&duration={duration}
```

**Parameters:**
| Parameter | Type   | Required | Default | Description |
|-----------|--------|----------|---------|-------------|
| x         | float  | Yes      | -       | X coordinate |
| y         | float  | Yes      | -       | Y coordinate |
| duration  | long   | No       | 1000    | Duration in ms |

**Example:**
```bash
curl http://localhost:8089/v1/displays/0/actions/longPress?x=500&y=1000&duration=2000
```

#### Swipe
```http
GET /v1/displays/{displayId}/actions/swipe?startX={startX}&startY={startY}&endX={endX}&endY={endY}&duration={duration}
```

**Parameters:**
| Parameter | Type   | Required | Default | Description |
|-----------|--------|----------|---------|-------------|
| startX    | float  | Yes      | -       | Start X coordinate |
| startY    | float  | Yes      | -       | Start Y coordinate |
| endX      | float  | Yes      | -       | End X coordinate |
| endY      | float  | Yes      | -       | End Y coordinate |
| duration  | long   | No       | 500     | Duration in ms |

**Example:**
```bash
# Swipe up
curl "http://localhost:8089/v1/displays/0/actions/swipe?startX=500&startY=1500&endX=500&endY=500&duration=300"
```

#### Zoom
```http
GET /v1/displays/{displayId}/actions/zoom?type={type}
```

**Parameters:**
| Parameter | Type   | Required | Default | Description |
|-----------|--------|----------|---------|-------------|
| type      | string | No       | in      | Zoom type: `in` or `out` |

**Example:**
```bash
# Zoom in
curl http://localhost:8089/v1/displays/0/actions/zoom?type=in

# Zoom out
curl http://localhost:8089/v1/displays/0/actions/zoom?type=out
```

#### Custom Zoom (POST)
```http
POST /v1/displays/{displayId}/actions/customZoom
```

**Request Body:**
```json
{
  "displayId": 0,
  "finger1": {
    "start": { "x": 400, "y": 1000 },
    "end": { "x": 200, "y": 1000 }
  },
  "finger2": {
    "start": { "x": 600, "y": 1000 },
    "end": { "x": 800, "y": 1000 }
  },
  "duration": 500
}
```

---

### Input APIs

#### Input Text (POST)
```http
POST /v1/displays/{displayId}/input/text
```

**Request Body:**
```json
{
  "text": "Hello World"
}
```

**Parameters:**
| Parameter | Type   | Required | Description |
|-----------|--------|----------|-------------|
| text      | string | Yes      | Text to input |

**Example:**
```bash
curl -X POST http://localhost:8089/v1/displays/0/input/text \
  -H "Content-Type: application/json" \
  -d '{"text": "Hello World"}'
```

#### Clear Input
```http
GET /v1/displays/{displayId}/input/clear
```

**Example:**
```bash
curl http://localhost:8089/v1/displays/0/input/clear
```

---

### Search APIs

#### Scroll Search (POST)
```http
POST /v1/displays/{displayId}/search
```

**Request Body:**
```json
{
  "displayId": 0,
  "text": "Settings",
  "resourceId": null,
  "className": null,
  "containerResourceId": null,
  "direction": "down",
  "maxRetries": 10
}
```

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| text | string? | Text to search for |
| resourceId | string? | Resource ID to search for |
| className | string? | Class name to search for |
| containerResourceId | string? | Container to scroll |
| direction | string | Scroll direction: `up`, `down`, `left`, `right` |
| maxRetries | int | Maximum scroll attempts |

---

### UI Selector APIs

#### Locate UI Element (POST)
```http
POST /v1/displays/{displayId}/uiselector/locator
```

**Request Body:**
```json
{
  "key": "submit-button",
  "text": "Submit",
  "resourceId": "com.example:id/submit_button",
  "className": "android.widget.Button",
  "child": null
}
```

**Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| key | string | Yes | Unique identifier for the selector |
| text | string? | No | Text content to match |
| resourceId | string? | No | Resource ID to match |
| className | string? | No | Class name to match |
| child | UiSelectorModel? | No | Nested child selector for hierarchical matching |

**Response:**
```json
{
  "success": true,
  "result": {
    "key": "submit-button-0",
    "text": "Submit",
    "resourceId": "com.example:id/submit_button",
    "className": "android.widget.Button",
    "bounds": {
      "left": 400,
      "top": 800,
      "right": 680,
      "bottom": 900
    },
    "clickable": true,
    "enabled": true,
    "focused": false,
    "scrollable": false
  }
}
```

**Example:**
```bash
# Find element by text
curl -X POST http://localhost:8089/v1/displays/0/uiselector/locator \
  -H "Content-Type: application/json" \
  -d '{"key": "my-button", "text": "Submit"}'

# Find element by resourceId
curl -X POST http://localhost:8089/v1/displays/0/uiselector/locator \
  -H "Content-Type: application/json" \
  -d '{"key": "my-button", "resourceId": "com.example:id/submit_button"}'

# Find element with nested child selector
curl -X POST http://localhost:8089/v1/displays/0/uiselector/locator \
  -H "Content-Type: application/json" \
  -d '{
    "key": "container-button",
    "resourceId": "com.example:id/container",
    "child": {
      "key": "nested-button",
      "text": "Submit"
    }
  }'
```

---

### Notification APIs

#### Get Notifications
```http
GET /v1/notifications
```

**Response:**
```json
{
  "success": true,
  "result": [
    {
      "key": "win-1",
      "className": "android.widget.FrameLayout",
      "text": "Notification Title",
      "bounds": { "left": 0, "top": 0, "right": 1080, "bottom": 200 }
    }
  ]
}
```

#### Trigger Test Notification
```http
GET /v1/notifications/trigger?title={title}&content={content}&duration={duration}
```

**Parameters:**
| Parameter | Type   | Required | Default | Description |
|-----------|--------|----------|---------|-------------|
| title     | string | No       | Test Notification | Notification title |
| content   | string | No       | This is a test notification | Notification content |
| duration  | long   | No       | 30 | Duration in seconds |

**Example:**
```bash
curl "http://localhost:8089/v1/notifications/trigger?title=Test&content=Hello&duration=10"
```

---

## 🔧 Development

### Project Structure
```
app/src/main/java/com/hermes/portal/
├── HermesAccessibilityService.kt  # Core accessibility service
├── HermesHttpServer.kt           # HTTP server with Ktor
├── HermesContentProvider.kt      # Content provider for ADB control
├── HermesService.kt              # Foreground service wrapper
├── NotificationHelper.kt         # Notification utilities
├── DataModels.kt                 # Data classes for requests
├── ApiModels.kt                  # API response models
└── ui/                           # UI components (Compose)

app/src/androidTest/java/com/hermes/portal/
├── HermesPortalService.kt        # UI Automator-based service
├── TestDataModels.kt             # Data models for UI Automator mode
├── HermesPortalServerTest.kt     # UI Automator test runner
└── ExampleInstrumentedTest.kt    # Example instrumented test
```

### Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| HTTP Server | Ktor (CIO engine) |
| Serialization | kotlinx.serialization |
| UI Automation (Accessibility Mode) | Android Accessibility Service |
| UI Automation (UI Automator Mode) | AndroidX Test UI Automator |
| Testing Framework | JUnit 4 / AndroidX Test |
| Min SDK | 34 (Android 14) |
| Target SDK | 36 |

### Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test

# Install debug
./gradlew installDebug
```

### Adding New API Endpoints

1. Define request model in `DataModels.kt`
2. Add route in `HermesHttpServer.kt`
3. Implement logic in `HermesAccessibilityService.kt`

---

## 🛠️ Usage Examples

### Python Client Example

```python
import requests

BASE_URL = "http://localhost:8089/v1"

def tap(x, y, duration=100):
    response = requests.get(f"{BASE_URL}/displays/0/actions/tap", params={
        "x": x, "y": y, "duration": duration
    })
    return response.json()

def swipe(start_x, start_y, end_x, end_y, duration=300):
    response = requests.get(f"{BASE_URL}/displays/0/actions/swipe", params={
        "startX": start_x, "startY": start_y,
        "endX": end_x, "endY": end_y,
        "duration": duration
    })
    return response.json()

def input_text(text):
    response = requests.post(f"{BASE_URL}/displays/0/input/text", json={
        "text": text
    })
    return response.json()

def get_hierarchy():
    response = requests.get(f"{BASE_URL}/displays/0/hierarchy")
    return response.json()

def capture_screenshot(output_path="screenshot.png"):
    response = requests.get(f"{BASE_URL}/displays/0/capture")
    with open(output_path, "wb") as f:
        f.write(response.content)
    return output_path

# Example usage
if __name__ == "__main__":
    # Tap on coordinates
    tap(500, 1000)
    
    # Input text
    input_text("Hello World")
    
    # Capture screenshot
    capture_screenshot()
```

### Shell Script Example

```bash
#!/bin/bash
BASE="http://localhost:8089/v1"

# Health check
curl "$BASE/health"

# Tap
curl "$BASE/displays/0/actions/tap?x=500&y=1000"

# Swipe up
curl "$BASE/displays/0/actions/swipe?startX=540&startY=1500&endX=540&endY=500"

# Screenshot
curl "$BASE/displays/0/capture" -o screen.png

# Get UI hierarchy
curl "$BASE/displays/0/hierarchy" | jq .
```

---

## ⚠️ Important Notes

### Permissions Required

| Permission | Purpose |
|------------|---------|
| FOREGROUND_SERVICE | Run HTTP server in background |
| FOREGROUND_SERVICE_DATA_SYNC | Data sync foreground service type |
| POST_NOTIFICATIONS | Show foreground service notification |
| INTERNET | HTTP server networking |
| ACCESS_NETWORK_STATE | Network state detection |

### Security Considerations

- **Local Network Only**: The HTTP server binds to localhost (127.0.0.1)
- **No Authentication**: API has no authentication - use only in trusted environments
- **ADB Required**: Port forwarding needed for host machine access

### Troubleshooting

| Issue | Solution |
|-------|----------|
| Service not starting | Enable accessibility service in Settings |
| Connection refused | Check port forwarding: `adb forward tcp:8089 tcp:8089` |
| Gestures not working | Ensure accessibility service is enabled |
| Empty hierarchy | Wait for app to fully load |

---

## 📋 Service Control Commands

```bash
# Start HTTP server
adb shell content query --uri content://com.hermes.portal.provider/start-service

# Stop HTTP server
adb shell content query --uri content://com.hermes.portal.provider/stop-service

# Check if running
adb shell netstat -tuln | grep 8089
```

---

## 🧪 UI Automator Test

### Run Tests
```bash
adb shell am instrument -r -w -e class com.hermes.portal.AutomatorServerTest#runAutomatorServer com.hermes.portal.test/androidx.test.runner.AndroidJUnitRunner
```

### Test Commands
```bash
# Start server
adb shell content query --uri content://com.hermes.portal.provider/start-service

# Capture screenshot
adb shell content query --uri content://com.hermes.portal.provider/screenshot

# Stop server
adb shell content query --uri content://com.hermes.portal.provider/stop-service
```

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- [Ktor](https://ktor.io/) - HTTP server framework
- [Android Accessibility Service](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) - Core functionality
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) - JSON serialization

---

<p align="center">
  Made with ❤️ for Android Test Automation
</p>
