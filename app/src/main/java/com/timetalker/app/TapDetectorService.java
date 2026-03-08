package com.timetalker.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class TapDetectorService extends Service implements SensorEventListener {

    private static final String TAG = "TapDetectorService";
    private static final String CHANNEL_ID = "tap_detector_channel";
    private static final int NOTIFICATION_ID = 1;

    // Parametry detekcji stuknięć
    private static final double TAP_THRESHOLD = 15.0;    // Próg przyspieszenia (m/s²)
    private static final int REQUIRED_TAPS = 3;           // Wymagana liczba stuknięć
    private static final long TAP_WINDOW_MS = 1500;        // Okno czasowe na 3 stuknięcia (ms)
    private static final long TAP_COOLDOWN_MS = 300;       // Minimalny czas między stuknięciami (ms)
    private static final long ANNOUNCE_COOLDOWN_MS = 3000;  // Cooldown po ogłoszeniu (ms)

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private PolishTimeSpeaker timeSpeaker;
    private PowerManager.WakeLock wakeLock;

    private long[] tapTimestamps = new long[REQUIRED_TAPS];
    private int tapCount = 0;
    private long lastTapTime = 0;
    private long lastAnnounceTime = 0;

    // Gravity filter
    private float[] gravity = new float[3];
    private static final float ALPHA = 0.8f;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Serwis utworzony");

        createNotificationChannel();
        timeSpeaker = new PolishTimeSpeaker(this);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // WakeLock żeby serwis działał z wyłączonym ekranem
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TimetalkerApp::TapDetector");
        wakeLock.acquire();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Serwis uruchomiony");

        startForeground(NOTIFICATION_ID, buildNotification());

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_GAME);
            Log.i(TAG, "Akcelerometr zarejestrowany");
        } else {
            Log.e(TAG, "Brak akcelerometru w urządzeniu!");
        }

        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        // Filtr dolnoprzepustowy do wyodrębnienia grawitacji
        gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0];
        gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1];
        gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2];

        // Przyspieszenie liniowe (bez grawitacji)
        float linearX = event.values[0] - gravity[0];
        float linearY = event.values[1] - gravity[1];
        float linearZ = event.values[2] - gravity[2];

        double magnitude = Math.sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ);

        long now = System.currentTimeMillis();

        // Sprawdź czy wykryto stuknięcie
        if (magnitude > TAP_THRESHOLD) {
            // Cooldown między stuknięciami
            if (now - lastTapTime < TAP_COOLDOWN_MS) {
                return;
            }

            // Cooldown po ogłoszeniu
            if (now - lastAnnounceTime < ANNOUNCE_COOLDOWN_MS) {
                return;
            }

            lastTapTime = now;

            // Przesuń tablicę i dodaj nowy timestamp
            if (tapCount < REQUIRED_TAPS) {
                tapTimestamps[tapCount] = now;
                tapCount++;
            } else {
                // Przesuń w lewo
                System.arraycopy(tapTimestamps, 1, tapTimestamps, 0, REQUIRED_TAPS - 1);
                tapTimestamps[REQUIRED_TAPS - 1] = now;
            }

            Log.d(TAG, "Stuknięcie wykryte! Liczba: " + tapCount + ", siła: " + String.format("%.1f", magnitude));

            // Sprawdź czy mamy 3 stuknięcia w oknie czasowym
            if (tapCount >= REQUIRED_TAPS) {
                long timeDiff = tapTimestamps[REQUIRED_TAPS - 1] - tapTimestamps[0];
                if (timeDiff <= TAP_WINDOW_MS) {
                    Log.i(TAG, "3 stuknięcia wykryte w " + timeDiff + "ms! Ogłaszam godzinę.");
                    timeSpeaker.speakCurrentTime();
                    lastAnnounceTime = now;
                    tapCount = 0; // Reset
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Nie potrzebujemy
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Detektor stuknięć",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Powiadomienie o działaniu detektora stuknięć w tle");
        channel.setShowBadge(false);

        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Czytanie godziny")
                .setContentText("Stuknij 3 razy w telefon żeby usłyszeć godzinę")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Serwis zatrzymany");

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        if (timeSpeaker != null) {
            timeSpeaker.shutdown();
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        super.onDestroy();
    }
}

