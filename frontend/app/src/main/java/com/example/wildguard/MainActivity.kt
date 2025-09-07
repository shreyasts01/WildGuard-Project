package com.example.wildguard

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.Location
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

data class ShownAlert(val label: String, val timestamp: Long, val imageUrl: String)
data class PlaceInfo(val name: String, val latLng: LatLng, val distanceMeters: Float)

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var topAppBar: MaterialToolbar
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var locationStatusIndicator: ImageView
    private lateinit var mapCard: MaterialCardView
    private lateinit var quickActionsCard: MaterialCardView
    private lateinit var distanceAlertBox: MaterialCardView
    private lateinit var statsRow: LinearLayout
    private lateinit var sosButton: MaterialButton
    private lateinit var enableLocationBtn: MaterialButton
    private lateinit var driveModeToggle: MaterialButton
    private lateinit var quickRerouteBtn: MaterialButton
    private lateinit var reportFalseBtn: MaterialButton

    private lateinit var animalCount: TextView
    private lateinit var avgRespTime: TextView
    private lateinit var roadSafe: TextView
    private lateinit var alertTimestampText: TextView
    private lateinit var alertText: TextView
    private lateinit var animalSnapshot: ImageView

    private lateinit var prefs: SharedPreferences
    private var locationSharingEnabled = false
    private var drivingModeOn = false
    private var locationSharingActive = false
    private var alertCount = 0
    private var avgResponseSeconds = 0.0
    private var responseSamples = 0
    private var lastImageId: String? = null
    private var lastUserLocation: Location? = null
    private val shownAlertsList = mutableListOf<ShownAlert>()

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private val handler = Handler(Looper.getMainLooper())

    private val CAMERA_ALERT_RADIUS_M = 1000.0
    private val CHANNEL_ID = "wildguard_channel"
    private val pollingIntervalMs = 5000L
    private val GOOGLE_PLACES_API_KEY = "YOUR_API_KEY"

    private val updateTask = object : Runnable {
        override fun run() {
            if (!drivingModeOn) {
                clearAlertUI()
                lastImageId = null
            } else {
                checkUserNearCamerasAndFetchImage()
            }
            handler.postDelayed(this, pollingIntervalMs)
        }
    }


    private fun saveSettingsToUser() {
        val user = auth.currentUser ?: return
        val settingUpdate = mapOf(
            "emergency_contact" to prefs.getString("emergency_contact", ""),
            "enable_alarm_sounds" to prefs.getBoolean("enable_alarm_sounds", true),
            "enable_push_notifications" to prefs.getBoolean("enable_push_notifications", true),
            "enable_vibrations" to prefs.getBoolean("enable_vibrations", true),
            "enable_tts_alerts" to prefs.getBoolean("enable_tts_alerts", false),
            "location_sharing_enabled" to prefs.getBoolean("location_sharing_enabled", false)
        )
        firestore.collection("users").document(user.uid).set(settingUpdate, SetOptions.merge())
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) toggleLiveLocationSharing(true)
            else Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("WildGuardPrefs", Context.MODE_PRIVATE)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        tts = TextToSpeech(this, this)
        createNotificationChannel()
        initializeViews()
        setupLocationSystem()
        setupListeners()
        loadPreferences()
        updateLocationStatusUI()
        ensureUserDocument()
        if (drivingModeOn) handler.post(updateTask)
    }

    private fun initializeViews() {
        topAppBar = findViewById(R.id.topAppBar)
        bottomNav = findViewById(R.id.bottomNav)
        locationStatusIndicator = findViewById(R.id.locationStatusIndicator)
        mapCard = findViewById(R.id.mapCard)
        quickActionsCard = findViewById(R.id.quickActionsCard)
        distanceAlertBox = findViewById(R.id.distanceAlertBox)
        statsRow = findViewById(R.id.statsRow)
        sosButton = findViewById(R.id.sosButton)
        enableLocationBtn = findViewById(R.id.enableLocationBtn)
        driveModeToggle = findViewById(R.id.driveModeToggle)
        quickRerouteBtn = findViewById(R.id.quickRerouteBtn)
        reportFalseBtn = findViewById(R.id.reportFalseBtn)
        animalCount = findViewById(R.id.animalCount)
        avgRespTime = findViewById(R.id.avgRespTime)
        roadSafe = findViewById(R.id.roadSafe)
        alertTimestampText = findViewById(R.id.alertTimestampText)
        alertText = findViewById(R.id.alertText)
        animalSnapshot = findViewById(R.id.animalSnapshot)
        setSupportActionBar(topAppBar)
    }

    private fun setupListeners() {
        driveModeToggle.setOnClickListener {
            drivingModeOn = !drivingModeOn
            driveModeToggle.text = if (drivingModeOn) "Stop Driving" else "Driver Mode"
            if (drivingModeOn) {
                alertCount = 0
                avgResponseSeconds = 0.0
                responseSamples = 0
                lastImageId = null
                animalCount.text = "0"
                avgRespTime.text = "0s"
                shownAlertsList.clear()
                if (!locationSharingActive) {
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    } else toggleLiveLocationSharing(true)
                }
                handler.post(updateTask)
            } else {
                toggleLiveLocationSharing(false)
                clearAlertUI()
                handler.removeCallbacks(updateTask)
            }
        }

        enableLocationBtn.setOnClickListener {
            if (locationSharingActive) toggleLiveLocationSharing(false)
            else if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else toggleLiveLocationSharing(true)
        }

        quickRerouteBtn.setOnClickListener { openGoogleMapsLiveLocation() }

        reportFalseBtn.setOnClickListener {
            if (lastUserLocation == null)
                Toast.makeText(this, "Current location not available", Toast.LENGTH_SHORT).show()
            else fetchNearbyEmergencyPlaces()
        }

        sosButton.setOnClickListener {
            val emergencyContact = prefs.getString("emergency_contact", "")
            if (emergencyContact.isNullOrEmpty()) {
                Toast.makeText(
                    this,
                    "Please set an emergency contact in settings.",
                    Toast.LENGTH_LONG
                ).show()
                showSettingsDialog()
            } else sendSOSAlert(emergencyContact)
        }

        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_settings -> {
                    showSettingsDialog(); true
                }

                R.id.nav_map -> {
                    startActivity(Intent(this, MapActivity::class.java)); true
                }

                else -> false
            }
        }

        animalCount.setOnClickListener {
            if (shownAlertsList.isEmpty()) {
                Toast.makeText(this, "No alerts yet", Toast.LENGTH_SHORT).show()
            } else {
                showAlertHistoryDialog()
            }
        }
    }

    private fun fetchNearbyEmergencyPlaces() {
        val loc = lastUserLocation
        if (loc == null) {
            Toast.makeText(this, "Current location not available", Toast.LENGTH_SHORT).show()
            return
        }

        val types = listOf("hospital", "police", "gas_station")
        val placeTypeLabels = mapOf(
            "hospital" to "Hospitals",
            "police" to "Police Stations",
            "gas_station" to "Petrol Pumps"
        )

        val allResults = mutableListOf<PlaceInfo>()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (type in types) {
                    val urlStr =
                        "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${loc.latitude},${loc.longitude}&radius=5000&type=$type&key=$GOOGLE_PLACES_API_KEY"
                    val response = URL(urlStr).readText()
                    val json = JSONObject(response)
                    val results = json.getJSONArray("results")
                    for (i in 0 until results.length()) {
                        val obj = results.getJSONObject(i)
                        val name = obj.getString("name")
                        val lat = obj.getJSONObject("geometry").getJSONObject("location").getDouble("lat")
                        val lng = obj.getJSONObject("geometry").getJSONObject("location").getDouble("lng")
                        val placeLoc = Location("").apply { latitude = lat; longitude = lng }
                        val dist = loc.distanceTo(placeLoc)
                        allResults.add(PlaceInfo("${placeTypeLabels[type]}: $name", LatLng(lat, lng), dist))
                    }
                }

                withContext(Dispatchers.Main) {
                    if (allResults.isEmpty()) {
                        Toast.makeText(this@MainActivity, "No nearby services found", Toast.LENGTH_SHORT).show()
                    } else {
                        showPlacesDialog(allResults)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error fetching places: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showPlacesDialog(places: List<PlaceInfo>) {
        val items = places.sortedBy { it.distanceMeters }
            .map { "${it.name} - ${(it.distanceMeters / 1000).toDouble().format(2)} km" }

            .toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Nearby Emergency Services")
            .setItems(items) { _, which ->
                navigateToLocation(places[which].latLng.latitude, places[which].latLng.longitude)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    private fun navigateToLocation(lat: Double, lng: Double) {
        val uri = Uri.parse("google.navigation:q=$lat,$lng")
        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
        mapIntent.setPackage("com.google.android.apps.maps")
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            Toast.makeText(this, "Google Maps not found", Toast.LENGTH_SHORT).show()
        }
    }


    private fun openGoogleMapsLiveLocation() {
        val loc = lastUserLocation
        if (loc == null) {
            Toast.makeText(this, "No location available", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}(My+Location)")
        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
        mapIntent.setPackage("com.google.android.apps.maps")
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            Toast.makeText(this, "Google Maps not found", Toast.LENGTH_SHORT).show()
        }
    }


    private fun loadPreferences() {
        locationSharingEnabled = prefs.getBoolean("location_sharing_enabled", false)
    }

    private fun ensureUserDocument() {
        val user = auth.currentUser ?: return
        val userData = hashMapOf(
            "email" to (user.email ?: ""),
            "emergency_contact" to prefs.getString("emergency_contact", ""),
            "enable_alarm_sounds" to prefs.getBoolean("enable_alarm_sounds", true),
            "enable_push_notifications" to prefs.getBoolean("enable_push_notifications", true),
            "enable_vibrations" to prefs.getBoolean("enable_vibrations", true),
            "enable_tts_alerts" to prefs.getBoolean("enable_tts_alerts", false),
            "location_sharing_enabled" to prefs.getBoolean("location_sharing_enabled", false)
        )
        firestore.collection("users").document(user.uid).set(userData, SetOptions.merge())
    }

    private fun setupLocationSystem() {
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            4000L
        ).setMinUpdateIntervalMillis(3500).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                lastUserLocation = location
                if (locationSharingActive) {
                    val user = auth.currentUser ?: return
                    val locData = mapOf(
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "timestamp" to com.google.firebase.Timestamp.now()
                    )
                    firestore.collection("users").document(user.uid)
                        .set(mapOf("location" to locData), SetOptions.merge())
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        locationSharingActive = true
        updateLocationStatusUI()
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        locationSharingActive = false
        updateLocationStatusUI()
    }

    private fun toggleLiveLocationSharing(enable: Boolean) {
        if (enable) startLocationUpdates() else stopLocationUpdates()
        locationSharingEnabled = enable
        prefs.edit().putBoolean("location_sharing_enabled", enable).apply()
        updateAppBarColors()
        ensureUserDocument()
    }

    private fun updateAppBarColors(isDanger: Boolean = false) {
        val colorResId = if (locationSharingEnabled) {
            if (isDanger) R.color.appbar_danger else R.color.appbar_safe
        } else R.color.appbar_unknown
        val color = ContextCompat.getColor(this, colorResId)
        topAppBar.backgroundTintList = ColorStateList.valueOf(color)
        bottomNav.backgroundTintList = ColorStateList.valueOf(color)
        window.statusBarColor = color
    }

    private fun updateLocationStatusUI() {
        if (locationSharingActive) {
            locationStatusIndicator.setImageResource(R.drawable.ic_location_on)
            enableLocationBtn.text = "Disable Location"
        } else {
            locationStatusIndicator.setImageResource(R.drawable.ic_location_off)
            enableLocationBtn.text = "Enable Location"
        }
    }


    private fun checkUserNearCamerasAndFetchImage() {
        val userLocation = lastUserLocation ?: return
        firestore.collection("cameras").get().addOnSuccessListener { cameras ->
            var nearestDistance = Double.MAX_VALUE
            var nearestName = ""
            var found = false
            for (doc in cameras) {
                val lat = doc.getDouble("latitude") ?: continue
                val lng = doc.getDouble("longitude") ?: continue
                val cameraLocation = Location("").apply {
                    latitude = lat
                    longitude = lng
                }
                val dist = userLocation.distanceTo(cameraLocation)
                if (dist <= 1000 && dist < nearestDistance) {
                    nearestDistance = dist.toDouble()
                    nearestName = doc.getString("name") ?: "Camera"
                    found = true
                }
            }
            if (found) {
                showCameraAhead(nearestName, nearestDistance)
                fetchLatestImageFromFirestore()
            } else clearAlertUI()
        }
    }

    private fun fetchLatestImageFromFirestore() {
        firestore.collection("images").orderBy("timestamp", Query.Direction.DESCENDING).limit(1)
            .get().addOnSuccessListener { snaps ->
                val doc = snaps.documents.firstOrNull() ?: return@addOnSuccessListener
                val imgUrl = doc.getString("img_url") ?: return@addOnSuccessListener
                val label = doc.getString("label") ?: "Unknown"
                val ts = doc.getTimestamp("timestamp")?.toDate()?.time ?: return@addOnSuccessListener
                if (System.currentTimeMillis() - ts > 15_000) return@addOnSuccessListener
                if (doc.id != lastImageId) {
                    lastImageId = doc.id
                    alertCount++
                    val respSec = ((System.currentTimeMillis() - ts) / 1000).coerceAtLeast(0)
                    responseSamples++
                    avgResponseSeconds = ((avgResponseSeconds * (responseSamples - 1)) + respSec) / responseSamples
                    runOnUiThread {
                        animalCount.text = alertCount.toString()
                        avgRespTime.text = "${(avgResponseSeconds * 10).roundToInt() / 10.0}s"
                        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        alertTimestampText.text = "Detected at: ${sdf.format(Date(ts))}"
                        showAlertWithImage(imgUrl, label)
                    }
                }
            }
    }

    private fun showCameraAhead(name: String, dist: Double) {
        distanceAlertBox.visibility = View.VISIBLE
        animalSnapshot.visibility = View.GONE
        alertText.text = "📷 Camera ahead"
        alertTimestampText.text = "$name • ${if (dist >= 1000) "%.1f km".format(dist / 1000) else "%.0f m".format(dist)} ahead"
        setRoadStatusSafe()
    }

    private fun showAlertWithImage(imgUrl: String, label: String) {
        shownAlertsList.add(ShownAlert(label, System.currentTimeMillis(), imgUrl))
        animalSnapshot.visibility = View.VISIBLE
        alertText.text = "Animal detected: $label"
        Glide.with(this).load(imgUrl).centerCrop().into(animalSnapshot)
        setRoadStatusDanger()
        if (prefs.getBoolean("enable_vibrations", true)) vibratePhone()
        if (prefs.getBoolean("enable_alarm_sounds", true)) playAlarm()
        if (prefs.getBoolean("enable_tts_alerts", false)) tts?.speak("$label detected ahead", TextToSpeech.QUEUE_FLUSH, null, "")
        if (prefs.getBoolean("enable_push_notifications", true)) sendNotification("🚨 $label Detected", "Tap to open WildGuard")
    }

    private fun showAlertHistoryDialog() {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val items = shownAlertsList.map { "${sdf.format(Date(it.timestamp))} - ${it.label}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Alerts Shown (${shownAlertsList.size})")
            .setItems(items) { _, which -> showPastAlertImage(shownAlertsList[which]) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showPastAlertImage(alert: ShownAlert) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_alert_image, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.dialogAnimalImage)
        val textLabel = dialogView.findViewById<TextView>(R.id.dialogAnimalLabel)
        textLabel.text = "${alert.label} • ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(alert.timestamp))}"
        Glide.with(this).load(alert.imageUrl).into(imageView)
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun clearAlertUI() {
        distanceAlertBox.visibility = View.GONE
        animalSnapshot.visibility = View.GONE
        alertText.text = ""
        alertTimestampText.text = ""
        setRoadStatusSafe()
    }

    private fun playAlarm() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(this, R.raw.short_notification)
        mediaPlayer?.start()
    }

    private fun vibratePhone() {
        val vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vib.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vib.vibrate(350)
    }

    private fun sendNotification(title: String, message: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning_animal)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun sendSOSAlert(contact: String) {
        if (contact.isBlank()) {
            Toast.makeText(this, "No emergency contact set", Toast.LENGTH_SHORT).show()
            return
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 101)
            return
        }
        firestore.collection("sos_alerts").add(
            mapOf(
                "userId" to auth.currentUser?.uid,
                "timestamp" to System.currentTimeMillis(),
                "contact" to contact
            )
        )
        val callIntent = Intent(Intent.ACTION_CALL).apply { data = Uri.parse("tel:$contact") }
        startActivity(callIntent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            val contact = prefs.getString("emergency_contact", "")
            if (!contact.isNullOrEmpty()) sendSOSAlert(contact)
        } else {
            Toast.makeText(this, "CALL_PHONE permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
    }

    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val builder = AlertDialog.Builder(this).setView(dialogView).setTitle("Settings")
        val dialog = builder.create()
        dialog.show()
        val userEmail = dialogView.findViewById<TextView>(R.id.userEmail)
        val logoutBtn = dialogView.findViewById<MaterialButton>(R.id.logoutBtn)
        val emergencyContactInput = dialogView.findViewById<TextInputEditText>(R.id.emergencyContactInput)
        val saveEmergencyBtn = dialogView.findViewById<MaterialButton>(R.id.saveEmergencyBtn)
        val alarmSwitch = dialogView.findViewById<SwitchMaterial>(R.id.alarmSwitch)
        val notifSwitch = dialogView.findViewById<SwitchMaterial>(R.id.notifSwitch)
        val vibrateSwitch = dialogView.findViewById<SwitchMaterial>(R.id.vibrateSwitch)
        val ttsSwitch = dialogView.findViewById<SwitchMaterial>(R.id.ttsSwitch)
        val email = auth.currentUser?.email ?: "Not logged in"
        userEmail.text = email
        emergencyContactInput.setText(prefs.getString("emergency_contact", ""))
        alarmSwitch.isChecked = prefs.getBoolean("enable_alarm_sounds", true)
        notifSwitch.isChecked = prefs.getBoolean("enable_push_notifications", true)
        vibrateSwitch.isChecked = prefs.getBoolean("enable_vibrations", true)
        ttsSwitch.isChecked = prefs.getBoolean("enable_tts_alerts", false)

        saveEmergencyBtn.setOnClickListener {
            with(prefs.edit()) {
                putString("emergency_contact", emergencyContactInput.text.toString())
                putBoolean("enable_alarm_sounds", alarmSwitch.isChecked)
                putBoolean("enable_push_notifications", notifSwitch.isChecked)
                putBoolean("enable_vibrations", vibrateSwitch.isChecked)
                putBoolean("enable_tts_alerts", ttsSwitch.isChecked)
                apply()
            }
            saveSettingsToUser()
            dialog.dismiss()
        }

        logoutBtn.setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun setRoadStatusSafe() {
        roadSafe.text = "Safe"
        roadSafe.setTextColor(ContextCompat.getColor(this, R.color.safe_green))
    }

    private fun setRoadStatusDanger() {
        roadSafe.text = "Danger"
        roadSafe.setTextColor(ContextCompat.getColor(this, R.color.danger_red))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "WildGuard Alerts", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Animal detection alerts"
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateTask)
        stopLocationUpdates()
        mediaPlayer?.release()
        tts?.shutdown()
        super.onDestroy()
    }
}
