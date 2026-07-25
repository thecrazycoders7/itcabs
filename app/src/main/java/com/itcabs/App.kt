package com.itcabs

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create the trips channel up front so background FCM messages land on the high-importance
        // channel (the system otherwise auto-creates a silent default for the first background push).
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("itcabs_trips", "Trips", NotificationManager.IMPORTANCE_HIGH),
        )
    }
}
