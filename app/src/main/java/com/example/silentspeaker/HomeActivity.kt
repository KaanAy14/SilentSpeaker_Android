package com.example.silentspeaker

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val btnTranslator = findViewById<View>(R.id.btnTranslator)
        val btnLearningModule = findViewById<View>(R.id.btnLearningModule)
        val btnHistory = findViewById<View>(R.id.btnHistory)
        val btnThemeToggle = findViewById<Button>(R.id.btnThemeToggle)

        val sharedPref = getSharedPreferences("SilentSpeakerPrefs", Context.MODE_PRIVATE)
        val username = sharedPref.getString("username", "?") ?: "?"
        findViewById<TextView>(R.id.tvWelcome).text = "Hello, $username!"
        findViewById<TextView>(R.id.tvProfileInitial).text = username.first().uppercaseChar().toString()

        val themePref = getSharedPreferences("AppTheme", Context.MODE_PRIVATE)
        btnThemeToggle.text = if (themePref.getBoolean("dark_mode", false)) "☀️" else "🌙"
        btnThemeToggle.setOnClickListener {
            val newDark = !themePref.getBoolean("dark_mode", false)
            themePref.edit().putBoolean("dark_mode", newDark).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (newDark) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            recreate()
        }

        btnTranslator.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        btnLearningModule.setOnClickListener { startActivity(Intent(this, LearningMenuActivity::class.java)) }
        btnHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        findViewById<View>(R.id.btnProfile).setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }

        setupProgressCard()

        val swipeRefresh = findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        swipeRefresh.setColorSchemeColors(0xFF7C3AED.toInt())
        swipeRefresh.setProgressBackgroundColorSchemeColor(0xFF1E1437.toInt())
        swipeRefresh.setOnRefreshListener {
            setupProgressCard()
            swipeRefresh.isRefreshing = false
        }
    }

    override fun onResume() {
        super.onResume()
        setupProgressCard()
    }

    private fun setupProgressCard() {
        val streak = ProgressTracker.getStreak(this)
        val signsLearned = ProgressTracker.getSignsLearned(this)
        val totalSessions = ProgressTracker.getTotalSessions(this)

        findViewById<TextView>(R.id.tvStreakNumber).text = streak.toString()
        findViewById<TextView>(R.id.tvSignsCount).text = signsLearned.toString()
        findViewById<TextView>(R.id.tvSessionsCount).text = totalSessions.toString()

        if (streak == 0) {
            findViewById<TextView>(R.id.tvStreakLabel).text = "Start your streak!"
            findViewById<TextView>(R.id.tvStreakEmoji).text = "⚡"
        } else {
            findViewById<TextView>(R.id.tvStreakLabel).text = if (streak == 1) "Day Streak" else "Days Streak"
            findViewById<TextView>(R.id.tvStreakEmoji).text = "🔥"
        }

        buildWeekRow()
    }

    private fun buildWeekRow() {
        val weekRow = findViewById<LinearLayout>(R.id.weekRow)
        weekRow.removeAllViews()

        val weekActivity = ProgressTracker.getWeekActivity(this)
        val todayIndex = ProgressTracker.getTodayIndex()
        val dayNames = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val dp = resources.displayMetrics.density
        val circleSize = (36 * dp).toInt()

        for (i in 0..6) {
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val circle = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).also {
                    it.bottomMargin = (6 * dp).toInt()
                }
            }

            val innerTv = TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                gravity = Gravity.CENTER
            }

            when {
                i == todayIndex && weekActivity[i] -> {
                    innerTv.text = "🔥"
                    innerTv.textSize = 20f
                }
                i == todayIndex -> {
                    circle.background = ContextCompat.getDrawable(this, R.drawable.day_today)
                }
                weekActivity[i] -> {
                    circle.background = ContextCompat.getDrawable(this, R.drawable.day_active)
                    innerTv.text = "✓"
                    innerTv.textSize = 15f
                    innerTv.setTextColor(0xFFFFFFFF.toInt())
                    innerTv.setTypeface(null, Typeface.BOLD)
                }
                else -> {
                    circle.background = ContextCompat.getDrawable(this, R.drawable.day_inactive)
                }
            }

            circle.addView(innerTv)
            container.addView(circle)

            val label = TextView(this).apply {
                text = dayNames[i]
                textSize = 11f
                setTextColor(if (i == todayIndex) 0xFFFFFFFF.toInt() else 0xFF9D8EC7.toInt())
                gravity = Gravity.CENTER
                if (i == todayIndex) setTypeface(null, Typeface.BOLD)
            }
            container.addView(label)
            weekRow.addView(container)
        }
    }
}
