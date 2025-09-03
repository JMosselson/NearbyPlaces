package com.example.nearbyplaces

import com.mapbox.geojson.Point

data class PlaceData(
    val name: String,
    val location: Point
)
