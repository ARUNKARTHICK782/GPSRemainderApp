package com.example.gpsremindermapbox.listener

import android.util.Log
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver

class MyMapBoxNavigationObserver : MapboxNavigationObserver {
    var newMBN: MapboxNavigation? = null

    private val observerTag: String = "MyMapBoxNavigationObserver Says"


    override fun onAttached(mapboxNavigation: MapboxNavigation) {
        Log.i(observerTag, "Attached")
        newMBN = mapboxNavigation
    }

    override fun onDetached(mapboxNavigation: MapboxNavigation) {
        Log.i(observerTag, "Detached")
        newMBN = null
    }

}