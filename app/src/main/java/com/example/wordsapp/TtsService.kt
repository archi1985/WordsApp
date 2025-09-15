package com.example.myapp   // замени на твой пакет

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsService : Service() {
    private lateinit var tts: TextToSpeech

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                tts.speak("Hello world", TextToSpeech.QUEUE_FLUSH, null, "tts1")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
