package com.example.silentspeaker

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HistoryActivity : AppCompatActivity() {

    private lateinit var sharedPref: android.content.SharedPreferences
    private lateinit var lvHistory: ListView
    private lateinit var tvEmpty: View
    private lateinit var btnClearHistory: Button
    private val historyList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        sharedPref     = getSharedPreferences("TranslationHistory", Context.MODE_PRIVATE)
        lvHistory      = findViewById(R.id.lvHistory)
        tvEmpty        = findViewById<View>(R.id.tvEmpty)
        btnClearHistory = findViewById(R.id.btnClearHistory)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        btnClearHistory.setOnClickListener {
            historyList.clear()
            saveAndRefresh()
        }

        loadHistory()
        applyAdapter()
    }

    private fun loadHistory() {
        val type = object : com.google.gson.reflect.TypeToken<MutableList<String>>() {}.type
        val loaded: MutableList<String> = try {
            com.google.gson.Gson().fromJson(
                sharedPref.getString("history", "[]"), type
            ) ?: mutableListOf()
        } catch (e: Exception) { mutableListOf() }
        historyList.clear()
        historyList.addAll(loaded)
    }

    private fun applyAdapter() {
        if (historyList.isEmpty()) {
            tvEmpty.visibility       = View.VISIBLE
            lvHistory.visibility    = View.GONE
            btnClearHistory.visibility = View.GONE
            return
        }
        tvEmpty.visibility       = View.GONE
        lvHistory.visibility    = View.VISIBLE
        btnClearHistory.visibility = View.VISIBLE

        lvHistory.adapter = object : ArrayAdapter<String>(
            this, R.layout.list_item_history, historyList
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView
                    ?: LayoutInflater.from(context).inflate(R.layout.list_item_history, parent, false)
                view.findViewById<TextView>(R.id.tvItemText).text = historyList[position]
                view.findViewById<Button>(R.id.btnDeleteItem).setOnClickListener {
                    historyList.removeAt(position)
                    saveAndRefresh()
                }
                return view
            }
        }
    }

    private fun saveAndRefresh() {
        sharedPref.edit()
            .putString("history", com.google.gson.Gson().toJson(historyList))
            .apply()
        applyAdapter()
    }
}
