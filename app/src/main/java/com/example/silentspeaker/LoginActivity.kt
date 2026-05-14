package com.example.silentspeaker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etDisplayName = findViewById<EditText>(R.id.etDisplayName)
        val labelDisplayName = findViewById<TextView>(R.id.labelDisplayName)
        val tvCardTitle = findViewById<TextView>(R.id.tvCardTitle)
        val tvCardSubtitle = findViewById<TextView>(R.id.tvCardSubtitle)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvSignUpLink = findViewById<TextView>(R.id.tvSignUpLink)

        tvSignUpLink.setOnClickListener {
            isRegisterMode = !isRegisterMode
            if (isRegisterMode) {
                labelDisplayName.visibility = View.VISIBLE
                etDisplayName.visibility = View.VISIBLE
                tvCardTitle.text = "Create Account"
                tvCardSubtitle.text = "Sign up to get started"
                btnLogin.text = "SIGN UP"
                tvSignUpLink.text = "Already have an account?  Sign In"
            } else {
                labelDisplayName.visibility = View.GONE
                etDisplayName.visibility = View.GONE
                tvCardTitle.text = "Welcome Back"
                tvCardSubtitle.text = "Sign in to continue"
                btnLogin.text = "SIGN IN"
                tvSignUpLink.text = "Don't have an account?  Sign Up"
            }
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isRegisterMode) {
                val displayName = etDisplayName.text.toString().trim()
                if (displayName.isEmpty()) {
                    Toast.makeText(this, "Please enter your display name!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                btnLogin.isEnabled = false
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName)
                            .build()
                        result.user?.updateProfile(profileUpdates)
                            ?.addOnCompleteListener {
                                UserSync.pull(this) {
                                    startActivity(Intent(this, HomeActivity::class.java))
                                    finish()
                                }
                            }
                    }
                    .addOnFailureListener { e ->
                        btnLogin.isEnabled = true
                        Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                    }
            } else {
                btnLogin.isEnabled = false
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        UserSync.pull(this) {
                            startActivity(Intent(this, HomeActivity::class.java))
                            finish()
                        }
                    }
                    .addOnFailureListener { e ->
                        btnLogin.isEnabled = true
                        Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                    }
            }
        }
    }
}
