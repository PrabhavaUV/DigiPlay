package com.example.digiplay.utils;

public class Constants {
    // 10.0.2.2 is the special IP for Android Emulator to access the host's localhost.
    // If you use a physical device, change this to your computer's local IP (e.g., http://192.168.1.11:8000)
    public static final String SERVER_BASE_URL = "http://10.151.166.99:8000";
    public static final String APP_API_KEY     = "my-mobile-app-static-key";
    public static final int    MAX_CONTENT_LEN = 100;
    public static final int    STATUS_POLL_INTERVAL_MS = 5000; // Poll every 5 seconds for faster feedback
}
