package com.example.silentspeaker

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object UserSync {

    private fun uid() = FirebaseAuth.getInstance().currentUser?.uid

    fun push(context: Context) {
        val uid = uid() ?: return
        val db = FirebaseFirestore.getInstance()

        val progressPrefs = context.getSharedPreferences("SilentSpeakerProgress", Context.MODE_PRIVATE)
        val historyPrefs = context.getSharedPreferences("TranslationHistory", Context.MODE_PRIVATE)

        val data = mapOf(
            "streak" to progressPrefs.getInt("streak", 0),
            "signs_learned" to progressPrefs.getInt("signs_learned", 0),
            "total_sessions" to progressPrefs.getInt("total_sessions", 0),
            "last_date" to (progressPrefs.getString("last_date", "") ?: ""),
            "active_dates" to (progressPrefs.getString("active_dates", "") ?: ""),
            "history" to (historyPrefs.getString("history", "[]") ?: "[]")
        )

        db.collection("users").document(uid).set(data, SetOptions.merge())
    }

    fun pull(context: Context, onComplete: () -> Unit) {
        val uid = uid() ?: run { onComplete(); return }
        val db = FirebaseFirestore.getInstance()

        val progressPrefs = context.getSharedPreferences("SilentSpeakerProgress", Context.MODE_PRIVATE)
        val historyPrefs = context.getSharedPreferences("TranslationHistory", Context.MODE_PRIVATE)
        progressPrefs.edit().clear().apply()
        historyPrefs.edit().clear().apply()

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    progressPrefs.edit()
                        .putInt("streak", (doc.getLong("streak") ?: 0).toInt())
                        .putInt("signs_learned", (doc.getLong("signs_learned") ?: 0).toInt())
                        .putInt("total_sessions", (doc.getLong("total_sessions") ?: 0).toInt())
                        .putString("last_date", doc.getString("last_date") ?: "")
                        .putString("active_dates", doc.getString("active_dates") ?: "")
                        .apply()

                    historyPrefs.edit()
                        .putString("history", doc.getString("history") ?: "[]")
                        .apply()
                }
                onComplete()
            }
            .addOnFailureListener { onComplete() }
    }
}
