package com.example.wildguard

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var loginBtn: Button
    private lateinit var switchBtn: Button
    private var isRegistering = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Auto-redirect if already logged in
        if (FirebaseAuth.getInstance().currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        emailField = findViewById(R.id.email)
        passwordField = findViewById(R.id.password)
        loginBtn = findViewById(R.id.loginBtn)
        switchBtn = findViewById(R.id.switchBtn)

        loginBtn.setOnClickListener { handleAuth() }
        switchBtn.setOnClickListener {
            isRegistering = !isRegistering
            updateUI()
        }

        updateUI()
    }

    private fun handleAuth() {
        val email = emailField.text.toString()
        val password = passwordField.text.toString()

        if (isRegistering) {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    Toast.makeText(this, "Registered!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
                }
        } else {
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateUI() {
        loginBtn.text = if (isRegistering) "Register" else "Login"
        switchBtn.text = if (isRegistering) "Already have an account? Login"
        else "Don't have an account? Register"
    }
}
