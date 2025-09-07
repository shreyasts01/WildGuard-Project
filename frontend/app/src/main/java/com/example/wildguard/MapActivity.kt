package com.example.wildguard

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MapActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load osmdroid configuration
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))

        setContentView(R.layout.activity_map)
        map = findViewById(R.id.mapView)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        val mapController: IMapController = map.controller
        mapController.setZoom(15.0)

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not signed in", Toast.LENGTH_SHORT).show()
            val defaultPoint = GeoPoint(12.9716, 77.5946)
            mapController.setCenter(defaultPoint)
            addMarker(defaultPoint, "Default Location")
            return
        }
        val userDocRef = db.collection("users").document(user.uid)
        userDocRef.get().addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                val locationMap = document.get("location") as? Map<*, *>
                if (locationMap != null) {
                    val lat = locationMap["latitude"] as? Double
                    val lng = locationMap["longitude"] as? Double
                    if (lat != null && lng != null) {
                        val userPoint = GeoPoint(lat, lng)
                        mapController.setCenter(userPoint)
                        addMarker(userPoint, "Your Location")
                    } else {
                        showDefaultLocation(mapController)
                    }
                } else {
                    showDefaultLocation(mapController)
                }
            } else {
                showDefaultLocation(mapController)
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to load location", Toast.LENGTH_SHORT).show()
            showDefaultLocation(mapController)
        }
    }

    private fun addMarker(point: GeoPoint, title: String) {
        val marker = Marker(map)
        marker.position = point
        marker.title = title
        map.overlays.add(marker)
        map.invalidate()
    }

    private fun showDefaultLocation(mapController: IMapController) {
        val defaultPoint = GeoPoint(12.9716, 77.5946)
        mapController.setCenter(defaultPoint)
        addMarker(defaultPoint, "Default Location")
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}
