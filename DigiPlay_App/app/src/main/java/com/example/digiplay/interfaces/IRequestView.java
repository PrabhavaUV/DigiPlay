package com.example.digiplay.interfaces;

public interface IRequestView {
    void onRequestSubmitted(int requestId);
    void onStatusUpdated(String status);
    void showError(String message);
    void setLoading(boolean loading);
}
