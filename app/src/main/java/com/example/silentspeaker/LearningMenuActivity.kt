package com.example.silentspeaker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class LearningMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning_menu)

        // Bind each button explicitly so you can customize them later
        val btnWord1 = findViewById<Button>(R.id.btnWord1)
        val btnWord2 = findViewById<Button>(R.id.btnWord2)
        val btnWord3 = findViewById<Button>(R.id.btnWord3)
        val btnWord4 = findViewById<Button>(R.id.btnWord4)
        val btnWord5 = findViewById<Button>(R.id.btnWord5)
        val btnWord6 = findViewById<Button>(R.id.btnWord6)
        val btnWord7 = findViewById<Button>(R.id.btnWord7)
        val btnWord8 = findViewById<Button>(R.id.btnWord8)
        val btnWord9 = findViewById<Button>(R.id.btnWord9)
        val btnWord10 = findViewById<Button>(R.id.btnWord10)
        val btnBack = findViewById<Button>(R.id.btnBack)

        // Set click listeners to launch PracticeActivity and tell it which word to look for
        btnWord1.setOnClickListener { startPractice("hello") }
        btnWord2.setOnClickListener { startPractice("please") }
        btnWord3.setOnClickListener { startPractice("thankyou") }
        btnWord4.setOnClickListener { startPractice("yes") }
        btnWord5.setOnClickListener { startPractice("no") }
        btnWord6.setOnClickListener { startPractice("water") }
        btnWord7.setOnClickListener { startPractice("mom") }
        btnWord8.setOnClickListener { startPractice("dad") }
        btnWord9.setOnClickListener { startPractice("food") }
        btnWord10.setOnClickListener { startPractice("drink") }
        
        btnBack.setOnClickListener { finish() }
    }

    private fun startPractice(word: String) {
        val intent = Intent(this, PracticeActivity::class.java)
        intent.putExtra("TARGET_WORD", word)
        startActivity(intent)
    }
}
