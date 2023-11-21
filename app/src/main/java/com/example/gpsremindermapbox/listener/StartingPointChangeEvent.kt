package com.example.gpsremindermapbox.listener

interface StartingPointChangeEvent {
    fun onStartPointChange(oldStart: Int, newStart: Int)
}