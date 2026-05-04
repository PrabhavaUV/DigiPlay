package com.example.digiplay.presenters;

import android.os.Handler;
import android.os.Looper;

import com.example.digiplay.interfaces.IRequestView;
import com.example.digiplay.network.ApiService;

public class RequestPresenter {
    private final IRequestView view;
    private final ApiService apiService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public RequestPresenter(IRequestView view, ApiService apiService) {
        this.view = view;
        this.apiService = apiService;
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

        apiService.submitRequest(deviceId, "Mobile User", newContent,
            new ApiService.Callback<Integer>() {
                @Override public void onSuccess(Integer requestId) {
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
        apiService.getRequestStatus(requestId, new ApiService.Callback<String>() {
            @Override public void onSuccess(String status) {
                mainHandler.post(() -> {
                    view.onStatusUpdated(status);
                });
            }
            @Override public void onError(String message) {
                mainHandler.post(() -> {
                    view.showError(message);
                });
            }
        });
    }
}
