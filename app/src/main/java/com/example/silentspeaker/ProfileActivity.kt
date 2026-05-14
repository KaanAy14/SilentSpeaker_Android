package com.example.silentspeaker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth

class ProfileActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val user = auth.currentUser
        val themePref = getSharedPreferences("AppTheme", Context.MODE_PRIVATE)

        val displayName = user?.displayName?.takeIf { it.isNotEmpty() }
            ?: user?.email?.substringBefore("@")
            ?: "?"

        findViewById<TextView>(R.id.tvUsername).text = displayName
        findViewById<TextView>(R.id.tvAvatarLetter).text = displayName.first().uppercaseChar().toString()
        findViewById<TextView>(R.id.tvUserEmail).text = user?.email ?: ""

        // Stats (local SharedPreferences — migrated to Firestore later if needed)
        findViewById<TextView>(R.id.tvStatStreak).text = ProgressTracker.getStreak(this).toString()
        findViewById<TextView>(R.id.tvStatSigns).text = ProgressTracker.getSignsLearned(this).toString()
        findViewById<TextView>(R.id.tvStatSessions).text = ProgressTracker.getTotalSessions(this).toString()

        updateThemeRow(themePref.getBoolean("dark_mode", false))
        findViewById<LinearLayout>(R.id.rowTheme).setOnClickListener {
            val newDark = !themePref.getBoolean("dark_mode", false)
            themePref.edit().putBoolean("dark_mode", newDark).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (newDark) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            recreate()
        }

        findViewById<LinearLayout>(R.id.rowResetProgress).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Progress")
                .setMessage("This will clear your streak, signs learned, and session count. This cannot be undone.")
                .setPositiveButton("Reset") { _, _ ->
                    ProgressTracker.resetProgress(this)
                    findViewById<TextView>(R.id.tvStatStreak).text = "0"
                    findViewById<TextView>(R.id.tvStatSigns).text = "0"
                    findViewById<TextView>(R.id.tvStatSessions).text = "0"
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<Button>(R.id.btnSignOut).setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun updateThemeRow(isDark: Boolean) {
        findViewById<TextView>(R.id.tvThemeIcon).text = if (isDark) "☀️" else "🌙"
        findViewById<TextView>(R.id.tvThemeLabel).text = if (isDark) "Dark Mode" else "Day Mode"
    }
}
