package com.example.gpsremindermapbox

import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation

class NodePoint(
    var point: Point,
    var pin: PointAnnotation,
    var pos: Int,
    var priority: Int = -1,
    var name: String,
    var date: String,
    var location: String
) {
    override fun toString(): String {
        return "Name: $name - Location: $location - Priority: $priority - Pos: $pos - Date: $date"
    }
}