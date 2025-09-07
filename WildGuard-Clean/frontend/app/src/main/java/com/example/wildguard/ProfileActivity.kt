package com.example.wildguard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import com.google.android.material.button.MaterialButton

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val editBtn = findViewById<MaterialButton>(R.id.btnEditProfile)
        editBtn.setOnClickListener {
            Toast.makeText(this, "Profile editing coming soon!", Toast.LENGTH_SHORT).show()
        }
    }
}
