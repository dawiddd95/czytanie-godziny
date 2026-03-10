package com.timetalker.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class TapDetectorService extends Service implements SensorEventListener {

    private static final String TAG = "TapDetectorService";
    private static final String CHANNEL_ID = "shake_detector_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final String PREFS_NAME = "timetalker_prefs";
    private static final String KEY_SHAKE_COUNT = "shake_count";
    static final String ACTION_SHAKE_DETECTED = "com.timetalker.app.SHAKE_DETECTED";

    // Parametry detekcji potrząśnięcia
    private static final double SHAKE_THRESHOLD = 4.5;      // Próg przyspieszenia (m/s²)
    private static final int SHAKE_MOVEMENTS = 4;          // Liczba ruchów do wykrycia potrząśnięcia
    private static final long SHAKE_WINDOW_MS = 800;      // Okno czasowe na potrząśnięcie (ms)
    private static final long SHAKE_PEAK_COOLDOWN_MS = 80; // Min. czas między liczeniem ruchów (ms)
    private static final long ANNOUNCE_COOLDOWN_MS = 3000; // Cooldown po ogłoszeniu (ms)

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private PolishTimeSpeaker timeSpeaker;
    private PowerManager.WakeLock wakeLock;
    private Handler mainHandler;

    private long[] shakeTimestamps = new long[SHAKE_MOVEMENTS];
    private int shakeCount = 0;
    private boolean wasAboveThreshold = false;
    private long lastPeakTime = 0;
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
        mainHandler = new Handler(Looper.getMainLooper());

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

        // Wykrywanie potrząśnięcia: liczymy przejścia z poniżej progu do powyżej progu
        boolean isAboveThreshold = magnitude > SHAKE_THRESHOLD;

        if (isAboveThreshold && !wasAboveThreshold) {
            // Nowe przejście w górę = jeden ruch potrząśnięcia
            wasAboveThreshold = true;

            if (now - lastAnnounceTime < ANNOUNCE_COOLDOWN_MS) {
                return;
            }
            if (now - lastPeakTime < SHAKE_PEAK_COOLDOWN_MS) {
                return;
            }

            lastPeakTime = now;

            if (shakeCount < SHAKE_MOVEMENTS) {
                shakeTimestamps[shakeCount] = now;
                shakeCount++;
            } else {
                System.arraycopy(shakeTimestamps, 1, shakeTimestamps, 0, SHAKE_MOVEMENTS - 1);
                shakeTimestamps[SHAKE_MOVEMENTS - 1] = now;
            }

            Log.d(TAG, "Ruch potrząśnięcia: " + shakeCount + ", siła: " + String.format("%.1f", magnitude));

            if (shakeCount >= SHAKE_MOVEMENTS) {
                long timeDiff = shakeTimestamps[SHAKE_MOVEMENTS - 1] - shakeTimestamps[0];
                if (timeDiff <= SHAKE_WINDOW_MS) {
                    Log.i(TAG, "Potrząśnięcie wykryte w " + timeDiff + "ms! Ogłaszam godzinę.");
                    incrementAndBroadcastShakeCount();
                    announceTimeWithRetry();
                    lastAnnounceTime = now;
                    shakeCount = 0;
                }
            }
        } else if (!isAboveThreshold) {
            wasAboveThreshold = false;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Nie potrzebujemy
    }

    private void incrementAndBroadcastShakeCount() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int totalCount = prefs.getInt(KEY_SHAKE_COUNT, 0) + 1;
        prefs.edit().putInt(KEY_SHAKE_COUNT, totalCount).apply();

        Intent intent = new Intent(ACTION_SHAKE_DETECTED);
        intent.setPackage(getPackageName());
        intent.putExtra("count", totalCount);
        sendBroadcast(intent);
    }

    /** Ogłasza godzinę, z ponowieniem próby jeśli TTS nie jest jeszcze gotowy */
    private void announceTimeWithRetry() {
        if (timeSpeaker.isReady()) {
            timeSpeaker.speakCurrentTime();
        } else {
            Log.w(TAG, "TTS nie gotowy, ponawiam za 2 sekundy");
            mainHandler.postDelayed(() -> {
                if (timeSpeaker != null && timeSpeaker.isReady()) {
                    timeSpeaker.speakCurrentTime();
                } else {
                    Log.w(TAG, "TTS nadal nie gotowy - spróbuj potrząsnąć ponownie za chwilę");
                }
            }, 2000);
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Detektor potrząśnięcia",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Powiadomienie o działaniu detektora potrząśnięcia w tle");
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
                .setContentText("Potrząśnij telefonem żeby usłyszeć godzinę")
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

        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }

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




