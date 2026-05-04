package com.example.digiplay.network;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

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
