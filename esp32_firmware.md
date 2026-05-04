# DigiPlay – IoT-Based Remote Digital Name Display System
# `esp32_firmware.md` — ESP32 Device Firmware

---

## Table of Contents

1. [Device Overview](#device-overview)
2. [Hardware Components](#hardware-components)
3. [Circuit Diagram](#circuit-diagram)
4. [Firmware Architecture](#firmware-architecture)
5. [WiFi Auto-Connect System](#wifi-auto-connect-system)
6. [Server Communication Logic](#server-communication-logic)
7. [Approval-Only Update Logic](#approval-only-update-logic)
8. [Display Handling](#display-handling)
9. [Complete Firmware Code](#complete-firmware-code)
10. [Flash & Deployment](#flash--deployment)
11. [Power & Memory Optimization](#power--memory-optimization)
12. [Security Design](#security-design)
13. [Improvements & Future Enhancements](#improvements--future-enhancements)
14. [How to Convert to PDF](#how-to-convert-to-pdf)

---

## 1. Device Overview

Each DigiPlay ESP32 device is an autonomous display unit that:
- Connects to WiFi automatically (no manual credential updates required)
- Periodically polls the DigiPlay server for **approved** content updates
- Displays the current approved message on an OLED/LCD/E-Paper screen
- Ignores all `PENDING` or `REJECTED` requests
- Falls back to the last known approved message if the server is unreachable

**Core Constraints:**
- Firmware must be lightweight (ESP32 has ~320KB RAM)
- No unnecessary libraries or heavy frameworks
- Efficient polling to conserve power
- Must not accept or display unapproved content under any circumstances

---

## 2. Hardware Components

### 2.1 Component List

| Component | Specification | Purpose |
|---|---|---|
| Microcontroller | ESP32 (WROOM-32 or WROVER) | Main controller + WiFi |
| Display (Option A) | SSD1306 OLED 0.96" (128x64 I2C) | Primary display, low power |
| Display (Option B) | SH1106 OLED 1.3" (128x64 I2C) | Slightly larger, same protocol |
| Display (Option C) | E-Paper 2.9" (SPI, e.g. GDEW029T5) | Ultra-low power, sunlight readable |
| Power Supply | 5V USB or 3.7V LiPo + TP4056 | Flexible power source |
| Reset Button | Tactile push button | WiFi config reset trigger |
| Status LED | 3mm LED + 330Ω resistor | WiFi/connection status indicator |
| Capacitor | 100µF electrolytic | Power rail stabilization |

### 2.2 Recommended Display Choice

| Display | Power Draw | Readability | Refresh Speed | Recommended For |
|---|---|---|---|---|
| SSD1306 OLED | ~20mA | Good indoors | Instant | Standard use |
| E-Paper | ~0mA static | Excellent | ~2 seconds | Battery-powered, outdoor |

For most DigiPlay deployments, **SSD1306 OLED** is recommended: instant refresh, widely available, and well-supported.

---

## 3. Circuit Diagram

### 3.1 ESP32 + SSD1306 OLED (I2C)

```
                        ┌──────────────────────────────────────┐
                        │         ESP32 WROOM-32               │
                        │                                      │
    3.3V ──────────────►│ 3V3          GPIO 21 (SDA)──────────►│──► SSD1306 SDA
    GND  ──────────────►│ GND          GPIO 22 (SCL)──────────►│──► SSD1306 SCL
                        │                                      │
    5V → 3.3V Reg ─────►│ VIN          GPIO 2  (LED)──────────►│──► LED ──► 330Ω ──► GND
                        │                                      │
    Reset Button ──────►│ GPIO 0 (BOOT)                        │
                        └──────────────────────────────────────┘

SSD1306 OLED Wiring:
┌──────────────┐
│  SSD1306     │
│  VCC  ───────┼──► 3.3V
│  GND  ───────┼──► GND
│  SDA  ───────┼──► GPIO 21 (ESP32)
│  SCL  ───────┼──► GPIO 22 (ESP32)
└──────────────┘
```

### 3.2 ESP32 + E-Paper (SPI)

```
                        ┌──────────────────────────────────────┐
                        │         ESP32 WROOM-32               │
                        │                                      │
    3.3V ──────────────►│ 3V3          GPIO 18 (SCK) ─────────►│──► EPD CLK
    GND  ──────────────►│ GND          GPIO 23 (MOSI)─────────►│──► EPD DIN
                        │              GPIO  5 (CS)  ─────────►│──► EPD CS
                        │              GPIO  4 (DC)  ─────────►│──► EPD DC
                        │              GPIO  2 (RST) ─────────►│──► EPD RST
                        │              GPIO 15 (BUSY)◄─────────│──► EPD BUSY
                        └──────────────────────────────────────┘
```

---

## 4. Firmware Architecture

### 4.1 Main Loop Flow

```
 BOOT / RESET
      │
      ▼
 [ Load from EEPROM/Preferences ]
   - Device ID
   - Device Token
   - Last approved content
      │
      ▼
 [ WiFi Auto-Connect ]
   - Try stored SSIDs
   - If fail → WiFiManager captive portal
      │
      ├─── WiFi Failed ──────────────────────────────────────────┐
      │                                                          │
      ▼                                                          ▼
 [ Connected ]                                        [ Display last content ]
      │                                                          │
      ▼                                                    [ Deep sleep 60s ]
 [ Poll Server: GET /api/esp32/{id}/content ]               [ Retry WiFi ]
      │
      ├─── HTTP Error / Timeout ────────────────────────────────►│ Keep current display
      │
      ├─── 200 OK: checksum == stored checksum ────────────────►│ No update needed
      │
      └─── 200 OK: checksum != stored checksum ────────────────►│ Update display
                                                                 │ Save to Preferences
                                                                 │
                                                      [ Wait POLL_INTERVAL ]
                                                                 │
                                                      [ Loop back to Poll ]
```

### 4.2 State Machine

```
States:
  BOOT → WIFI_CONNECTING → SERVER_POLLING → DISPLAY_UPDATING → SLEEPING

WIFI_CONNECTING:
  - Attempt known SSIDs (3 tries each)
  - If all fail → start WiFiManager portal (timeout 3 min)
  - If portal timeout → SLEEPING (retry in 60s)

SERVER_POLLING:
  - Send GET with Device Token header
  - Parse JSON response
  - Compare checksum with stored value
  - If mismatch → DISPLAY_UPDATING
  - If match → SLEEPING

SLEEPING:
  - Delay(POLL_INTERVAL_MS)   // or deep-sleep for battery devices
  - Wake → SERVER_POLLING
```

---

## 5. WiFi Auto-Connect System

### 5.1 Approach Comparison

| Approach | Description | Pros | Cons |
|---|---|---|---|
| **WiFiManager** (chosen) | Captive portal to configure SSID | No hardcoding, user-friendly setup | Requires one-time physical access |
| Pre-stored Multi-SSID | Array of SSIDs in firmware | Fast auto-switch between networks | Credentials in firmware (security risk) |
| Open Network Fallback | Connect to any open network | Zero config | Very insecure; unreliable |
| Bluetooth Provisioning | Send credentials over BLE | Elegant UX | Adds BLE complexity, more power |

**Chosen Approach: WiFiManager Library**

WiFiManager creates a temporary WiFi access point and serves a captive portal web page. The user connects to the ESP32's AP once, enters WiFi credentials, and the device stores them in flash. On all subsequent boots, it auto-connects using stored credentials.

### 5.2 WiFiManager Implementation

```cpp
// WiFi auto-connect using WiFiManager
#include <WiFiManager.h>

WiFiManager wm;

void setupWiFi() {
    Serial.println("[WiFi] Starting auto-connect...");

    // Set portal timeout to 3 minutes
    wm.setConfigPortalTimeout(180);

    // Custom AP name for portal (unique per device)
    String apName = "DigiPlay-" + String((uint32_t)ESP.getEfuseMac(), HEX);

    // Try to connect using stored credentials
    // If fails, open configuration portal
    bool connected = wm.autoConnect(apName.c_str(), "digiplay123");

    if (!connected) {
        Serial.println("[WiFi] Config portal timed out. Restarting...");
        delay(3000);
        ESP.restart();
    }

    Serial.println("[WiFi] Connected!");
    Serial.print("[WiFi] IP Address: ");
    Serial.println(WiFi.localIP());
}

// Reset WiFi credentials when boot button held for 3 seconds
void checkResetButton() {
    if (digitalRead(0) == LOW) {
        delay(3000);
        if (digitalRead(0) == LOW) {
            Serial.println("[WiFi] Resetting credentials...");
            wm.resetSettings();
            ESP.restart();
        }
    }
}
```

### 5.3 WiFi Reconnection on Drop

```cpp
void ensureWiFiConnected() {
    if (WiFi.status() == WL_CONNECTED) return;

    Serial.println("[WiFi] Reconnecting...");
    WiFi.disconnect();
    WiFi.reconnect();

    int retries = 0;
    while (WiFi.status() != WL_CONNECTED && retries < 20) {
        delay(500);
        retries++;
        Serial.print(".");
    }

    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("\n[WiFi] Reconnect failed. Restarting...");
        delay(3000);
        ESP.restart();
    }

    Serial.println("\n[WiFi] Reconnected.");
}
```

---

## 6. Server Communication Logic

### 6.1 HTTPS Communication

```cpp
#include <HTTPClient.h>
#include <WiFiClientSecure.h>

// Server root CA certificate (copy from your server's SSL cert)
const char* ROOT_CA = R"(
-----BEGIN CERTIFICATE-----
... your CA certificate here ...
-----END CERTIFICATE-----
)";

String fetchApprovedContent(String deviceId, String deviceToken) {
    WiFiClientSecure client;
    client.setCACert(ROOT_CA);

    HTTPClient http;
    String url = String(SERVER_BASE_URL) + "/api/esp32/" + deviceId + "/content";

    http.begin(client, url);
    http.addHeader("X-Device-Token", deviceToken);
    http.addHeader("Content-Type", "application/json");

    int httpCode = http.GET();
    String payload = "";

    if (httpCode == HTTP_CODE_OK) {
        payload = http.getString();
        Serial.println("[HTTP] Response: " + payload);
    } else if (httpCode == 304) {
        Serial.println("[HTTP] No change (304). Keeping current display.");
    } else {
        Serial.printf("[HTTP] Error: %d\n", httpCode);
    }

    http.end();
    return payload;
}
```

### 6.2 JSON Parsing (ArduinoJson)

```cpp
#include <ArduinoJson.h>

struct ContentResponse {
    String content;
    String checksum;
    bool valid;
};

ContentResponse parseContentResponse(String json) {
    ContentResponse result = {"", "", false};

    StaticJsonDocument<512> doc;
    DeserializationError err = deserializeJson(doc, json);

    if (err) {
        Serial.println("[JSON] Parse error: " + String(err.c_str()));
        return result;
    }

    result.content  = doc["content"].as<String>();
    result.checksum = doc["checksum"].as<String>();
    result.valid    = true;

    return result;
}
```

---

## 7. Approval-Only Update Logic

This is the **most critical** section of the firmware. The ESP32 **must never** apply an unapproved update. The server API guarantees this by only serving `APPROVED` content from the `/api/esp32/{id}/content` endpoint, but the firmware also validates by comparing checksums.

```cpp
#include <Preferences.h>

Preferences preferences;

// Load stored values
String loadStoredContent() {
    preferences.begin("digiplay", true); // read-only
    String content = preferences.getString("content", "Welcome!");
    preferences.end();
    return content;
}

String loadStoredChecksum() {
    preferences.begin("digiplay", true);
    String checksum = preferences.getString("checksum", "");
    preferences.end();
    return checksum;
}

// Save approved content
void saveApprovedContent(String content, String checksum) {
    preferences.begin("digiplay", false); // read-write
    preferences.putString("content",  content);
    preferences.putString("checksum", checksum);
    preferences.end();
    Serial.println("[Storage] Saved approved content: " + content);
}

// Core approval check & update logic
void checkAndUpdate() {
    String json = fetchApprovedContent(DEVICE_ID, DEVICE_TOKEN);

    if (json.isEmpty()) {
        Serial.println("[Update] No response from server. Keeping current display.");
        return;
    }

    ContentResponse resp = parseContentResponse(json);

    if (!resp.valid) {
        Serial.println("[Update] Invalid response. Keeping current display.");
        return;
    }

    String storedChecksum = loadStoredChecksum();

    if (resp.checksum == storedChecksum) {
        Serial.println("[Update] No change in approved content.");
        return;
    }

    // New approved content available — update display
    Serial.println("[Update] New approved content: " + resp.content);
    saveApprovedContent(resp.content, resp.checksum);
    displayContent(resp.content);
}
```

---

## 8. Display Handling

### 8.1 SSD1306 OLED Display

```cpp
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET   -1  // No reset pin used
#define OLED_ADDR    0x3C

Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

void setupDisplay() {
    if (!display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDR)) {
        Serial.println("[Display] SSD1306 init failed!");
        for(;;); // Halt if no display found
    }
    display.clearDisplay();
    display.setTextColor(SSD1306_WHITE);
    display.display();
}

void displayContent(String text) {
    display.clearDisplay();

    // Determine font size based on content length
    if (text.length() <= 20) {
        display.setTextSize(2);
        display.setFont(nullptr);
    } else {
        display.setTextSize(1);
        display.setFont(nullptr);
    }

    // Word-wrap and center text
    display.setCursor(0, 16);
    display.setTextWrap(true);
    display.println(text);
    display.display();

    Serial.println("[Display] Showing: " + text);
}

void displayStatus(String line1, String line2 = "") {
    display.clearDisplay();
    display.setTextSize(1);
    display.setCursor(0, 0);
    display.println(line1);
    if (!line2.isEmpty()) {
        display.setCursor(0, 20);
        display.println(line2);
    }
    display.display();
}
```

### 8.2 Status LED

```cpp
#define LED_PIN 2

void ledBlink(int times, int delayMs = 200) {
    for (int i = 0; i < times; i++) {
        digitalWrite(LED_PIN, HIGH);
        delay(delayMs);
        digitalWrite(LED_PIN, LOW);
        delay(delayMs);
    }
}

// 1 blink = WiFi connecting
// 2 blinks = WiFi connected
// 3 blinks = Content updated
// Solid = Error
```

---

## 9. Complete Firmware Code

```cpp
// ============================================================
// DigiPlay ESP32 Firmware v1.0
// Language: Arduino (C++)
// Target: ESP32 WROOM-32 / WROVER
// Display: SSD1306 OLED 128x64 (I2C)
// ============================================================

#include <WiFiManager.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>
#include <Adafruit_SSD1306.h>
#include <Adafruit_GFX.h>
#include <Wire.h>
#include <Preferences.h>

// ─── Configuration ───────────────────────────────────────────
const char* SERVER_BASE_URL = "https://your-server.com";
const char* DEVICE_ID       = "550e8400-e29b-41d4-a716-446655440000";
const char* DEVICE_TOKEN    = "your-device-raw-token";
const int   POLL_INTERVAL   = 30000; // 30 seconds

const char* ROOT_CA = R"(
-----BEGIN CERTIFICATE-----
... paste your server root CA here ...
-----END CERTIFICATE-----
)";

// ─── Hardware Pins ───────────────────────────────────────────
#define LED_PIN      2
#define RESET_PIN    0
#define OLED_SDA     21
#define OLED_SCL     22

// ─── Display Setup ───────────────────────────────────────────
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, -1);

// ─── Globals ─────────────────────────────────────────────────
Preferences preferences;
WiFiManager wm;
String currentContent  = "Initializing...";
String currentChecksum = "";

// ─── Display Functions ───────────────────────────────────────
void setupDisplay() {
    Wire.begin(OLED_SDA, OLED_SCL);
    if (!display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) {
        Serial.println("[Display] FATAL: SSD1306 not found!");
        while (true) delay(1000);
    }
    display.clearDisplay();
    display.setTextColor(SSD1306_WHITE);
    display.display();
}

void displayContent(String text) {
    display.clearDisplay();
    display.setTextWrap(true);
    display.setCursor(0, (text.length() <= 20) ? 20 : 10);
    display.setTextSize((text.length() <= 20) ? 2 : 1);
    display.println(text);
    display.display();
}

void displayStatus(String msg) {
    display.clearDisplay();
    display.setTextSize(1);
    display.setCursor(0, 28);
    display.println(msg);
    display.display();
}

// ─── Preferences (EEPROM equivalent) ─────────────────────────
void loadStoredData() {
    preferences.begin("digiplay", true);
    currentContent  = preferences.getString("content",  "Welcome!");
    currentChecksum = preferences.getString("checksum", "");
    preferences.end();
    Serial.println("[Prefs] Loaded: " + currentContent);
}

void saveData(String content, String checksum) {
    preferences.begin("digiplay", false);
    preferences.putString("content",  content);
    preferences.putString("checksum", checksum);
    preferences.end();
}

// ─── WiFi ────────────────────────────────────────────────────
void setupWiFi() {
    displayStatus("Connecting WiFi...");
    ledBlink(1);

    wm.setConfigPortalTimeout(180);
    String apName = "DigiPlay-" + String((uint32_t)ESP.getEfuseMac(), HEX);

    if (!wm.autoConnect(apName.c_str(), "digiplay123")) {
        Serial.println("[WiFi] Portal timeout. Restarting...");
        displayStatus("WiFi Failed. Retry...");
        delay(5000);
        ESP.restart();
    }

    Serial.println("[WiFi] Connected: " + WiFi.localIP().toString());
    displayStatus("WiFi OK!");
    ledBlink(2);
    delay(1000);
}

void ensureConnected() {
    if (WiFi.status() == WL_CONNECTED) return;
    int tries = 0;
    WiFi.reconnect();
    while (WiFi.status() != WL_CONNECTED && tries++ < 20) delay(500);
    if (WiFi.status() != WL_CONNECTED) ESP.restart();
}

// ─── LED ─────────────────────────────────────────────────────
void ledBlink(int times, int ms = 200) {
    for (int i = 0; i < times; i++) {
        digitalWrite(LED_PIN, HIGH); delay(ms);
        digitalWrite(LED_PIN, LOW);  delay(ms);
    }
}

// ─── HTTP Fetch ──────────────────────────────────────────────
String fetchFromServer() {
    WiFiClientSecure client;
    client.setCACert(ROOT_CA);

    HTTPClient http;
    String url = String(SERVER_BASE_URL) + "/api/esp32/" + DEVICE_ID + "/content";
    http.begin(client, url);
    http.addHeader("X-Device-Token", DEVICE_TOKEN);

    int code = http.GET();
    String payload = (code == HTTP_CODE_OK) ? http.getString() : "";

    if (code != HTTP_CODE_OK && code != 304) {
        Serial.printf("[HTTP] Error code: %d\n", code);
    }

    http.end();
    return payload;
}

// ─── Core Update Logic ────────────────────────────────────────
void checkAndUpdate() {
    ensureConnected();

    String json = fetchFromServer();
    if (json.isEmpty()) {
        Serial.println("[Update] No data. Keeping display.");
        return;
    }

    StaticJsonDocument<512> doc;
    if (deserializeJson(doc, json) != DeserializationError::Ok) {
        Serial.println("[Update] JSON error.");
        return;
    }

    String newContent  = doc["content"].as<String>();
    String newChecksum = doc["checksum"].as<String>();

    if (newChecksum == currentChecksum) {
        Serial.println("[Update] No change.");
        return;
    }

    Serial.println("[Update] New approved content: " + newContent);
    currentContent  = newContent;
    currentChecksum = newChecksum;

    saveData(currentContent, currentChecksum);
    displayContent(currentContent);
    ledBlink(3);
}

// ─── Setup ───────────────────────────────────────────────────
void setup() {
    Serial.begin(115200);
    pinMode(LED_PIN,   OUTPUT);
    pinMode(RESET_PIN, INPUT_PULLUP);

    // Check if reset button held at boot
    if (digitalRead(RESET_PIN) == LOW) {
        delay(3000);
        if (digitalRead(RESET_PIN) == LOW) {
            Serial.println("[Boot] Reset button held. Clearing WiFi...");
            wm.resetSettings();
            ESP.restart();
        }
    }

    setupDisplay();
    loadStoredData();
    displayContent(currentContent);   // Show last known content immediately
    setupWiFi();
}

// ─── Main Loop ───────────────────────────────────────────────
void loop() {
    checkAndUpdate();
    delay(POLL_INTERVAL);
}
```

---

## 10. Flash & Deployment

### 10.1 Arduino IDE Setup

1. Install Arduino IDE 2.x
2. Add ESP32 board package:
   - Go to **File → Preferences → Additional Board Manager URLs**
   - Add: `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
3. Go to **Tools → Board Manager** → search and install `esp32` by Espressif

### 10.2 Required Libraries

Install via **Tools → Manage Libraries:**

| Library | Version | Source |
|---|---|---|
| WiFiManager | 2.0.17 | tzapu/WiFiManager |
| Adafruit SSD1306 | 2.5.x | Adafruit |
| Adafruit GFX Library | 1.11.x | Adafruit |
| ArduinoJson | 6.x | Benoit Blanchon |

### 10.3 Build & Flash Settings

```
Board:          ESP32 Dev Module
Upload Speed:   921600
CPU Frequency:  240MHz
Flash Mode:     QIO
Flash Size:     4MB (32Mb)
Partition:      Default 4MB with spiffs
Port:           (select your COM/ttyUSB port)
```

```bash
# Flash via CLI (esptool)
esptool.py --chip esp32 --port /dev/ttyUSB0 --baud 921600 \
  write_flash -z 0x1000 firmware.bin

# Or using arduino-cli
arduino-cli compile --fqbn esp32:esp32:esp32 digiplay_firmware/
arduino-cli upload  --fqbn esp32:esp32:esp32 --port /dev/ttyUSB0 digiplay_firmware/
```

### 10.4 First-Time Device Provisioning

1. Flash firmware to ESP32
2. Power on device — it will show **"Connecting WiFi..."** and start the captive portal
3. On your phone/laptop, connect to WiFi network named `DigiPlay-XXXXXXXX`
4. A browser window opens automatically → enter your WiFi SSID and password
5. Device saves credentials and restarts
6. Device connects to WiFi and polls server

### 10.5 Provisioning Device Token & ID

Before deploying, update these constants in `firmware.ino`:

```cpp
const char* DEVICE_ID    = "your-device-uuid-from-server";
const char* DEVICE_TOKEN = "your-raw-token-from-registration";
```

> For production, consider storing these in a separate `config.h` that is not committed to git. For fleet deployments, implement OTA-based configuration.

---

## 11. Power & Memory Optimization

### 11.1 Memory Usage

| Resource | Usage | Limit |
|---|---|---|
| Flash (Sketch) | ~400-600 KB | 1.2 MB (default partition) |
| RAM (Heap) | ~80-120 KB | ~320 KB |
| ArduinoJson buffer | 512 bytes (StaticJsonDocument) | Adjust as needed |

### 11.2 Deep Sleep Mode (Battery Optimization)

For battery-powered devices (LiPo), replace `delay(POLL_INTERVAL)` with deep sleep:

```cpp
#define POLL_INTERVAL_SECS 30

void goToSleep() {
    Serial.println("[Sleep] Entering deep sleep for " +
                   String(POLL_INTERVAL_SECS) + "s...");
    display.ssd1306_command(SSD1306_DISPLAYOFF); // Turn off OLED
    WiFi.disconnect(true);
    esp_deep_sleep(POLL_INTERVAL_SECS * 1000000ULL); // microseconds
}

// In loop():
void loop() {
    checkAndUpdate();
    goToSleep();  // Replace delay() with this for battery mode
}
```

**Power draw comparison:**
| Mode | Current Draw |
|---|---|
| Active WiFi + OLED on | ~160 mA |
| Deep sleep | ~10 µA |
| With E-Paper (no power needed) | ~10 µA (display holds image) |

### 11.3 CPU Frequency Optimization

```cpp
// Reduce CPU speed for lower power (still runs fine for polling)
setCpuFrequencyMhz(80);  // Default 240MHz → 80MHz saves ~30mA
```

---

## 12. Security Design

### 12.1 Device Authentication

- Each device has a unique `DEVICE_TOKEN` provisioned at registration
- The raw token is stored only in the device firmware
- The server stores only the SHA256 hash of the token
- On each API call, the raw token is sent in the `X-Device-Token` header
- Server hashes it and compares to stored hash before serving content

### 12.2 HTTPS Certificate Validation

```cpp
// Always validate the server certificate — never use:
// client.setInsecure();  ← NEVER DO THIS IN PRODUCTION

// Instead, embed the root CA:
client.setCACert(ROOT_CA);
```

To get your server's root CA:
```bash
openssl s_client -connect your-server.com:443 -showcerts </dev/null 2>/dev/null \
  | openssl x509 -outform PEM
```

### 12.3 Content Integrity Check

The server includes a `checksum` (CRC32 or MD5 of content) in every response. The ESP32 stores the last checksum and only updates the display if it changes. This prevents re-rendering unchanged content and provides a basic integrity check.

### 12.4 Replay Attack Prevention

- Each API call is fresh (no cached credentials sent with stale timestamps)
- HTTPS TLS layer prevents replay at the transport level
- Device tokens are static but HTTPS + server-side hashing makes interception useless

### 12.5 Security Checklist

- [ ] Server root CA embedded in firmware (no `setInsecure()`)
- [ ] DEVICE_TOKEN stored in firmware (not in public repository)
- [ ] Device token is unique per device (not shared)
- [ ] Firmware uses HTTPS for all communication
- [ ] Reset button protected (requires 3s hold, not accidental press)
- [ ] Stale content shown on server failure (no blank/error screen)

---

## 13. Improvements & Future Enhancements

| Enhancement | Description | Priority |
|---|---|---|
| MQTT Support | Replace HTTP polling with MQTT subscribe for instant push updates | High |
| OTA Firmware Update | Server-triggered firmware updates via `Update.h` or ArduinoOTA | High |
| E-Paper Display | Ultra-low power; display persists without power; ideal for battery devices | Medium |
| Bluetooth Provisioning | Replace WiFiManager with BLE provisioning via mobile app | Medium |
| Multi-SSID Fallback | Store up to 3 SSIDs; auto-switch on drop | Medium |
| Fleet Management | Server pushes config/token updates to groups of devices | Medium |
| Local Cache Encryption | Encrypt stored content in Preferences using device key | Low |
| Watchdog Timer | Hardware WDT to restart device if firmware hangs | High |
| Status Reporting | Device sends heartbeat (uptime, WiFi RSSI) to server | Low |
| Custom Fonts | Use GFX font library for beautiful typography on display | Low |

### 13.1 MQTT Implementation Preview

```cpp
// Replace HTTP polling with MQTT (PubSubClient library)
#include <PubSubClient.h>

WiFiClientSecure secureClient;
PubSubClient mqtt(secureClient);

const char* MQTT_BROKER = "mqtt.your-server.com";
const int   MQTT_PORT   = 8883;
String      TOPIC       = "digiplay/devices/" + String(DEVICE_ID) + "/content";

void mqttCallback(char* topic, byte* payload, unsigned int length) {
    String msg = "";
    for (int i = 0; i < length; i++) msg += (char)payload[i];

    StaticJsonDocument<512> doc;
    deserializeJson(doc, msg);

    String newContent  = doc["content"].as<String>();
    String newChecksum = doc["checksum"].as<String>();

    if (newChecksum != currentChecksum) {
        currentContent  = newContent;
        currentChecksum = newChecksum;
        saveData(currentContent, currentChecksum);
        displayContent(currentContent);
    }
}

// In setup():
mqtt.setServer(MQTT_BROKER, MQTT_PORT);
mqtt.setCallback(mqttCallback);
mqtt.connect(DEVICE_ID, DEVICE_TOKEN, "");
mqtt.subscribe(TOPIC.c_str());

// In loop():
mqtt.loop();  // No delay needed — event-driven!
```

---

## 14. How to Convert to PDF

### Using Pandoc (Recommended)

```bash
# Install pandoc + LaTeX
sudo apt install pandoc texlive-xetex

# Basic conversion
pandoc esp32_firmware.md -o esp32_firmware.pdf

# High-quality conversion
pandoc esp32_firmware.md \
  --pdf-engine=xelatex \
  -V geometry:margin=1in \
  -V fontsize=11pt \
  -V mainfont="DejaVu Sans" \
  -V monofont="DejaVu Sans Mono" \
  --toc \
  --toc-depth=2 \
  --highlight-style=tango \
  -o esp32_firmware.pdf
```

### Using VS Code

1. Install extension: **Markdown PDF** (yzane.markdown-pdf)
2. Open `esp32_firmware.md`
3. Right-click → **Markdown PDF: Export (pdf)**

### Using Typora

1. Open `esp32_firmware.md` in Typora
2. Go to **File → Export → PDF**
3. Select a theme (e.g. GitHub) and export

---

*DigiPlay ESP32 Firmware Documentation — v1.0*
*Language: Arduino C++ | Target: ESP32 WROOM-32 | Display: SSD1306 OLED*
