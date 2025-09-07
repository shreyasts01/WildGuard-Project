package com.example.wildguard

import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class DetectedAnimalsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val db = FirebaseFirestore.getInstance()
    private var lastUserLat: Double = 0.0
    private var lastUserLng: Double = 0.0
    private lateinit var adapter: DetectedAnimalsAdapter

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detected_animals)

        recyclerView = findViewById(R.id.recyclerViewAnimals)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DetectedAnimalsAdapter()
        recyclerView.adapter = adapter

        lastUserLat = intent.getDoubleExtra("user_lat", 0.0)
        lastUserLng = intent.getDoubleExtra("user_lng", 0.0)

        loadDetectedAnimals()
    }

    private fun loadDetectedAnimals() {
        db.collection("images")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val items = mutableListOf<DetectedAnimal>()
                for (doc in snapshot.documents) {
                    val label = doc.getString("label") ?: "Unknown"
                    val lat = doc.getDouble("latitude") ?: 0.0
                    val lng = doc.getDouble("longitude") ?: 0.0
                    val ts = doc.getTimestamp("timestamp")?.toDate() ?: Date()

                    val distance = if (lastUserLat != 0.0 && lastUserLng != 0.0) {
                        val results = FloatArray(1)
                        Location.distanceBetween(lastUserLat, lastUserLng, lat, lng, results)
                        results[0]
                    } else 0f

                    items.add(DetectedAnimal(label, distance, ts))
                }
                adapter.setData(items)
            }
            .addOnFailureListener {
                Log.e("DetectedAnimalsActivity", "Failed to load detected animals", it)
            }
    }
}

data class DetectedAnimal(val label: String, val distanceMeters: Float, val timestamp: Date)
