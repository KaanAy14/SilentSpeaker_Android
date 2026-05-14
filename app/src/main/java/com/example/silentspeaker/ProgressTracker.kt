package com.example.silentspeaker

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object ProgressTracker {
    private const val PREFS = "SilentSpeakerProgress"
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun today() = sdf.format(Calendar.getInstance().time)

    private fun yesterday(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -1)
        return sdf.format(cal.time)
    }

    private fun recordActivity(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val todayStr = today()
        val lastDate = prefs.getString("last_date", "") ?: ""

        val currentStreak = prefs.getInt("streak", 0)
        val newStreak = when (lastDate) {
            todayStr -> currentStreak
            yesterday() -> currentStreak + 1
            else -> 1
        }

        val raw = prefs.getString("active_dates", "") ?: ""
        val dateSet = if (raw.isEmpty()) mutableSetOf() else raw.split(",").toMutableSet()
        dateSet.add(todayStr)

        prefs.edit()
            .putInt("streak", newStreak)
            .putString("last_date", todayStr)
            .putString("active_dates", dateSet.joinToString(","))
            .apply()
    }

    fun recordTranslation(context: Context) {
        recordActivity(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("total_sessions", prefs.getInt("total_sessions", 0) + 1)
            .apply()
        UserSync.push(context)
    }

    fun recordSignLearned(context: Context) {
        recordActivity(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("signs_learned", prefs.getInt("signs_learned", 0) + 1)
            .apply()
        UserSync.push(context)
    }

    fun getStreak(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("streak", 0)

    fun getSignsLearned(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("signs_learned", 0)

    fun getTotalSessions(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("total_sessions", 0)

    // Returns BooleanArray[7] where index 0 = Monday, 6 = Sunday
    fun getWeekActivity(context: Context): BooleanArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString("active_dates", "") ?: ""
        val dateSet = if (raw.isEmpty()) emptySet() else raw.split(",").toSet()

        val result = BooleanArray(7)
        val cal = Calendar.getInstance()
        // Move to Monday of current week
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = (dow - Calendar.MONDAY + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)

        for (i in 0..6) {
            result[i] = dateSet.contains(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return result
    }

    // Returns index 0=Mon ... 6=Sun
    fun getTodayIndex(): Int {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return (dow - Calendar.MONDAY + 7) % 7
    }

    fun resetProgress(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("TranslationHistory", Context.MODE_PRIVATE).edit().clear().apply()
        UserSync.push(context)
    }
}
