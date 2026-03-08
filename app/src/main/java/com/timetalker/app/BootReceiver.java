package com.timetalker.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            Log.i(TAG, "Boot wykryty, sprawdzam czy uruchomić serwis...");

            SharedPreferences prefs = context.getSharedPreferences("timetalker_prefs",
                    Context.MODE_PRIVATE);
            boolean wasEnabled = prefs.getBoolean("service_enabled", false);

            if (wasEnabled) {
                Log.i(TAG, "Serwis był włączony, uruchamiam ponownie...");
                Intent serviceIntent = new Intent(context, TapDetectorService.class);
                ContextCompat.startForegroundService(context, serviceIntent);
            }
        }
    }
}

