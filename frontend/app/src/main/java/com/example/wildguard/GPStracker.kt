package com.example.wildguard

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle

@Suppress("DEPRECATION") // To silence warnings for older LocationListener methods
class GPSTracker(private val context: Context) : LocationListener {

    var latitude: Double = 0.0
        private set
    var longitude: Double = 0.0
        private set

    private var locationManager: LocationManager? = null

    /**
     * Attempts to get the current location using GPS provider.
     * Returns true if location can be retrieved, false otherwise.
     */
    @SuppressLint("MissingPermission") // You must ensure permissions are granted before calling
    fun canGetLocation(): Boolean {
        return try {
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val isGPSEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false

            if (isGPSEnabled) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L, // 1 second update interval
                    1f,    // 1 meter distance change
                    this
                )

                locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { loc ->
                    latitude = loc.latitude
                    longitude = loc.longitude
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun onLocationChanged(location: Location) {
        latitude = location.latitude
        longitude = location.longitude
    }

    // These are deprecated but included for backward compatibility
    @Deprecated("Deprecated in Android Q")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}
}
