package com.example.digiplay.network;

import com.example.digiplay.models.Device;
import com.example.digiplay.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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
        try {
            JSONObject body = new JSONObject();
            body.put("username", username);
            body.put("password", password);

            Request request = new Request.Builder()
                .url(BASE_URL + "/auth/login")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

            http.newCall(request).enqueue(new okhttp3.Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    callback.onError("Network error: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    String bodyStr = response.body() != null ? response.body().string() : "";
                    try {
                        JSONObject obj = new JSONObject(bodyStr);
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
        } catch (JSONException e) {
            callback.onError("Request build error");
        }
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
                String bodyStr = response.body() != null ? response.body().string() : "";
                try {
                    if (response.isSuccessful()) {
                        JSONObject obj = new JSONObject(bodyStr);
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
                    String resp = response.body() != null ? response.body().string() : "";
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
                String bodyStr = response.body() != null ? response.body().string() : "";
                try {
                    JSONObject obj = new JSONObject(bodyStr);
                    callback.onSuccess(obj.getString("status"));
                } catch (JSONException e) {
                    callback.onError("Parse error");
                }
            }
        });
    }
}
