package com.example.silentspeaker

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class TextToSignActivity : AppCompatActivity() {

    private val signMap = mapOf(
        "hello"     to R.drawable.sign_hello,
        "please"    to R.drawable.sign_please,
        "thank you" to R.drawable.sign_thankyou,
        "thankyou"  to R.drawable.sign_thankyou,
        "thanks"    to R.drawable.sign_thankyou,
        "yes"       to R.drawable.sign_yes,
        "no"        to R.drawable.sign_no,
        "water"     to R.drawable.sign_water,
        "mom"       to R.drawable.sign_mom,
        "dad"       to R.drawable.sign_dad,
        "food"      to R.drawable.sign_food,
        "drink"     to R.drawable.sign_drink
    )

    private lateinit var etInput: EditText
    private lateinit var btnTranslate: Button
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var tvCurrentWord: TextView
    private lateinit var tvWordCounter: TextView
    private lateinit var ivSign: ImageView
    private lateinit var tvPlaceholder: TextView
    private lateinit var tvNotFound: TextView
    private lateinit var layoutNavigation: View

    private var words = listOf<String>()
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_to_sign)

        etInput         = findViewById(R.id.etSignInput)
        btnTranslate    = findViewById(R.id.btnTranslate)
        btnPrev         = findViewById(R.id.btnPrev)
        btnNext         = findViewById(R.id.btnNext)
        tvCurrentWord   = findViewById(R.id.tvCurrentWord)
        tvWordCounter   = findViewById(R.id.tvWordCounter)
        ivSign          = findViewById(R.id.ivSign)
        tvPlaceholder   = findViewById(R.id.tvPlaceholder)
        tvNotFound      = findViewById(R.id.tvNotFound)
        layoutNavigation = findViewById(R.id.layoutNavigation)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        btnTranslate.setOnClickListener { translate() }
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { translate(); true } else false
        }

        btnPrev.setOnClickListener {
            if (currentIndex > 0) showWord(--currentIndex)
        }
        btnNext.setOnClickListener {
            if (currentIndex < words.size - 1) showWord(++currentIndex)
        }
    }

    private fun translate() {
        val input = etInput.text.toString().trim().lowercase()
        if (input.isEmpty()) return

        words = input.split(" ").filter { it.isNotEmpty() }
        currentIndex = 0
        layoutNavigation.visibility = if (words.size > 1) View.VISIBLE else View.GONE
        showWord(0)
    }

    private fun showWord(index: Int) {
        val word = words[index]
        tvCurrentWord.text = word.replaceFirstChar { it.uppercase() }
        tvWordCounter.text = "${index + 1} / ${words.size}"
        btnPrev.isEnabled = index > 0
        btnNext.isEnabled = index < words.size - 1

        val resId = signMap[word]
        if (resId != null) {
            ivSign.visibility = View.VISIBLE
            tvPlaceholder.visibility = View.GONE
            tvNotFound.visibility = View.GONE
            Glide.with(this).load(resId).into(ivSign)
        } else {
            ivSign.visibility = View.GONE
            tvPlaceholder.visibility = View.GONE
            tvNotFound.visibility = View.VISIBLE
        }
    }
}
