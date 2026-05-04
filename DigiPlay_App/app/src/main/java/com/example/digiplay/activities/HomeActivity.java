package com.example.digiplay.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.digiplay.R;

public class HomeActivity extends AppCompatActivity {
    private EditText etDeviceId;
    private Button btnLookup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        etDeviceId = findViewById(R.id.et_device_id);
        btnLookup = findViewById(R.id.btn_lookup);

        btnLookup.setOnClickListener(v -> {
            String deviceId = etDeviceId.getText().toString().trim();
            if (!deviceId.isEmpty()) {
                Intent intent = new Intent(HomeActivity.this, DeviceActivity.class);
                intent.putExtra("DEVICE_ID", deviceId);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Please enter a Device ID", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Clear input when coming back (effectively "logging out")
        if (etDeviceId != null) {
            etDeviceId.setText("");
        }
    }
}
