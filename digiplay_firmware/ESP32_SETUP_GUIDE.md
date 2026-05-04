# DigiPlay – Complete Setup & Flash Guide

Follow these steps to deploy your **DigiPlay** Digital Name Display.

## 1. Hardware Required
* **Microcontroller:** ESP32 (WROOM-32 Recommended)
* **Display:** SSD1306 OLED (128x64 I2C)
* **Wiring:**
    | OLED Pin | ESP32 Pin |
    |---|---|
    | VCC | 3.3V |
    | GND | GND |
    | **SCL** | **GPIO 22** |
    | **SDA** | **GPIO 23** |

## 2. Server Setup (Node.js)
The Node.js server now includes a built-in MQTT Broker (Aedes).
1. Ensure your server is running: `node index.js`
2. You will see `MQTT Broker (Aedes) running on port 1883` in the logs.
3. Find your computer's local IP address (e.g., `192.168.1.11`).

## 3. Arduino IDE Configuration
1. Open `digiplay_firmware.ino`.
2. **Library Manager (Ctrl+Shift+I):** Install exactly these:
    * `WiFiManager` (by tzapu)
    * `PubSubClient` (by Nick O'Leary)
    * `ArduinoJson` (by Benoit Blanchon)
    * `Adafruit SSD1306` & `Adafruit GFX`
3. **Board Selection:** `Tools → Board → ESP32 Dev Module`.
4. **Port Selection:** `Tools → Port → /dev/ttyUSB0` (Run `sudo chmod a+rw /dev/ttyUSB0` if greyed out).

## 4. Firmware Settings
In the `.ino` file, ensure these match your environment:
* `MQTT_BROKER`: Set to your computer's IP (e.g. `192.168.1.11`).
* `DEVICE_ID`: Ensure this matches the ID generated on your web dashboard (e.g. `DP001`).

## 5. First-Time Provisioning
1. **Flash the ESP32:** Click the Upload button.
2. **WiFi Setup:**
    * When the screen says "Connecting WiFi...", connect your phone to the `DigiPlay-XXXX` hotspot.
    * A portal will pop up; select your Home WiFi and enter the password.
3. **MQTT Link:** The device will now connect to your Node server's broker.

## 6. Testing the Update
1. Open `http://localhost:8000/dashboard` in your browser.
2. Trigger an update request (via mobile app API or manual DB entry).
3. Click **Approve** on the Dashboard.
4. The ESP32 will instantly (via MQTT) blink its LED and update the OLED screen with the new approved text!
