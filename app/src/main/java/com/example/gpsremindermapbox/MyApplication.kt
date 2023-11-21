package com.example.gpsremindermapbox

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

    }

    companion object {
        var appContext: Context? = null
            private set

        const val channelID = "GPSNavigationService"
    }


}