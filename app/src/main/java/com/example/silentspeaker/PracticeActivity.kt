package com.example.silentspeaker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PracticeActivity : AppCompatActivity() {

    private lateinit var practicePreviewView: PreviewView
    private lateinit var tvTargetWord: TextView
    private lateinit var tvPracticeStatus: TextView
    private lateinit var tvPracticeResult: TextView
    private lateinit var btnPracticeRecord: Button
    private lateinit var btnBack: Button
    private lateinit var btnFlipCamera: Button

    private var isFrontCamera = true

    private lateinit var cameraExecutor: ExecutorService
    private var tfliteInterpreter: Interpreter? = null
    private lateinit var landmarkExtractor: LandmarkExtractor

    private var targetWord: String = ""
    private var isRecording = false
    private val framesList = mutableListOf<FloatArray>()
    private val MAX_FRAMES = 15
    private val KEEP_EVERY = 10
    private val MIN_SAVED_FRAMES = 3
    private var frameCounter = 0
    private var hadHandDetection = false
    private val CONFIDENCE_THRESHOLD = 0.05f

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) setupCamera()
        else Toast.makeText(this, "Camera permission denied!", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practice)

        practicePreviewView = findViewById(R.id.practicePreviewView)
        tvTargetWord        = findViewById(R.id.tvTargetWord)
        tvPracticeStatus    = findViewById(R.id.tvPracticeStatus)
        tvPracticeResult    = findViewById(R.id.tvPracticeResult)
        btnPracticeRecord   = findViewById(R.id.btnPracticeRecord)
        btnBack             = findViewById(R.id.btnBack)
        btnFlipCamera       = findViewById(R.id.btnFlipCamera)

        targetWord = intent.getStringExtra("TARGET_WORD") ?: "hello"
        tvTargetWord.text = "Practice: ${targetWord.replaceFirstChar { it.uppercase() }}"

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupTFLite()

        landmarkExtractor = LandmarkExtractor(this)
        if (!landmarkExtractor.setup()) {
            Toast.makeText(this,
                "MediaPipe failed to load! Is hand_landmarker.task in assets/?",
                Toast.LENGTH_LONG).show()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        btnPracticeRecord.setOnClickListener {
            if (!isRecording) {
                framesList.clear()
                hadHandDetection = false
                frameCounter = 0
                isRecording = true
                btnPracticeRecord.text = "Stop & Verify"
                tvPracticeStatus.text = "Recording sign..."
                tvPracticeResult.visibility = View.GONE
            } else {
                isRecording = false
                btnPracticeRecord.text = "Start Practice"
                if (framesList.size < 5) {
                    tvPracticeResult.visibility = View.VISIBLE
                    tvPracticeResult.text = "Error: Too few frames (${framesList.size})"
                    tvPracticeResult.setTextColor(android.graphics.Color.RED)
                    tvPracticeStatus.text = "Ready - Try again"
                } else {
                    if (!hadHandDetection) {
                        tvPracticeResult.visibility = View.VISIBLE
                        tvPracticeResult.text = "Can't recognize — show your hand to the camera"
                        tvPracticeResult.setTextColor(android.graphics.Color.RED)
                        tvPracticeStatus.text = "Ready - Try again"
                        btnPracticeRecord.text = "Start Practice"
                    } else {
                        tvPracticeStatus.text = "Analyzing (${framesList.size} frames)..."
                        runInference()
                    }
                }
            }
        }

        btnBack.setOnClickListener { finish() }

        btnFlipCamera.setOnClickListener {
            isFrontCamera = !isFrontCamera
            landmarkExtractor.isFrontCamera = isFrontCamera
            setupCamera()
        }
    }

    private fun setupTFLite() {
        try {
            val afd = assets.openFd("model.tflite")
            val mappedBuffer = FileInputStream(afd.fileDescriptor).channel
                .map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            tfliteInterpreter = Interpreter(mappedBuffer, Interpreter.Options())
        } catch (e: Exception) {
            Log.e("TFLITE", "Model load error: ${e.message}")
        }
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(practicePreviewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        landmarkExtractor.processFrame(imageProxy)

                        if (isRecording && framesList.size < MAX_FRAMES) {
                            frameCounter++
                            if (frameCounter % KEEP_EVERY == 0) {
                                if (landmarkExtractor.hasHandDetection) hadHandDetection = true
                                framesList.add(landmarkExtractor.buildLandmarkArray())
                                runOnUiThread {
                                    btnPracticeRecord.text =
                                        "Stop (${framesList.size}/$MAX_FRAMES) | ${landmarkExtractor.detectionStatus}"
                                }
                            }
                        }

                        imageProxy.close()
                    }
                }

            try {
                val selector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
                               else CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, preview, imageAnalyzer)
            } catch (e: Exception) {
                Log.e("CAMERA", "Camera bind error", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max() ?: 0f
        val exps = FloatArray(logits.size) { Math.exp((logits[it] - max).toDouble()).toFloat() }
        val sum = exps.sum()
        return FloatArray(logits.size) { exps[it] / sum }
    }

    private fun runInference() {
        Thread {
            try {
                val numFrames = framesList.size
                val interpreter = tfliteInterpreter
                    ?: throw IllegalStateException("TFLite interpreter not loaded")

                interpreter.resizeInput(0, intArrayOf(numFrames, 543, 3))
                interpreter.allocateTensors()

                val inputBuffer = ByteBuffer.allocateDirect(numFrames * 543 * 3 * 4)
                inputBuffer.order(ByteOrder.nativeOrder())
                for (frame in framesList) {
                    for (v in frame) inputBuffer.putFloat(v)
                }

                val numClasses = interpreter.getOutputTensor(0).numElements()
                val outputBuffer = ByteBuffer.allocateDirect(numClasses * 4)
                outputBuffer.order(ByteOrder.nativeOrder())

                interpreter.run(inputBuffer, outputBuffer)

                outputBuffer.rewind()
                val logits = FloatArray(numClasses)
                outputBuffer.asFloatBuffer().get(logits)
                val probs = softmax(logits)

                var maxProb = -1f; var maxIndex = -1
                for (i in probs.indices) {
                    if (probs[i] > maxProb) { maxProb = probs[i]; maxIndex = i }
                }

                data class Pred(val word: String, val prob: Float)
                val top3 = mutableListOf<Pred>()
                try {
                    val json = org.json.JSONObject(
                        assets.open("sign_to_prediction_index_map.json")
                            .bufferedReader().use { it.readText() })
                    val indexToWord = mutableMapOf<Int, String>()
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val k = keys.next(); indexToWord[json.getInt(k)] = k
                    }
                    probs.indices.sortedByDescending { probs[it] }.take(3)
                        .forEach { idx -> top3.add(Pred(indexToWord[idx] ?: "?", probs[idx])) }
                } catch (e: Exception) { Log.e("JSON", "JSON error", e) }

                val predictedWord = top3.firstOrNull()?.word ?: "Unknown"
                val isMatch = predictedWord.equals(targetWord, ignoreCase = true)
                if (isMatch) ProgressTracker.recordSignLearned(this@PracticeActivity)
                val top3Text = top3.mapIndexed { i, p ->
                    "${i + 1}. ${p.word}"
                }.joinToString("\n")

                runOnUiThread {
                    tvPracticeResult.visibility = View.VISIBLE
                    when {
                        maxProb < CONFIDENCE_THRESHOLD -> {
                            tvPracticeResult.text = "Can't recognize"
                            tvPracticeResult.setTextColor(android.graphics.Color.parseColor("#FFA500"))
                        }
                        isMatch -> {
                            tvPracticeResult.text = "Correct! '$predictedWord'\n\n$top3Text"
                            tvPracticeResult.setTextColor(android.graphics.Color.GREEN)
                        }
                        else -> {
                            tvPracticeResult.text = "Try Again\n\n$top3Text"
                            tvPracticeResult.setTextColor(android.graphics.Color.RED)
                        }
                    }
                    tvPracticeStatus.text = "Ready - Try again"
                    btnPracticeRecord.text = "Start Practice"
                }

            } catch (e: Exception) {
                Log.e("TFLITE_RUN", "Inference error", e)
                runOnUiThread {
                    tvPracticeResult.visibility = View.VISIBLE
                    tvPracticeResult.text = "Error: ${e.message}"
                    tvPracticeStatus.text = "Ready - Try again"
                    btnPracticeRecord.text = "Start Practice"
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        tfliteInterpreter?.close()
        landmarkExtractor.close()
    }
}
