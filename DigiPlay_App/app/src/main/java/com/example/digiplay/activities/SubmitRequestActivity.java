package com.example.digiplay.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.digiplay.R;
import com.example.digiplay.interfaces.IRequestView;
import com.example.digiplay.network.ApiService;
import com.example.digiplay.presenters.RequestPresenter;

public class SubmitRequestActivity extends AppCompatActivity implements IRequestView {
    private TextView tvDeviceId;
    private EditText etNewContent;
    private Button btnSubmit;
    private ProgressBar progressBar;
    private RequestPresenter presenter;
    private String deviceId;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private int currentRequestId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_submit_request);

        deviceId = getIntent().getStringExtra("DEVICE_ID");

        tvDeviceId = findViewById(R.id.tv_device_id);
        etNewContent = findViewById(R.id.et_new_content);
        btnSubmit = findViewById(R.id.btn_submit);
        progressBar = findViewById(R.id.progress_bar);

        tvDeviceId.setText("Device: " + deviceId);

        presenter = new RequestPresenter(this, new ApiService());

        btnSubmit.setOnClickListener(v -> {
            String content = etNewContent.getText().toString().trim();
            presenter.submitRequest(deviceId, content);
        });
    }

    @Override
    public void onRequestSubmitted(int requestId) {
        this.currentRequestId = requestId;
        Toast.makeText(this, "Request submitted. Waiting for approval...", Toast.LENGTH_LONG).show();
        startPolling();
    }

    private void startPolling() {
        setLoading(true);
        pollHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (currentRequestId != -1) {
                    presenter.checkRequestStatus(currentRequestId);
                }
            }
        }, 5000);
    }

    @Override
    public void onStatusUpdated(String status) {
        if (status.equalsIgnoreCase("PENDING")) {
            pollHandler.postDelayed(() -> presenter.checkRequestStatus(currentRequestId), 5000);
        } else {
            showResultPopup(status);
        }
    }

    private void showResultPopup(String status) {
        setLoading(false);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Request " + status);
        
        String message = status.equalsIgnoreCase("APPROVED") 
            ? "Your request has been approved and the display is updated!" 
            : "Your request was rejected by the administrator.";
            
        builder.setMessage(message);
        builder.setCancelable(false);
        builder.setPositiveButton("OK", (dialog, which) -> {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
        builder.show();
    }

    @Override
    public void showError(String message) {
        setLoading(false);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void setLoading(boolean loading) {
        btnSubmit.setEnabled(!loading);
        etNewContent.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pollHandler.removeCallbacksAndMessages(null);
    }
}
