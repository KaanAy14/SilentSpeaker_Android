# SilentSpeaker

SilentSpeaker is a native Android app that translates sign language into text in real time and helps users learn sign language through guided practice. It uses on-device computer vision (MediaPipe) and a TensorFlow Lite model to recognize hand, pose, and face landmarks from the camera feed and classify them into words.

## Features

- **Live Sign Translator** — Point the camera at a hand sign, record a short clip, and get the top-3 predicted words with confidence-based feedback.
- **Learning Module** — Practice a curated list of signs (hello, please, thank you, yes, no, water, mom, dad, food, drink) with instant correct/incorrect feedback against a target word.
- **Text → Sign** — Type a word or sentence and see the corresponding sign demonstrated as an animated GIF, with navigation between words for multi-word input.
- **Translation History** — Every recognized sign is timestamped and saved locally.
- **Progress Tracking** — Daily streaks, a weekly activity view, total signs learned, and total sessions completed.
- **Account Sync** — Firebase Authentication (email/password) with Firestore-backed sync, so progress and history follow the user across devices.
- **Light/Dark Theme** — Toggleable app-wide theme, persisted between sessions.

## How It Works

1. **Landmark extraction** (`LandmarkExtractor.kt`): Each camera frame is run through MediaPipe's Hand, Pose, and Face Landmarker tasks. The results are packed into a fixed-size array of 543 landmarks (`face: 468 + left hand: 21 + pose: 33 + right hand: 21`), each with `x, y, z` — matching the layout expected by the classification model.
2. **Frame sampling**: While recording, up to 15 frames are kept, sampling roughly every 10th analyzed frame (~3 fps) to build a short temporal sequence of the gesture.
3. **Classification**: The sequence of landmark frames is fed into a bundled TensorFlow Lite model (`model.tflite`), which outputs a probability distribution over ~250 sign classes. A softmax is applied and the top-3 predictions are shown, using `sign_to_prediction_index_map.json` to map class indices back to words.
4. **Practice mode** compares the top prediction against a target word to give pass/fail feedback and update learning progress.

## Tech Stack

- **Language:** Kotlin
- **UI:** Android Views, ConstraintLayout, Material Components
- **Camera:** CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`)
- **Computer Vision:** [MediaPipe Tasks Vision](https://developers.google.com/mediapipe) (Hand, Pose, Face Landmarker)
- **ML Inference:** TensorFlow Lite
- **Backend:** Firebase Authentication, Firebase Firestore
- **Other libraries:** Glide (image loading), Gson (JSON serialization)

## Project Structure

```
app/src/main/java/com/example/silentspeaker/
├── App.kt                  # Application class, applies saved theme on launch
├── LoginActivity.kt        # Firebase email/password sign in & sign up
├── HomeActivity.kt         # Dashboard: streak, progress, navigation
├── MainActivity.kt         # Live camera sign translator
├── PracticeActivity.kt     # Practice a specific sign with feedback
├── LearningMenuActivity.kt # List of practiceable signs
├── TextToSignActivity.kt   # Text-to-sign GIF lookup
├── HistoryActivity.kt      # Translation history list
├── ProfileActivity.kt      # User profile
├── LandmarkExtractor.kt    # MediaPipe landmark extraction pipeline
├── ProgressTracker.kt      # Streaks / sessions / signs-learned tracking
└── UserSync.kt             # Push/pull local progress & history to Firestore

app/src/main/assets/
├── model.tflite                          # Sign classification model
├── hand_landmarker.task                  # MediaPipe hand landmark model
├── pose_landmarker_full.task              # MediaPipe pose landmark model
├── face_landmarker.task                  # MediaPipe face landmark model
└── sign_to_prediction_index_map.json     # Class index → word mapping
```

## Requirements

- Android Studio (recent stable version)
- JDK 11
- Android device or emulator with a camera, running **API 24 (Android 7.0) or higher**
- A Firebase project (for authentication and progress sync)

## Setup & Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/KaanAy14/SilentSpeaker_Android.git
   ```
2. **Add Firebase configuration**
   - Create a project in the [Firebase Console](https://console.firebase.google.com/).
   - Add an Android app with the package name `com.example.silentspeaker`.
   - Enable **Authentication → Email/Password** and **Firestore Database**.
   - Download the generated `google-services.json` and place it in `app/`.
3. **Open in Android Studio** and let Gradle sync (the project uses AGP 8.11.2 and Kotlin 2.0.21).
4. **Run** the app on a device or emulator with camera support. Grant the camera permission when prompted.

## Notes

- The bundled TFLite model, MediaPipe task files, and `google-services.json` are required at runtime; the app will show a warning toast if the MediaPipe models fail to load.
- Progress and history are stored locally via `SharedPreferences` and synced to Firestore on sign-in and after each recognition/practice session.
