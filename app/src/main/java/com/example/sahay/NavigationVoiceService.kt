package com.example.sahay

import android.app.Service
import android.content.Intent
import android.os.IBinder

class NavigationVoiceService : Service() {

    override fun onCreate() {
        super.onCreate()

        /*
         * IMPORTANT:
         *
         * This service does NOT start SpeechRecognizer.
         *
         * Therefore Sahāy's microphone is NOT
         * continuously listening during Google Maps
         * navigation.
         */
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}