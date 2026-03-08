package com.timetalker.app;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Calendar;
import java.util.Locale;

public class PolishTimeSpeaker implements TextToSpeech.OnInitListener {

    private static final String TAG = "PolishTimeSpeaker";
    private TextToSpeech tts;
    private boolean isReady = false;

    // Godziny w formie żeńskiej porządkowej
    private static final String[] HOURS = {
            "dwunasta w nocy",   // 0 - północ
            "pierwsza",          // 1
            "druga",             // 2
            "trzecia",           // 3
            "czwarta",           // 4
            "piąta",             // 5
            "szósta",            // 6
            "siódma",            // 7
            "ósma",              // 8
            "dziewiąta",         // 9
            "dziesiąta",         // 10
            "jedenasta",         // 11
            "dwunasta",          // 12
            "trzynasta",         // 13
            "czternasta",        // 14
            "piętnasta",         // 15
            "szesnasta",         // 16
            "siedemnasta",       // 17
            "osiemnasta",        // 18
            "dziewiętnasta",     // 19
            "dwudziesta",        // 20
            "dwudziesta pierwsza", // 21
            "dwudziesta druga",    // 22
            "dwudziesta trzecia"   // 23
    };

    // Minuty - jedności
    private static final String[] MINUTES_ONES = {
            "", "jeden", "dwa", "trzy", "cztery",
            "pięć", "sześć", "siedem", "osiem", "dziewięć"
    };

    // Minuty 10-19
    private static final String[] MINUTES_TEENS = {
            "dziesięć", "jedenaście", "dwanaście", "trzynaście", "czternaście",
            "piętnaście", "szesnaście", "siedemnaście", "osiemnaście", "dziewiętnaście"
    };

    // Minuty - dziesiątki
    private static final String[] MINUTES_TENS = {
            "", "dziesięć", "dwadzieścia", "trzydzieści",
            "czterdzieści", "pięćdziesiąt"
    };

    public PolishTimeSpeaker(Context context) {
        tts = new TextToSpeech(context, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            Locale polish = new Locale("pl", "PL");
            int result = tts.setLanguage(polish);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Polski język TTS niedostępny, próbuję domyślny");
                tts.setLanguage(Locale.getDefault());
            }
            tts.setSpeechRate(0.9f);
            isReady = true;
            Log.i(TAG, "TTS zainicjalizowany pomyślnie");
        } else {
            Log.e(TAG, "Błąd inicjalizacji TTS: " + status);
        }
    }

    public void speakCurrentTime() {
        if (!isReady) {
            Log.w(TAG, "TTS nie jest jeszcze gotowy");
            return;
        }

        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        String timeText = buildTimeText(hour, minute);
        Log.i(TAG, "Mówię: " + timeText);
        tts.speak(timeText, TextToSpeech.QUEUE_FLUSH, null, "time_announcement");
    }

    public String buildTimeText(int hour, int minute) {
        StringBuilder sb = new StringBuilder();
        sb.append("Jest godzina ");
        sb.append(HOURS[hour]);

        if (minute > 0) {
            sb.append(" ");
            sb.append(minuteToWords(minute));
        }

        return sb.toString();
    }

    private String minuteToWords(int minute) {
        if (minute == 0) {
            return "";
        }

        if (minute < 10) {
            return "zero " + MINUTES_ONES[minute];
        }

        if (minute < 20) {
            return MINUTES_TEENS[minute - 10];
        }

        int tens = minute / 10;
        int ones = minute % 10;

        if (ones == 0) {
            return MINUTES_TENS[tens];
        }

        return MINUTES_TENS[tens] + " " + MINUTES_ONES[ones];
    }

    public boolean isReady() {
        return isReady;
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            isReady = false;
        }
    }
}




