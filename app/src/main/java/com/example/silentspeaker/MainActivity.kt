package com.example.silentspeaker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var tvStatus: TextView
    private lateinit var tvPrediction: TextView
    private lateinit var btnRecord: Button
    private lateinit var btnBack: Button
    private lateinit var btnFlipCamera: Button

    private var isFrontCamera = true

    private lateinit var cameraExecutor: ExecutorService
    private var tfliteInterpreter: Interpreter? = null
    private lateinit var landmarkExtractor: LandmarkExtractor

    private var isRecording = false
    private val framesList = mutableListOf<FloatArray>()
    private val MAX_FRAMES = 15     // Flutter ile aynı (_maxFrames = 15)
    private val KEEP_EVERY = 10     // Flutter ile aynı (_keepEvery = 10, ~3fps)
    private val MIN_SAVED_FRAMES = 3
    private var frameCounter = 0
    private var hadHandDetection = false
    private val CONFIDENCE_THRESHOLD = 0.05f  // 250 sınıf → çok düşük eşik yeterli

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) setupCamera()
        else Toast.makeText(this, "Camera permission denied!", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView    = findViewById(R.id.previewView)
        tvStatus       = findViewById(R.id.tvStatus)
        tvPrediction   = findViewById(R.id.tvPrediction)
        btnRecord      = findViewById(R.id.btnRecord)
        btnBack        = findViewById(R.id.btnBack)
        btnFlipCamera  = findViewById(R.id.btnFlipCamera)

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

        btnRecord.setOnClickListener {
            if (!isRecording) {
                framesList.clear()
                hadHandDetection = false
                frameCounter = 0
                isRecording = true
                btnRecord.text = "◼ STOP RECORDING"
                tvStatus.text = "Recording..."
                tvPrediction.text = "Waiting for gesture..."
            } else {
                isRecording = false
                btnRecord.text = "● START RECORDING"
                if (framesList.size < MIN_SAVED_FRAMES) {
                    tvPrediction.text = "Too short! Sign for longer (${framesList.size} frames captured)"
                    tvStatus.text = "Ready — try again"
                } else {
                    tvStatus.text = "Processing (${framesList.size} frames)..."
                    runInference()
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
            val inShape = tfliteInterpreter!!.getInputTensor(0).shape()
            Log.d("TFLITE", "Model yüklendi. Girdi shape: ${inShape.contentToString()}")
        } catch (e: Exception) {
            Log.e("TFLITE", "Model yükleme hatası: ${e.message}")
        }
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        landmarkExtractor.processFrame(imageProxy)

                        if (isRecording && framesList.size < MAX_FRAMES) {
                            frameCounter++
                            // Her 10. kareyi al — Flutter _keepEvery=10 ile aynı (~3fps)
                            if (frameCounter % KEEP_EVERY == 0) {
                                if (landmarkExtractor.hasHandDetection) hadHandDetection = true
                                framesList.add(landmarkExtractor.buildLandmarkArray())
                                runOnUiThread {
                                    btnRecord.text =
                                        "◼ Stop (${framesList.size}/$MAX_FRAMES)  ${landmarkExtractor.detectionStatus}"
                                }
                            }
                        } else if (!isRecording) {
                            runOnUiThread {
                                tvStatus.text = "Ready — ${landmarkExtractor.detectionStatus}"
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
                Log.e("CAMERA", "Kamera başlatılamadı", e)
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
        // Arka planda çalıştır — tüm frame'leri (num_frames, 543, 3) olarak modele gönder
        // Hiç el görülmediyse direkt uyar
        if (!hadHandDetection) {
            tvPrediction.text = "Not recognized — show your hand to the camera"
            tvStatus.text = "Ready — try again"
            btnRecord.text = "● START RECORDING"
            return
        }

        Thread {
            try {
                val numFrames = framesList.size
                val interpreter = tfliteInterpreter
                    ?: throw IllegalStateException("TFLite interpreter yüklenmedi")

                // Python: interpreter.resize_tensor_input(inp['index'], list(xyz.shape))
                interpreter.resizeInput(0, intArrayOf(numFrames, 543, 3))
                interpreter.allocateTensors()

                // Python: xyz = np.stack(landmark_frames)  → (numFrames, 543, 3)
                val inputBuffer = ByteBuffer.allocateDirect(numFrames * 543 * 3 * 4)
                inputBuffer.order(ByteOrder.nativeOrder())
                for (frame in framesList) {
                    for (v in frame) inputBuffer.putFloat(v)
                }

                // Çıktı boyutunu modelden al (genellikle 250 class)
                val outTensor = interpreter.getOutputTensor(0)
                val numClasses = outTensor.numElements()
                Log.d("TFLITE", "Çıktı eleman sayısı: $numClasses")

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

                // Geçmişe kaydet
                val sp = getSharedPreferences("TranslationHistory", Context.MODE_PRIVATE)
                val type = object : com.google.gson.reflect.TypeToken<MutableList<String>>() {}.type
                val hist: MutableList<String> = try {
                    com.google.gson.Gson().fromJson(sp.getString("history", "[]"), type) ?: mutableListOf()
                } catch (e: Exception) { mutableListOf() }
                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date())
                hist.add(0, "$time - $predictedWord")
                sp.edit().putString("history", com.google.gson.Gson().toJson(hist)).apply()

                val top3Text = top3.mapIndexed { i, p ->
                    "${i + 1}. ${p.word}"
                }.joinToString("\n")
                val resultText = if (maxProb >= CONFIDENCE_THRESHOLD) {
                    top3Text
                } else {
                    "Unrecognized"
                }
                ProgressTracker.recordTranslation(this@MainActivity)
                runOnUiThread {
                    tvPrediction.text = resultText
                    tvStatus.text = "Ready — try again"
                    btnRecord.text = "● START RECORDING"
                }

            } catch (e: Exception) {
                Log.e("TFLITE_RUN", "Inference error", e)
                runOnUiThread {
                    tvPrediction.text = "Error: ${e.message}"
                    tvStatus.text = "Ready — try again"
                    btnRecord.text = "● START RECORDING"
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
