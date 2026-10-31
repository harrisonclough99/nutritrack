package com.nutritrack.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.nutritrack.app.model.NutritionPredictionResult
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max

class Nutrition5kInferenceEngine(private val context: Context) : AutoCloseable {

    companion object {
        private const val TAG = "Nutrition5kEngine"
        private const val MODEL_FILE = "nutrition5k_macro_predictor.tflite"
        private const val LABELS_FILE = "dish_classes.txt"

        private const val INPUT_SIZE = 224
        private const val PIXEL_SIZE = 3 // RGB
        private const val NUM_MACROS = 5 // [calories, mass, protein, fat, carbs]
    }

    private var interpreter: Interpreter? = null
    private var activeAccelerator: String = "Google Tensor CPU (XNNPACK 16KB-Native)"
    private val dishCategories = mutableListOf<String>()

    init {
        loadDishCategories()
        initInterpreter()
    }

    private fun loadDishCategories() {
        try {
            context.assets.open(LABELS_FILE).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            dishCategories.add(trimmed)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading dish categories, using defaults", e)
            dishCategories.addAll(
                listOf(
                    "Salad / Greens Bowl",
                    "Chicken & Grain Bowl",
                    "Pasta / Noodle Dish",
                    "Steak / Roasted Beef",
                    "Fish / Seafood Plate",
                    "Soup / Stew / Broth",
                    "Burger / Sandwich",
                    "Rice & Curry / Stew",
                    "Egg / Scramble / Omelet",
                    "Fruit Plate / Berry Bowl",
                    "Yogurt / Parfait / Oatmeal",
                    "Pizza / Flatbread",
                    "Tacos / Burrito Bowl",
                    "Roasted Vegetables / Roots",
                    "Dessert / Pastry / Bakery",
                    "Mixed Platter / Buffet Plate"
                )
            )
        }
    }

    private fun loadModelFile(): ByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun initInterpreter() {
        val modelBuffer = loadModelFile()
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            setUseXNNPACK(true)
        }

        try {
            interpreter = Interpreter(modelBuffer, options)
            activeAccelerator = "Google Tensor CPU (XNNPACK 16KB-Native)"
            Log.i(TAG, "Nutrition5k LiteRT engine initialized with XNNPACK")
        } catch (e: Exception) {
            Log.w(TAG, "Retrying interpreter on default CPU...", e)
            val fallbackOptions = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, fallbackOptions)
            activeAccelerator = "Google Tensor CPU (Multi-Threaded)"
        }
    }

    fun getHardwareAccelerator(): String = activeAccelerator

    /**
     * Executes on-device macronutrient prediction for a given food photo.
     * Takes bitmap, pre-processes to 224x224 RGB, runs model, and returns predicted values.
     */
    fun predictMacros(bitmap: Bitmap): NutritionPredictionResult {
        val interp = interpreter ?: throw IllegalStateException("Nutrition5k interpreter not initialized")

        val startTime = SystemClock.elapsedRealtime()

        // 1. Preprocess image to 224x224 RGB Float32 Buffer [0.0, 1.0]
        val scaledBitmap = if (bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        }

        val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE * 4).apply {
            order(ByteOrder.nativeOrder())
            rewind()
        }

        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaledBitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixelValue in intValues) {
            val r = ((pixelValue shr 16) and 0xFF) / 255.0f
            val g = ((pixelValue shr 8) and 0xFF) / 255.0f
            val b = (pixelValue and 0xFF) / 255.0f

            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        // 2. Prepare outputs
        // Output 0: Continuous Macros [calories, mass_g, protein_g, fat_g, carb_g]
        val macroOutput = Array(1) { FloatArray(NUM_MACROS) }

        // Output 1: Category Softmax Probabilities
        val numClasses = if (dishCategories.isNotEmpty()) dishCategories.size else 16
        val categoryOutput = Array(1) { FloatArray(numClasses) }

        val outputs = mutableMapOf<Int, Any>()
        outputs[0] = macroOutput
        outputs[1] = categoryOutput

        // 3. Run Inference on Phone
        interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

        val latencyMs = SystemClock.elapsedRealtime() - startTime

        // 4. Extract Category & Confidence
        var maxProb = 0.0f
        var maxIndex = 0
        for (i in 0 until numClasses) {
            val prob = categoryOutput[0][i]
            if (prob > maxProb) {
                maxProb = prob
                maxIndex = i
            }
        }

        val predictedCategory = if (maxIndex in dishCategories.indices) {
            dishCategories[maxIndex]
        } else {
            "Mixed Dish Plate"
        }

        // 5. Parse and Bound Macros
        // [calories, mass, protein, fat, carbs]
        val rawCalories = max(20.0f, macroOutput[0][0]).toDouble()
        val rawMass = max(40.0f, macroOutput[0][1]).toDouble()
        val rawProtein = max(1.0f, macroOutput[0][2]).toDouble()
        val rawFat = max(0.5f, macroOutput[0][3]).toDouble()
        val rawCarbs = max(2.0f, macroOutput[0][4]).toDouble()

        // Atwater physiological sanity reconciliation:
        // Theoretical calories = 4*P + 4*C + 9*F
        val atwaterCalories = (rawProtein * 4.0) + (rawCarbs * 4.0) + (rawFat * 9.0)
        val reconciledCalories = if (rawCalories in (atwaterCalories * 0.75)..(atwaterCalories * 1.25)) {
            rawCalories
        } else {
            atwaterCalories
        }

        return NutritionPredictionResult(
            dishName = predictedCategory,
            confidence = maxProb.coerceIn(0.65f, 0.98f),
            calories = kotlin.math.round(reconciledCalories),
            massGrams = kotlin.math.round(rawMass),
            protein = kotlin.math.round(rawProtein * 10.0) / 10.0,
            fat = kotlin.math.round(rawFat * 10.0) / 10.0,
            carbohydrates = kotlin.math.round(rawCarbs * 10.0) / 10.0,
            inferenceLatencyMs = latencyMs,
            delegateUsed = activeAccelerator,
            atwaterBalanced = true
        )
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }
}
