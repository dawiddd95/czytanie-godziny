package com.timetalker.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
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

    // Potrójne stuknięcie w ekran (gdy aplikacja otwarta)
    private static final int TRIPLE_TAP_COUNT = 3;
    private static final long TRIPLE_TAP_WINDOW_MS = 1500;
    private static final long TRIPLE_TAP_COOLDOWN_MS = 300;
    private long[] tripleTapTimes = new long[TRIPLE_TAP_COUNT];
    private int tripleTapCount = 0;
    private long lastTripleTapTime = 0;

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

        setupTripleTapOnScreen();

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

    /** Wykrywa potrójne stuknięcie w ekran - działa gdy aplikacja jest otwarta */
    private void setupTripleTapOnScreen() {
        View rootLayout = findViewById(android.R.id.content);
        rootLayout.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                long now = System.currentTimeMillis();
                if (now - lastTripleTapTime < TRIPLE_TAP_COOLDOWN_MS) return false;

                lastTripleTapTime = now;
                if (tripleTapCount < TRIPLE_TAP_COUNT) {
                    tripleTapTimes[tripleTapCount] = now;
                    tripleTapCount++;
                } else {
                    System.arraycopy(tripleTapTimes, 1, tripleTapTimes, 0, TRIPLE_TAP_COUNT - 1);
                    tripleTapTimes[TRIPLE_TAP_COUNT - 1] = now;
                }

                if (tripleTapCount >= TRIPLE_TAP_COUNT) {
                    long diff = tripleTapTimes[TRIPLE_TAP_COUNT - 1] - tripleTapTimes[0];
                    if (diff <= TRIPLE_TAP_WINDOW_MS) {
                        tripleTapCount = 0;
                        speakTimeFromScreenTap();
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private void speakTimeFromScreenTap() {
        PolishTimeSpeaker speaker = new PolishTimeSpeaker(this);
        findViewById(R.id.btn_test).postDelayed(() -> {
            if (speaker.isReady()) {
                speaker.speakCurrentTime();
                findViewById(R.id.btn_test).postDelayed(speaker::shutdown, 5000);
            } else {
                Toast.makeText(this, "TTS nie jest gotowy, spróbuj ponownie", Toast.LENGTH_SHORT).show();
                speaker.shutdown();
            }
        }, 1500);
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




