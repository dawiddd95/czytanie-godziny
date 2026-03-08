package com.timetalker.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "timetalker_prefs";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";

    private Button btnToggle;
    private TextView tvStatus;
    private TextView tvInfo;
    private boolean isServiceRunning = false;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startTapService();
                } else {
                    Toast.makeText(this,
                            "Uprawnienie do powiadomień jest wymagane do działania w tle",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnToggle = findViewById(R.id.btn_toggle);
        tvStatus = findViewById(R.id.tv_status);
        tvInfo = findViewById(R.id.tv_info);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isServiceRunning = prefs.getBoolean(KEY_SERVICE_ENABLED, false);

        updateUI();

        btnToggle.setOnClickListener(v -> {
            if (isServiceRunning) {
                stopTapService();
            } else {
                requestPermissionsAndStart();
            }
        });

        // Testowy przycisk do sprawdzenia TTS
        Button btnTest = findViewById(R.id.btn_test);
        btnTest.setOnClickListener(v -> {
            PolishTimeSpeaker speaker = new PolishTimeSpeaker(this);
            // Dajemy chwilę na inicjalizację TTS
            btnTest.postDelayed(() -> {
                if (speaker.isReady()) {
                    speaker.speakCurrentTime();
                    // Zamknij po 5 sekundach
                    btnTest.postDelayed(speaker::shutdown, 5000);
                } else {
                    Toast.makeText(this, "TTS nie jest gotowy, spróbuj ponownie",
                            Toast.LENGTH_SHORT).show();
                }
            }, 1500);
        });
    }

    private void requestPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        startTapService();
    }

    private void startTapService() {
        Intent serviceIntent = new Intent(this, TapDetectorService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        isServiceRunning = true;
        saveState();
        updateUI();
        Toast.makeText(this, "Detektor stuknięć uruchomiony!", Toast.LENGTH_SHORT).show();
    }

    private void stopTapService() {
        Intent serviceIntent = new Intent(this, TapDetectorService.class);
        stopService(serviceIntent);
        isServiceRunning = false;
        saveState();
        updateUI();
        Toast.makeText(this, "Detektor stuknięć zatrzymany", Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {
        if (isServiceRunning) {
            tvStatus.setText("● Aktywny");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_active));
            btnToggle.setText("Zatrzymaj");
            btnToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.btn_stop));
        } else {
            tvStatus.setText("○ Nieaktywny");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_inactive));
            btnToggle.setText("Uruchom");
            btnToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.btn_start));
        }
    }

    private void saveState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SERVICE_ENABLED, isServiceRunning)
                .apply();
    }
}

