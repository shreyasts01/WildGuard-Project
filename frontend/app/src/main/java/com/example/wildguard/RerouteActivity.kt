package com.example.wildguard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import com.google.android.material.button.MaterialButton

class RerouteActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reroute)

        val rerouteBtn = findViewById<MaterialButton>(R.id.btnFindReroute)
        rerouteBtn.setOnClickListener {
            Toast.makeText(this, "Finding safer route...", Toast.LENGTH_SHORT).show()
        }
    }
}
