# DigiPlay – IoT-Based Remote Digital Name Display System
# `mobile_app.md` — Android Mobile Application (Java)

---

## Table of Contents

1. [App Overview](#app-overview)
2. [Architecture](#architecture)
3. [Technology Stack](#technology-stack)
4. [Project Structure](#project-structure)
5. [Database & Local Storage](#database--local-storage)
6. [Screen Designs & UI Flow](#screen-designs--ui-flow)
7. [API Integration](#api-integration)
8. [Security Implementation](#security-implementation)
9. [Build & Deployment](#build--deployment)
10. [Improvements & Future Enhancements](#improvements--future-enhancements)
11. [How to Convert to PDF](#how-to-convert-to-pdf)

---

## 1. App Overview

The DigiPlay Android application allows users to:
- Look up an ESP32 display device by its unique ID
- View the device's current approved display content
- Submit a request to update the device's display message
- Track the status of submitted requests (`PENDING`, `APPROVED`, `REJECTED`)

**Critical Security Constraint:** All update requests submitted from the app are stored server-side as `PENDING`. No update is applied to the ESP32 device until a server admin explicitly approves the request through the web dashboard.

---

## 2. Architecture

### 2.1 Architecture Pattern: MVP (Model-View-Presenter)

MVP is chosen over MVVM or MVC because:
- It keeps Activities thin (only UI concerns)
- Presenters contain all business logic and are testable
- No need for heavy Jetpack ViewModel or LiveData
- Ideal for a lightweight, small-scope application

```
+----------------+       +------------------+       +-----------------+
|    View        |       |   Presenter      |       |   Model         |
|  (Activity /   | <───> |  (Business Logic)|<────> | (Repository /   |
|   Fragment)    |       |                  |       |  API Service)   |
+----------------+       +------------------+       +-----------------+
       │                                                    │
       │ UI Events                              API Calls   │
       │ (clicks, input)                       /Responses   │
       ▼                                                    ▼
 User Interaction                           DigiPlay Server + SharedPrefs
```

### 2.2 App Flow Diagram

```
  [ App Launch ]
        │
        ▼
  [ Login Screen ] ──── (First time / logged out)
        │
        │ Valid credentials
        ▼
  [ Home Screen ] ──────────────────────────────────────────────┐
        │                                                        │
        ▼                                                        │
  [ Enter Device ID ] ──── (Scan QR or manual entry)           │
        │                                                        │
        ▼                                                        │
  [ Device Info Screen ] ──── Shows current display content     │
        │                                                        │
        ▼                                                        │
  [ Submit Update Request ] ──── User types new content         │
        │                                                        │
        ▼                                                        │
  [ Request Submitted → PENDING ]                               │
        │                                                        │
        ▼                                                        │
  [ Request Status Screen ] ──── Poll / Refresh status          │
        │                                                        │
        └────── Back to Home ──────────────────────────────────┘
```

---

## 3. Technology Stack

### 3.1 Technology Choices

| Component | Choice | Reason |
|---|---|---|
| Language | Java (Android SDK) | Mandatory requirement; stable, mature |
| Min SDK | API 24 (Android 7.0) | Broad device coverage (>95%) |
| HTTP Client | OkHttp 4.x | Lightweight, no reflection, efficient |
| JSON Parsing | org.json (built-in) | Zero dependency; ships with Android |
| Local Storage | SharedPreferences | Lightweight key-value for auth token + device history |
| UI | XML Layouts + Material Components | Standard Android; no extra framework |
| Architecture | MVP (manual, no framework) | Lightweight; avoids Dagger, Hilt overhead |
| Build | Gradle (Groovy DSL) | Standard Android build system |

**Why OkHttp over Retrofit?**
- Retrofit adds an annotation-processing layer that, while convenient, adds overhead
- OkHttp is the underlying layer Retrofit uses anyway
- For a small API surface (5-6 endpoints), OkHttp is simpler and more transparent

**Why org.json over Gson/Moshi?**
- org.json ships with Android — zero additional dependency
- Gson/Moshi are unnecessary for a small JSON surface
- Reduces APK size

### 3.2 Dependencies (`build.gradle`)

```groovy
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

    // Testing
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
```

---

## 4. Project Structure

```
app/
└── src/main/
    ├── java/com/digiplay/app/
    │   ├── activities/
    │   │   ├── LoginActivity.java
    │   │   ├── HomeActivity.java
    │   │   ├── DeviceActivity.java
    │   │   ├── SubmitRequestActivity.java
    │   │   └── RequestStatusActivity.java
    │   ├── presenters/
    │   │   ├── LoginPresenter.java
    │   │   ├── DevicePresenter.java
    │   │   └── RequestPresenter.java
    │   ├── models/
    │   │   ├── Device.java
    │   │   ├── UpdateRequest.java
    │   │   └── ApiResponse.java
    │   ├── network/
    │   │   ├── ApiClient.java          # OkHttp singleton
    │   │   └── ApiService.java         # All API call methods
    │   ├── storage/
    │   │   └── PrefsManager.java       # SharedPreferences wrapper
    │   ├── interfaces/
    │   │   ├── ILoginView.java
    │   │   ├── IDeviceView.java
    │   │   └── IRequestView.java
    │   └── utils/
    │       ├── Constants.java
    │       └── Validator.java
    └── res/
        ├── layout/
        │   ├── activity_login.xml
        │   ├── activity_home.xml
        │   ├── activity_device.xml
        │   ├── activity_submit_request.xml
        │   └── activity_request_status.xml
        ├── values/
        │   ├── strings.xml
        │   ├── colors.xml
        │   └── themes.xml
        └── drawable/
            └── ic_launcher.xml
```

---

## 5. Database & Local Storage

### 5.1 SharedPreferences Schema

```
SharedPreferences file: "digiplay_prefs"

Key                     | Type    | Description
------------------------|---------|-----------------------------
auth_token              | String  | User's JWT or API token
username                | String  | Logged-in username
recent_device_ids       | String  | JSON array of recently accessed device IDs
last_request_id         | int     | Most recently submitted request ID
last_request_status     | String  | PENDING / APPROVED / REJECTED
server_base_url         | String  | Configurable server URL
```

### 5.2 PrefsManager Implementation

```java
// storage/PrefsManager.java
public class PrefsManager {
    private static final String PREFS_NAME = "digiplay_prefs";
    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveAuthToken(String token) {
        prefs.edit().putString("auth_token", token).apply();
    }

    public String getAuthToken() {
        return prefs.getString("auth_token", null);
    }

    public boolean isLoggedIn() {
        return getAuthToken() != null;
    }

    public void saveUsername(String username) {
        prefs.edit().putString("username", username).apply();
    }

    public String getUsername() {
        return prefs.getString("username", "");
    }

    public void saveLastRequestId(int id) {
        prefs.edit().putInt("last_request_id", id).apply();
    }

    public int getLastRequestId() {
        return prefs.getInt("last_request_id", -1);
    }

    public void saveLastRequestStatus(String status) {
        prefs.edit().putString("last_request_status", status).apply();
    }

    public String getLastRequestStatus() {
        return prefs.getString("last_request_status", "");
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
```

---

## 6. Screen Designs & UI Flow

### 6.1 Login Screen

```
+-------------------------------+
|         DigiPlay              |
|       Display Manager         |
|                               |
|   +-----------------------+   |
|   |   Username            |   |
|   +-----------------------+   |
|                               |
|   +-----------------------+   |
|   |   Password            |   |
|   +-----------------------+   |
|                               |
|   [       Login       ]       |
|                               |
|   ⚠ Error message here        |
+-------------------------------+
```

**LoginActivity.java (key excerpt):**

```java
// activities/LoginActivity.java
public class LoginActivity extends AppCompatActivity implements ILoginView {
    private EditText etUsername, etPassword;
    private Button btnLogin;
    private TextView tvError;
    private LoginPresenter presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        PrefsManager prefs = new PrefsManager(this);
        if (prefs.isLoggedIn()) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin   = findViewById(R.id.btn_login);
        tvError    = findViewById(R.id.tv_error);

        presenter = new LoginPresenter(this, new ApiService(), prefs);

        btnLogin.setOnClickListener(v -> {
            String user = etUsername.getText().toString().trim();
            String pass = etPassword.getText().toString().trim();
            presenter.login(user, pass);
        });
    }

    @Override
    public void onLoginSuccess() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    @Override
    public void onLoginError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    @Override
    public void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "Logging in..." : "Login");
    }
}
```

### 6.2 Home Screen — Enter Device ID

```
+-------------------------------+
|  ≡  DigiPlay       [Logout]  |
+-------------------------------+
|   Enter Device ID             |
|                               |
|   +-----------------------+   |
|   |  Device ID            |   |
|   +-----------------------+   |
|                               |
|   [     Look Up Device    ]   |
|                               |
|   Recent Devices:             |
|   ┌─────────────────────┐     |
|   │ ◉ DEV-0042          │     |
|   │ ◉ DEV-0031          │     |
|   └─────────────────────┘     |
+-------------------------------+
```

### 6.3 Device Info Screen

```
+-------------------------------+
|  ←  Device Info               |
+-------------------------------+
|  Device: DEV-0042             |
|  Status: 🟢 Online            |
|                               |
|  Current Display:             |
|  ┌─────────────────────────┐  |
|  │  "Alice Johnson         │  |
|  │   Software Engineer"    │  |
|  └─────────────────────────┘  |
|                               |
|  Last Updated: 2024-01-15     |
|  10:30 AM                     |
|                               |
|  [ Request Display Update ]   |
+-------------------------------+
```

### 6.4 Submit Update Request Screen

```
+-------------------------------+
|  ←  Request Update            |
+-------------------------------+
|  Device: DEV-0042             |
|                               |
|  New Display Content:         |
|  +-----------------------+    |
|  |  Type new name/       |    |
|  |  message here...      |    |
|  +-----------------------+    |
|  Characters: 0 / 100          |
|                               |
|  ⚠ This request requires      |
|    admin approval before      |
|    the display updates.       |
|                               |
|  [    Submit Request    ]     |
+-------------------------------+
```

### 6.5 Request Status Screen

```
+-------------------------------+
|  ←  Request Status            |
+-------------------------------+
|  Request #42                  |
|  Device: DEV-0042             |
|                               |
|  Status:                      |
|  ┌─────────────────────────┐  |
|  │  ⏳  PENDING            │  |
|  │  Awaiting admin review  │  |
|  └─────────────────────────┘  |
|                               |
|  Submitted: 2024-01-15 10:25  |
|  Content: "Alice Johnson..."  |
|                               |
|  [   Refresh Status   ]       |
|  [   Back to Home     ]       |
+-------------------------------+

Status variants:
⏳ PENDING  — Yellow badge
✅ APPROVED — Green badge
❌ REJECTED — Red badge
```

---

## 7. API Integration

### 7.1 ApiClient (OkHttp Singleton)

```java
// network/ApiClient.java
public class ApiClient {
    private static OkHttpClient client;

    public static OkHttpClient getInstance() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
        }
        return client;
    }
}
```

### 7.2 ApiService — All API Calls

```java
// network/ApiService.java
public class ApiService {
    private static final String BASE_URL = Constants.SERVER_BASE_URL;
    private static final String API_KEY  = Constants.APP_API_KEY;
    private final OkHttpClient http = ApiClient.getInstance();
    private final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    // --- Login ---
    public void login(String username, String password, Callback<String> callback) {
        String json = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        Request request = new Request.Builder()
            .url(BASE_URL + "/auth/login")
            .post(RequestBody.create(json, JSON))
            .build();

        http.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                try {
                    JSONObject obj = new JSONObject(body);
                    if (response.isSuccessful()) {
                        callback.onSuccess(obj.getString("access_token"));
                    } else {
                        callback.onError(obj.optString("detail", "Login failed"));
                    }
                } catch (JSONException e) {
                    callback.onError("Parse error");
                }
            }
        });
    }

    // --- Fetch Device Info ---
    public void getDevice(String deviceId, Callback<Device> callback) {
        Request request = new Request.Builder()
            .url(BASE_URL + "/api/devices/" + deviceId)
            .addHeader("X-API-Key", API_KEY)
            .get()
            .build();

        http.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onError("Network error");
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                try {
                    if (response.isSuccessful()) {
                        JSONObject obj = new JSONObject(body);
                        Device device = new Device(
                            obj.getString("id"),
                            obj.getString("name"),
                            obj.getString("current_content"),
                            obj.getBoolean("is_online")
                        );
                        callback.onSuccess(device);
                    } else {
                        callback.onError("Device not found");
                    }
                } catch (JSONException e) {
                    callback.onError("Parse error");
                }
            }
        });
    }

    // --- Submit Update Request ---
    public void submitRequest(String deviceId, String requestedBy,
                              String newContent, Callback<Integer> callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("device_id", deviceId);
            body.put("requested_by", requestedBy);
            body.put("new_content", newContent);

            Request request = new Request.Builder()
                .url(BASE_URL + "/api/requests")
                .addHeader("X-API-Key", API_KEY)
                .post(RequestBody.create(body.toString(), JSON))
                .build();

            http.newCall(request).enqueue(new okhttp3.Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    callback.onError("Network error");
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    String resp = response.body().string();
                    try {
                        JSONObject obj = new JSONObject(resp);
                        if (response.isSuccessful()) {
                            callback.onSuccess(obj.getInt("request_id"));
                        } else {
                            callback.onError(obj.optString("detail", "Submission failed"));
                        }
                    } catch (JSONException e) {
                        callback.onError("Parse error");
                    }
                }
            });
        } catch (JSONException e) {
            callback.onError("Request build error");
        }
    }

    // --- Get Request Status ---
    public void getRequestStatus(int requestId, Callback<String> callback) {
        Request request = new Request.Builder()
            .url(BASE_URL + "/api/requests/" + requestId + "/status")
            .addHeader("X-API-Key", API_KEY)
            .get()
            .build();

        http.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onError("Network error");
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                try {
                    JSONObject obj = new JSONObject(body);
                    callback.onSuccess(obj.getString("status"));
                } catch (JSONException e) {
                    callback.onError("Parse error");
                }
            }
        });
    }
}
```

### 7.3 Presenter Example — RequestPresenter

```java
// presenters/RequestPresenter.java
public class RequestPresenter {
    private final IRequestView view;
    private final ApiService apiService;
    private final PrefsManager prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public RequestPresenter(IRequestView view, ApiService apiService, PrefsManager prefs) {
        this.view = view;
        this.apiService = apiService;
        this.prefs = prefs;
    }

    public void submitRequest(String deviceId, String newContent) {
        if (newContent == null || newContent.trim().isEmpty()) {
            view.showError("Content cannot be empty");
            return;
        }
        if (newContent.length() > 100) {
            view.showError("Content must be 100 characters or less");
            return;
        }

        view.setLoading(true);

        apiService.submitRequest(deviceId, prefs.getUsername(), newContent,
            new ApiService.Callback<Integer>() {
                @Override public void onSuccess(Integer requestId) {
                    prefs.saveLastRequestId(requestId);
                    prefs.saveLastRequestStatus("PENDING");
                    mainHandler.post(() -> {
                        view.setLoading(false);
                        view.onRequestSubmitted(requestId);
                    });
                }
                @Override public void onError(String message) {
                    mainHandler.post(() -> {
                        view.setLoading(false);
                        view.showError(message);
                    });
                }
            }
        );
    }

    public void checkRequestStatus(int requestId) {
        view.setLoading(true);
        apiService.getRequestStatus(requestId, new ApiService.Callback<String>() {
            @Override public void onSuccess(String status) {
                prefs.saveLastRequestStatus(status);
                mainHandler.post(() -> {
                    view.setLoading(false);
                    view.onStatusUpdated(status);
                });
            }
            @Override public void onError(String message) {
                mainHandler.post(() -> {
                    view.setLoading(false);
                    view.showError(message);
                });
            }
        });
    }
}
```

### 7.4 Constants

```java
// utils/Constants.java
public class Constants {
    public static final String SERVER_BASE_URL = "https://your-server.com";
    public static final String APP_API_KEY     = "your-static-app-api-key";
    public static final int    MAX_CONTENT_LEN = 100;
    public static final int    STATUS_POLL_INTERVAL_MS = 10_000; // 10 seconds
}
```

> **Security Note:** The `APP_API_KEY` should be stored in `local.properties` and injected via `BuildConfig` at build time — never hardcoded directly. For production, use Android Keystore or a backend authentication flow.

---

## 8. Security Implementation

### 8.1 API Key Storage (Production)

```groovy
// local.properties (NOT committed to git)
API_KEY=your-actual-api-key

// build.gradle (app level)
android {
    defaultConfig {
        buildConfigField "String", "APP_API_KEY",
            "\"${project.findProperty('API_KEY') ?: ''}\""
    }
}

// Usage in code
String apiKey = BuildConfig.APP_API_KEY;
```

### 8.2 AndroidManifest.xml Permissions

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<application
    android:usesCleartextTraffic="false"
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

```xml
<!-- res/xml/network_security_config.xml -->
<!-- Enforce HTTPS; block cleartext HTTP -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">your-server.com</domain>
    </domain-config>
</network-security-config>
```

### 8.3 Input Validation

```java
// utils/Validator.java
public class Validator {
    public static boolean isValidDeviceId(String id) {
        if (id == null || id.trim().isEmpty()) return false;
        // UUID format validation
        return id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-" +
                          "[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    public static boolean isValidContent(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        return content.length() <= Constants.MAX_CONTENT_LEN;
    }
}
```

---

## 9. Build & Deployment

### 9.1 Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK API 34 (compile), API 24 (min)
- Gradle 8.x

### 9.2 Setup Steps

```bash
# 1. Clone the repository
git clone https://github.com/your-org/digiplay-android.git
cd digiplay-android

# 2. Configure local.properties
echo "API_KEY=your-api-key-here" >> local.properties
echo "SERVER_URL=https://your-server.com" >> local.properties

# 3. Open in Android Studio or build via CLI
./gradlew assembleDebug

# 4. Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 9.3 Build Variants

```groovy
// build.gradle (app level)
android {
    buildTypes {
        debug {
            buildConfigField "String", "SERVER_URL", "\"http://192.168.1.100:8000\""
            applicationIdSuffix ".debug"
        }
        release {
            buildConfigField "String", "SERVER_URL", "\"https://your-server.com\""
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'),
                          'proguard-rules.pro'
        }
    }
}
```

### 9.4 Release Build

```bash
# Generate signed APK
./gradlew assembleRelease

# APK location
app/build/outputs/apk/release/app-release.apk

# Or generate AAB for Play Store
./gradlew bundleRelease
app/build/outputs/bundle/release/app-release.aab
```

### 9.5 ProGuard Rules

```
# proguard-rules.pro
-keep class com.digiplay.app.models.** { *; }
-keep class com.digiplay.app.network.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
```

---

## 10. Improvements & Future Enhancements

| Enhancement | Description | Priority |
|---|---|---|
| QR Code Scanner | Scan device QR code to auto-fill device ID (ZXing lightweight) | High |
| Push Notifications | Notify users when their request is approved/rejected (Firebase FCM) | High |
| Request History | Local SQLite database for tracking all submitted requests | Medium |
| Offline Mode | Cache last known device info for offline viewing | Medium |
| Dark Theme | Material 3 dynamic theming support | Low |
| Multi-Language | i18n string resources for localization | Low |
| Biometric Auth | Fingerprint/face lock for app access | Medium |
| Deep Links | Open app directly to device lookup via NFC tag or URL | Low |
| Certificate Pinning | Pin server SSL certificate for enhanced MITM protection | High |

---

## 11. How to Convert to PDF

### Using Pandoc (Recommended)

```bash
# Install pandoc
sudo apt install pandoc texlive-xetex

# Basic conversion
pandoc mobile_app.md -o mobile_app.pdf

# High-quality conversion
pandoc mobile_app.md \
  --pdf-engine=xelatex \
  -V geometry:margin=1in \
  -V fontsize=11pt \
  -V mainfont="DejaVu Sans" \
  --toc \
  --toc-depth=2 \
  -o mobile_app.pdf
```

### Using VS Code

1. Install extension: **Markdown PDF** (yzane.markdown-pdf)
2. Open `mobile_app.md`
3. Right-click → **Markdown PDF: Export (pdf)**

### Using Typora

1. Open `mobile_app.md` in Typora
2. Go to **File → Export → PDF**
3. Choose preferred theme and export

---

*DigiPlay Android App Documentation — v1.0*
*Language: Java | Architecture: MVP | Min SDK: API 24*
