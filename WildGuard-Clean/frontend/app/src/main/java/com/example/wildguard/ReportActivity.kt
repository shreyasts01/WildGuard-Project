package com.example.wildguard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import com.google.android.material.button.MaterialButton

class ReportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        val submitBtn = findViewById<MaterialButton>(R.id.btnSubmitReport)
        submitBtn.setOnClickListener {
            Toast.makeText(this, "Report submitted!", Toast.LENGTH_SHORT).show()
            finish() // Close activity after submission
        }
    }
}
