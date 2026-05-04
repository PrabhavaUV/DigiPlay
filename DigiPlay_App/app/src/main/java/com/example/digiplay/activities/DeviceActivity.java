package com.example.digiplay.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.digiplay.R;
import com.example.digiplay.interfaces.IDeviceView;
import com.example.digiplay.models.Device;
import com.example.digiplay.network.ApiService;
import com.example.digiplay.presenters.DevicePresenter;

public class DeviceActivity extends AppCompatActivity implements IDeviceView {
    private TextView tvDeviceId, tvStatus, tvCurrentContent;
    private Button btnRequestUpdate;
    private ProgressBar progressBar;
    private LinearLayout layoutContent;
    private DevicePresenter presenter;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        deviceId = getIntent().getStringExtra("DEVICE_ID");

        tvDeviceId = findViewById(R.id.tv_device_id);
        tvStatus = findViewById(R.id.tv_status);
        tvCurrentContent = findViewById(R.id.tv_current_content);
        btnRequestUpdate = findViewById(R.id.btn_request_update);
        progressBar = findViewById(R.id.progress_bar);
        layoutContent = findViewById(R.id.layout_content);

        presenter = new DevicePresenter(this, new ApiService());
        presenter.loadDevice(deviceId);

        btnRequestUpdate.setOnClickListener(v -> {
            Intent intent = new Intent(DeviceActivity.this, SubmitRequestActivity.class);
            intent.putExtra("DEVICE_ID", deviceId);
            startActivity(intent);
        });
    }

    @Override
    public void onDeviceLoaded(Device device) {
        tvDeviceId.setText("Device: " + device.getId());
        tvStatus.setText("Status: " + (device.isOnline() ? "🟢 Online" : "🔴 Offline"));
        tvCurrentContent.setText('"' + device.getCurrentContent() + '"');
        layoutContent.setVisibility(View.VISIBLE);
    }

    @Override
    public void onError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
