package com.example.digiplay.interfaces;

import com.example.digiplay.models.Device;

public interface IDeviceView {
    void onDeviceLoaded(Device device);
    void onError(String message);
    void setLoading(boolean loading);
}
