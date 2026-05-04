package com.example.digiplay.presenters;

import android.os.Handler;
import android.os.Looper;

import com.example.digiplay.interfaces.IDeviceView;
import com.example.digiplay.models.Device;
import com.example.digiplay.network.ApiService;

public class DevicePresenter {
    private final IDeviceView view;
    private final ApiService apiService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public DevicePresenter(IDeviceView view, ApiService apiService) {
        this.view = view;
        this.apiService = apiService;
    }

    public void loadDevice(String deviceId) {
        view.setLoading(true);
        apiService.getDevice(deviceId, new ApiService.Callback<Device>() {
            @Override
            public void onSuccess(Device device) {
                mainHandler.post(() -> {
                    view.setLoading(false);
                    view.onDeviceLoaded(device);
                });
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> {
                    view.setLoading(false);
                    view.onError(message);
                });
            }
        });
    }
}
