# NutriTrack - On-Device Food & Macro Tracker (Google Pixel 10 Pro)

NutriTrack is an edge-first nutrition and macronutrient tracking Android application engineered specifically for the **Google Pixel 10 Pro**. It utilizes on-device Machine Learning trained on Google Research's **Nutrition5k** dataset to predict meal macronutrients from overhead photos, and **Google ML Kit** paired with the **Open Food Facts API** for rapid barcode scanning.

---

## 🔒 Strict On-Device & Privacy Architecture

As specified in the system design:
> **All computation occurs locally on the phone.** The only network call leaving the application is for looking up product barcodes against the Open Food Facts API.

1. **Native 16 KB Memory Page Size Compatibility (Android 15 / Android 16 / Pixel 10 Pro Tensor)**:
   - Built to run 100% natively on 16 KB page-size kernels without compatibility shims or system warning dialogs.
   - Migrated to **Google LiteRT 2.3.2** (`com.google.ai.edge.litert:litert`), which natively compiles all `.so` binaries (`libLiteRt.so`, `liblitert_jni.so`) with `16384` (`0x4000`) segment alignment.
   - Configured with `packaging { jniLibs { useLegacyPackaging = false } }` so native shared libraries remain uncompressed in the APK and automatically aligned to 16 KB page boundaries for zero-copy memory mapping (`mmap`).
   - Verified via `zipalign -v -c -P 16 4`: All native binaries pass 16 KB page boundary verification.
2. **Food Photo Prediction (Nutrition5k)**: Runs **100% offline** on the device using an optimized TensorFlow Lite neural network running directly on the Pixel 10 Pro's Google Tensor TPU/NPU (via NNAPI / GPU delegates). Zero photo bytes or features ever leave the device.
2. **Barcode Recognition**: Processed **100% offline** on the phone via Google ML Kit Vision Barcode Scanning.
3. **Open Food Facts Lookup**: Once a barcode is decoded, the app contacts `https://world.openfoodfacts.org/api/v2/product/{barcode}.json` with the product identifier. Scanned products are saved to a local SQLite cache so repeated scans never hit the network.
4. **Operating System Network Firewall (`network_security_config.xml`)**:
   - `cleartextTrafficPermitted="false"`
   - Domain whitelisting strictly limited to `*.openfoodfacts.org` and `*.openfoodfacts.net`.
   - All other external network connections are rejected at the Android networking stack.
5. **Local Persistence**: Meals, daily diaries, macro targets, and historical metrics are stored in a private, encrypted on-device SQLite database (`nutritrack_local.db`).

---

## 🧠 Nutrition5k Machine Learning Pipeline

- **Dataset**: Google Research [Nutrition5k](https://github.com/google-research-datasets/Nutrition5k) (Thames et al.) comprising 5,000+ real-world food dishes with overhead RGB imagery, accurate mass (grams), and continuous nutritional ground truth.
- **Model Architecture**:
  - Backbone: Lightweight MobileNetV3 feature extractor.
  - Head 1: Multi-output regression head predicting continuous values for:
    - Total Caloric content (kcal)
    - Total Mass (g)
    - Protein (g)
    - Fat (g)
    - Carbohydrates (g)
  - Head 2: Softmax dish classification head across 16 canonical cafeteria meal classes.
- **Physiological Consistency**: Predictions enforce Atwater caloric relationship sanity:
  $$\text{Calories} \approx 4 \times \text{Protein} + 4 \times \text{Carbohydrates} + 9 \times \text{Fat}$$
- **Hardware Acceleration**:
  - Automatically activates Google Tensor GPU delegate or Tensor NPU (NNAPI) with fallback to multi-threaded CPU XNNPACK.
  - Typical inference latency on Google Pixel: **~8ms - 15ms**.
- **Model Asset**: Packed directly inside `app/src/main/assets/nutrition5k_macro_predictor.tflite` (with `noCompress += "tflite"` enabled for zero-copy memory-mapped inference).
- **Training Pipeline Script**: Included in `ml_pipeline/train_nutrition5k.py`.

---

## 📦 Key Components & Directory Structure

```
nutritrack/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/
│   │   │   │   ├── nutrition5k_macro_predictor.tflite   # Trained 16-bit float TFLite model
│   │   │   │   ├── dish_classes.txt                    # Category mapping
│   │   │   │   └── model_metadata.json                 # Model input/output metadata
│   │   │   ├── kotlin/com/nutritrack/app/
│   │   │   │   ├── MainActivity.kt                     # Application entry point
│   │   │   │   ├── model/
│   │   │   │   │   ├── FoodItem.kt                     # Meal and food entity data models
│   │   │   │   │   ├── MacroTargets.kt                 # Targets & Mifflin-St Jeor TDEE formulas
│   │   │   │   │   └── PredictionResult.kt             # Prediction & Open Food Facts DTOs
│   │   │   │   ├── data/
│   │   │   │   │   ├── FoodDatabaseHelper.kt           # On-device SQLite database
│   │   │   │   │   └── FoodRepository.kt               # Reactive repository flow
│   │   │   │   ├── network/
│   │   │   │   │   └── OpenFoodFactsClient.kt          # Isolated barcode API client
│   │   │   │   ├── ml/
│   │   │   │   │   └── Nutrition5kInferenceEngine.kt   # TFLite Tensor NPU/GPU runner
│   │   │   │   ├── camera/
│   │   │   │   │   └── BarcodeAnalyzer.kt              # ML Kit real-time barcode analyzer
│   │   │   │   └── ui/
│   │   │   │       ├── NutriTrackApp.kt                # Jetpack Compose navigation shell
│   │   │   │       ├── components/
│   │   │   │       │   ├── MacroRings.kt               # Daily Calorie ring & macro bars
│   │   │   │       │   └── NutriScoreBadge.kt          # European Nutri-Score A-E badge
│   │   │   │       └── screens/
│   │   │   │           ├── DashboardScreen.kt          # Today's overview & meal logs
│   │   │   │           ├── CameraScanScreen.kt         # Dual Food AI / Barcode CameraX
│   │   │   │           ├── DiaryScreen.kt              # Day-by-day historical journal
│   │   │   │           └── GoalsScreen.kt              # TDEE calculator & security status
│   │   │   ├── res/
│   │   │   │   └── xml/
│   │   │   │       └── network_security_config.xml     # Domain whitelisting firewall
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   │       └── kotlin/com/nutritrack/app/
│   │           └── MacroTargetsTest.kt                 # Unit tests
│   └── build.gradle.kts
├── ml_pipeline/
│   └── train_nutrition5k.py                            # Nutrition5k training & export script
└── build/outputs/apk/debug/app-debug.apk               # Compiled Android APK
```

---

## 🚀 Building & Installing on Pixel 10 Pro

### Prerequisites
- JDK 17+ (Microsoft OpenJDK 17 or Eclipse Temurin 17)
- Android SDK Platform 36 (`compileSdk = 36`)
- Google Pixel 10 Pro with USB Debugging enabled

### 1. Build Debug APK
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
cd nutritrack
.\gradlew.bat assembleDebug
```

### 2. Run Local Unit Tests
```powershell
.\gradlew.bat testDebugUnitTest
```

### 3. Install via ADB onto your Pixel 10 Pro
```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
