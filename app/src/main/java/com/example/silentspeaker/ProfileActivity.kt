package com.example.silentspeaker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val sharedPref = getSharedPreferences("SilentSpeakerPrefs", Context.MODE_PRIVATE)
        val themePref = getSharedPreferences("AppTheme", Context.MODE_PRIVATE)

        // Avatar + username
        val username = sharedPref.getString("username", "?") ?: "?"
        findViewById<TextView>(R.id.tvUsername).text = username
        findViewById<TextView>(R.id.tvAvatarLetter).text = username.first().uppercaseChar().toString()

        // Stats
        findViewById<TextView>(R.id.tvStatStreak).text = ProgressTracker.getStreak(this).toString()
        findViewById<TextView>(R.id.tvStatSigns).text = ProgressTracker.getSignsLearned(this).toString()
        findViewById<TextView>(R.id.tvStatSessions).text = ProgressTracker.getTotalSessions(this).toString()

        // Theme row
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

        // Reset progress
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

        // Sign out
        findViewById<Button>(R.id.btnSignOut).setOnClickListener {
            sharedPref.edit().putBoolean("isLoggedIn", false).apply()
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
