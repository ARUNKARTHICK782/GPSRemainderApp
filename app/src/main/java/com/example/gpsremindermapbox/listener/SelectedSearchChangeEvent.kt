package com.example.gpsremindermapbox.listener

import com.mapbox.search.autocomplete.PlaceAutocompleteSuggestion

interface SelectedSearchChangeEvent {
    fun onSelectedSearchChange(suggestion: PlaceAutocompleteSuggestion)
}