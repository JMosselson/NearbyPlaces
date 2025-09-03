package com.example.nearbyplaces

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.nearbyplaces.databinding.ActivityMainBinding
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorBearingChangedListener
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mapView: MapView
    private var userLocation: Point? = null
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private var searchRadiusKm = 5.0
    private var isFirstLocationUpdate = true // Flag to handle initial camera position and annotation load

    // A sample list of places. In a real app, this would come from an API.
    private val samplePlaceData = listOf(
        PlaceData("Park", Point.fromLngLat(28.0436, -26.1952)),
        PlaceData("Museum", Point.fromLngLat(28.0399, -26.2023)),
        PlaceData("Cafe", Point.fromLngLat(28.0478, -26.1984)),
        PlaceData("Library", Point.fromLngLat(28.0410, -26.1905)),
        PlaceData("Theater", Point.fromLngLat(28.0495, -26.2051)),
        PlaceData("Restaurant (Far)", Point.fromLngLat(28.15, -26.25)) // A place outside the initial radius
    )

    private val onIndicatorBearingChangedListener = OnIndicatorBearingChangedListener {
        mapView.getMapboxMap().setCamera(CameraOptions.Builder().bearing(it).build())
    }

    private val onIndicatorPositionChangedListener = OnIndicatorPositionChangedListener {
        userLocation = it
        mapView.getMapboxMap().setCamera(CameraOptions.Builder().center(it).build())
        mapView.gestures.focalPoint = mapView.getMapboxMap().pixelForCoordinate(it)

        // Only update annotations on the first location update to avoid redundant calls
        if (isFirstLocationUpdate) {
            addPlaceAnnotations()
            isFirstLocationUpdate = false
        }
    }

    private val onMoveListener = object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) {
            onCameraTrackingDismissed()
        }

        override fun onMove(detector: MoveGestureDetector): Boolean {
            return false
        }

        override fun onMoveEnd(detector: MoveGestureDetector) {}
    }


    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                        permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    initLocationComponent()
                    setupGesturesListener()
                }
                else -> {
                    Toast.makeText(this, "Location permission not granted.", Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mapView = binding.mapView

        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) {
            if (hasLocationPermission()) {
                initLocationComponent()
                setupGesturesListener()
            } else {
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            setupAnnotations()
            setupSlider()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupAnnotations() {
        val annotationApi = mapView.annotations
        pointAnnotationManager = annotationApi.createPointAnnotationManager()
    }

    private fun addPlaceAnnotations() {
        pointAnnotationManager.deleteAll() // Clear existing annotations

        val icon = BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground)

        samplePlaceData.forEach { place ->
            userLocation?.let { userPoint ->
                val placeLocation = Location("").apply {
                    latitude = place.location.latitude()
                    longitude = place.location.longitude()
                }
                val currentUserLocation = Location("").apply {
                    latitude = userPoint.latitude()
                    longitude = userPoint.longitude()
                }

                val distanceInMeters = currentUserLocation.distanceTo(placeLocation)
                if (distanceInMeters <= searchRadiusKm * 1000) {
                    val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                        .withPoint(place.location)
                        .withTextField(place.name)
                        .withTextOffset(listOf(0.0, -2.0))
                        .withTextColor("#000000")
                        .withTextSize(12.0)
                        .withIconImage(icon)
                    pointAnnotationManager.create(pointAnnotationOptions)
                }
            }
        }
    }


    private fun setupSlider() {
        binding.distanceLabel.text = "Search Radius: ${searchRadiusKm.toInt()} km"
        binding.distanceSlider.value = searchRadiusKm.toFloat()
        binding.distanceSlider.addOnChangeListener { _, value, _ ->
            searchRadiusKm = value.toDouble()
            binding.distanceLabel.text = "Search Radius: ${value.toInt()} km"
            addPlaceAnnotations()
        }
    }

    private fun setupGesturesListener() {
        mapView.gestures.addOnMoveListener(onMoveListener)
    }

    private fun initLocationComponent() {
        val locationComponentPlugin = mapView.location
        locationComponentPlugin.updateSettings {
            this.enabled = true
            this.locationPuck = LocationPuck2D()
        }
        locationComponentPlugin.addOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        locationComponentPlugin.addOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
    }

    private fun onCameraTrackingDismissed() {
        Toast.makeText(this, "Camera tracking dismissed", Toast.LENGTH_SHORT).show()
        mapView.location.removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        mapView.location.removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.location.removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        mapView.location.removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        mapView.gestures.removeOnMoveListener(onMoveListener)
    }
}
