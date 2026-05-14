package com.example.silentspeaker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

// Landmark sırası (Python server ile birebir aynı):
// face(468) + left_hand(21) + pose(33) + right_hand(21) = 543 → 543*3 = 1629 float
class LandmarkExtractor(private val context: Context) {

    private var handLandmarker: HandLandmarker? = null
    private var poseLandmarker: PoseLandmarker? = null
    private var faceLandmarker: FaceLandmarker? = null

    @Volatile private var lastHandResult: HandLandmarkerResult? = null
    @Volatile private var lastPoseResult: PoseLandmarkerResult? = null
    @Volatile private var lastFaceResult: FaceLandmarkerResult? = null

    var isFrontCamera: Boolean = true

    val isReady: Boolean get() = handLandmarker != null
    val hasHandDetection: Boolean get() = (lastHandResult?.landmarks()?.isNotEmpty() == true)

    val detectionStatus: String get() {
        val hands = lastHandResult?.landmarks()?.size ?: 0
        val pose  = if (lastPoseResult?.landmarks()?.isNotEmpty() == true) "✓" else "✗"
        val face  = if (lastFaceResult?.faceLandmarks()?.isNotEmpty() == true) "✓" else "✗"
        return "Hand:$hands Pose:$pose Face:$face"
    }

    fun setup(): Boolean {
        return try {
            val handOptions = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
                .setNumHands(2)
                .setRunningMode(RunningMode.IMAGE)
                .build()
            handLandmarker = HandLandmarker.createFromOptions(context, handOptions)

            val poseFileNames = listOf("pose_landmarker.task", "pose_landmarker_full.task", "pose_landmarker_lite.task")
            for (poseFile in poseFileNames) {
                try {
                    val poseOptions = PoseLandmarker.PoseLandmarkerOptions.builder()
                        .setBaseOptions(BaseOptions.builder().setModelAssetPath(poseFile).build())
                        .setRunningMode(RunningMode.IMAGE)
                        .build()
                    poseLandmarker = PoseLandmarker.createFromOptions(context, poseOptions)
                    Log.d("MEDIAPIPE", "Pose modeli yüklendi: $poseFile")
                    break
                } catch (e: Exception) {
                    Log.w("MEDIAPIPE", "$poseFile bulunamadı, sonraki deneniyor")
                }
            }

            try {
                val faceOptions = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
                    .setNumFaces(1)
                    .setRunningMode(RunningMode.IMAGE)
                    .build()
                faceLandmarker = FaceLandmarker.createFromOptions(context, faceOptions)
                Log.d("MEDIAPIPE", "FaceLandmarker yüklendi ✓")
            } catch (e: Exception) {
                Log.w("MEDIAPIPE", "face_landmarker.task bulunamadı — tahminler kötü olacak: ${e.message}")
            }

            Log.d("MEDIAPIPE", "LandmarkExtractor hazır")
            true
        } catch (e: Exception) {
            Log.e("MEDIAPIPE", "LandmarkExtractor başlatılamadı: ${e.message}")
            false
        }
    }

    fun processFrame(imageProxy: ImageProxy) {
        if (!isReady) return
        try {
            val bitmap = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees

            var processed = if (rotation != 0) {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            } else bitmap

            if (isFrontCamera) {
                val flipM = Matrix().apply {
                    postScale(-1f, 1f, processed.width / 2f, processed.height / 2f)
                }
                processed = Bitmap.createBitmap(processed, 0, 0, processed.width, processed.height, flipM, true)
            }

            val mpImage = BitmapImageBuilder(processed).build()

            // Synchronous detection — mirrors Python server's RunningMode.IMAGE behavior
            // guarantees results belong to this exact frame when buildLandmarkArray() is called
            lastHandResult = handLandmarker?.detect(mpImage)
            lastPoseResult = poseLandmarker?.detect(mpImage)
            lastFaceResult = faceLandmarker?.detect(mpImage)
        } catch (e: Exception) {
            Log.e("MEDIAPIPE", "Frame hatası: ${e.message}")
        }
    }

    // Çıktı: face(468) + left_hand(21) + pose(33) + right_hand(21), her biri x/y/z
    // Tespit edilemeyen landmark → NaN  (Python server ile aynı davranış)
    fun buildLandmarkArray(): FloatArray {
        val out = FloatArray(543 * 3) { Float.NaN }

        // Yüz: offset 0, 468 landmark
        lastFaceResult?.faceLandmarks()?.firstOrNull()?.let { facePoints ->
            facePoints.take(468).forEachIndexed { i, lm ->
                out[i * 3]     = lm.x()
                out[i * 3 + 1] = lm.y()
                out[i * 3 + 2] = lm.z()
            }
        }

        // Poz: offset (468+21)*3 = 1467, 33 landmark
        lastPoseResult?.landmarks()?.firstOrNull()?.let { posePoints ->
            val base = (468 + 21) * 3
            posePoints.take(33).forEachIndexed { i, lm ->
                out[base + i * 3]     = lm.x()
                out[base + i * 3 + 1] = lm.y()
                out[base + i * 3 + 2] = lm.z()
            }
        }

        // Eller: sol=offset 468*3=1404, sağ=offset (468+21+33)*3=1566
        val handResult = lastHandResult ?: return out
        handResult.landmarks().forEachIndexed { idx, handPoints ->
            val isLeft = handResult.handednesses().getOrNull(idx)
                ?.firstOrNull()?.categoryName() == "Left"
            val base = if (isLeft) 468 * 3 else (468 + 21 + 33) * 3
            handPoints.take(21).forEachIndexed { i, lm ->
                out[base + i * 3]     = lm.x()
                out[base + i * 3 + 1] = lm.y()
                out[base + i * 3 + 2] = lm.z()
            }
        }

        return out
    }

    fun close() {
        handLandmarker?.close()
        poseLandmarker?.close()
        faceLandmarker?.close()
        handLandmarker = null
        poseLandmarker = null
        faceLandmarker = null
    }
}
