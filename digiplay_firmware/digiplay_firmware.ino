// ============================================================
// DigiPlay – IoT-Based Remote Digital Name Display System
// `esp32_firmware.ino` — ESP32 Device Firmware (MQTT Edition)
// ============================================================

#include <WiFi.h>

#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <Adafruit_SSD1306.h>
#include <Adafruit_GFX.h>
#include <Wire.h>
#include <Preferences.h>

// ─── CONFIGURATION ───────────────────────────────────────────
const char* MQTT_BROKER = "172.17.10.193"; // User specified IP
const int   MQTT_PORT   = 1883;
const char* DEVICE_ID   = "DP001";       // User specified ID

// WiFi Credentials (User specified)
const char* WIFI_SSID   = "Airtel_VPBHPBPN";
const char* WIFI_PASS   = "your_password_here"; // Replace with actual password

// ─── HARDWARE PINS ───────────────────────────────────────────

#define LED_PIN      2
#define RESET_PIN    0
#define OLED_SDA     23  // User's wiring
#define OLED_SCL     22  // User's wiring

// ─── DISPLAY SETUP ───────────────────────────────────────────

#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, -1);

// ─── GLOBALS ─────────────────────────────────────────────────
Preferences preferences;
WiFiClient espClient;
PubSubClient mqtt(espClient);

String currentContent  = "Initializing...";
String currentChecksum = "";


// ─── DISPLAY FUNCTIONS ───────────────────────────────────────
void setupDisplay() {
    Wire.begin(OLED_SDA, OLED_SCL);
    if (!display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) {
        Serial.println(F("[Display] FATAL: SSD1306 not found!"));
        while (true) delay(1000);
    }
    display.clearDisplay();
    display.setTextColor(SSD1306_WHITE);
    display.display();
}

void displayContent(String text) {
    display.clearDisplay();
    display.setTextWrap(true);
    
    // Determine font size based on content length
    if (text.length() <= 20) {
        display.setTextSize(2);
        display.setCursor(0, 20);
    } else {
        display.setTextSize(1);
        display.setCursor(0, 10);
    }
    
    display.println(text);
    display.display();
    Serial.println("[Display] Showing: " + text);
}

void displayStatus(String msg) {
    display.clearDisplay();
    display.setTextSize(1);
    display.setCursor(0, 28);
    display.println(msg);
    display.display();
}

// ─── PREFERENCES (FLASH STORAGE) ─────────────────────────────
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

// ─── STATUS LED ──────────────────────────────────────────────
void ledBlink(int times, int ms = 200) {
    for (int i = 0; i < times; i++) {
        digitalWrite(LED_PIN, HIGH); delay(ms);
        digitalWrite(LED_PIN, LOW);  delay(ms);
    }
}

// ─── WIFI ────────────────────────────────────────────────────
void setupWiFi() {
    displayStatus("Connecting WiFi...");
    ledBlink(1);

    Serial.println("[WiFi] Connecting to: " + String(WIFI_SSID));
    displayStatus("Connecting: Airtel..");
    
    WiFi.begin(WIFI_SSID, WIFI_PASS);
    
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }

    Serial.println("\n[WiFi] Connected: " + WiFi.localIP().toString());
    displayStatus("WiFi Connected!");
    ledBlink(2);
}

// ─── MQTT CALLBACK (The Core Update Logic) ───────────────────
void mqttCallback(char* topic, byte* payload, unsigned int length) {
    Serial.println(F("[MQTT] Message arrived."));
    
    String msg = "";
    for (int i = 0; i < length; i++) msg += (char)payload[i];

    StaticJsonDocument<512> doc;
    DeserializationError error = deserializeJson(doc, msg);

    if (error) {
        Serial.print(F("[JSON] Parse failed: "));
        Serial.println(error.f_str());
        return;
    }

    String newContent  = doc["content"].as<String>();
    String newChecksum = doc["checksum"].as<String>();

    // Checksum check to prevent redundant writes/blinks
    if (newChecksum == currentChecksum) {
        Serial.println(F("[Update] No change (matching checksum)."));
        return;
    }

    Serial.println("[Update] New content approved: " + newContent);
    currentContent  = newContent;
    currentChecksum = newChecksum;

    saveData(currentContent, currentChecksum);
    displayContent(currentContent);
    ledBlink(3);
}

void reconnectMQTT() {
    while (!mqtt.connected()) {
        Serial.print(F("[MQTT] Attempting connection..."));
        String clientId = "DigiPlayClient-" + String(DEVICE_ID);
        
        if (mqtt.connect(clientId.c_str())) {
            Serial.println(F(" connected!"));
            String topic = "digiplay/devices/" + String(DEVICE_ID) + "/content";
            mqtt.subscribe(topic.c_str());
            Serial.println("[MQTT] Subscribed to: " + topic);
        } else {
            Serial.print(F(" failed, rc="));
            Serial.print(mqtt.state());
            Serial.println(F(" try again in 5 seconds"));
            delay(5000);
        }
    }
}

// ─── SETUP ───────────────────────────────────────────────────
void setup() {
    Serial.begin(115200);
    pinMode(LED_PIN,   OUTPUT);
    pinMode(RESET_PIN, INPUT_PULLUP);

    setupDisplay();
    loadStoredData();
    displayContent(currentContent); // Show last known content immediately
    
    setupWiFi();
    
    mqtt.setServer(MQTT_BROKER, MQTT_PORT);
    mqtt.setCallback(mqttCallback);
}

// ─── MAIN LOOP ───────────────────────────────────────────────
void loop() {
    // Reset Check
    if (digitalRead(RESET_PIN) == LOW) {
        delay(3000);
        if (digitalRead(RESET_PIN) == LOW) {
            Serial.println(F("[Reset] Restarting device..."));
            displayStatus("Restarting...");
            ESP.restart();
        }
    }

    if (!mqtt.connected()) {
        reconnectMQTT();
    }
    mqtt.loop();
}
