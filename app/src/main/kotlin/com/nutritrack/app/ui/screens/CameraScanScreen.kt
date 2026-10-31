package com.nutritrack.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nutritrack.app.camera.BarcodeAnalyzer
import com.nutritrack.app.data.FoodRepository
import com.nutritrack.app.ml.Nutrition5kInferenceEngine
import com.nutritrack.app.model.FoodItem
import com.nutritrack.app.model.MacroSource
import com.nutritrack.app.model.MealType
import com.nutritrack.app.model.NutritionPredictionResult
import com.nutritrack.app.model.OpenFoodFactsProduct
import com.nutritrack.app.network.OpenFoodFactsClient
import com.nutritrack.app.ui.components.NutriScoreBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.roundToInt

enum class ScanMode {
    FOOD_AI,
    BARCODE
}

@Composable
fun CameraScanScreen(
    repository: FoodRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var scanMode by remember { mutableStateOf(ScanMode.FOOD_AI) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Engine & Network instances
    val mlEngine = remember { Nutrition5kInferenceEngine(context) }
    val offClient = remember { OpenFoodFactsClient() }

    DisposableEffect(Unit) {
        onDispose {
            mlEngine.close()
        }
    }

    // State for Food AI prediction
    var isPredicting by remember { mutableStateOf(false) }
    var predictionResult by remember { mutableStateOf<NutritionPredictionResult?>(null) }
    var massMultiplier by remember { mutableFloatStateOf(1.0f) }
    var selectedMealType by remember { mutableStateOf(MealType.LUNCH) }

    // State for Barcode scanning
    var isFetchingBarcode by remember { mutableStateOf(false) }
    var scannedBarcode by remember { mutableStateOf<String?>(null) }
    var barcodeProduct by remember { mutableStateOf<OpenFoodFactsProduct?>(null) }
    var barcodeServingGrams by remember { mutableDoubleStateOf(100.0) }
    var barcodeErrorMessage by remember { mutableStateOf<String?>(null) }

    // Camera instances
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        imageCapture = capture

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(
                            cameraExecutor,
                            BarcodeAnalyzer { detectedCode ->
                                if (scanMode == ScanMode.BARCODE && scannedBarcode != detectedCode && !isFetchingBarcode) {
                                    scannedBarcode = detectedCode
                                    scope.launch {
                                        isFetchingBarcode = true
                                        barcodeErrorMessage = null

                                        // 1. Check local cache first (avoids any network request if seen before)
                                        val cached = repository.getCachedBarcodeProduct(detectedCode)
                                        if (cached != null) {
                                            barcodeProduct = cached
                                            barcodeServingGrams = cached.servingQuantityGrams ?: 100.0
                                            isFetchingBarcode = false
                                        } else {
                                            // 2. Query Open Food Facts API (the ONLY permitted network call)
                                            val res = offClient.fetchProductByBarcode(detectedCode)
                                            res.onSuccess { product ->
                                                repository.cacheBarcodeProduct(product)
                                                barcodeProduct = product
                                                barcodeServingGrams = product.servingQuantityGrams ?: 100.0
                                            }.onFailure { err ->
                                                barcodeErrorMessage = err.localizedMessage ?: "Failed to find product"
                                            }
                                            isFetchingBarcode = false
                                        }
                                    }
                                }
                            }
                        )

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                capture,
                                imageAnalysis
                            )
                        } catch (exc: Exception) {
                            Log.e("CameraScanScreen", "Camera binding failed", exc)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Permission Required",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "To scan food dishes with the Nutrition5k on-device model and barcodes with Open Food Facts, camera access is needed.",
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        }

        // Top Header Bar & Mode Selector
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(top = 16.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                Text(
                    text = if (scanMode == ScanMode.FOOD_AI) "Food Scanner (Nutrition5k AI)" else "Barcode Scanner (Open Food Facts)",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                // On-Device Accelerator indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B5E20))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Tensor NPU",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mode Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeButton(
                    title = "Food Dish AI",
                    icon = Icons.Default.Restaurant,
                    isSelected = scanMode == ScanMode.FOOD_AI,
                    onClick = {
                        scanMode = ScanMode.FOOD_AI
                        barcodeProduct = null
                        scannedBarcode = null
                    },
                    modifier = Modifier.weight(1f)
                )
                ModeButton(
                    title = "Barcode",
                    icon = Icons.Default.QrCodeScanner,
                    isSelected = scanMode == ScanMode.BARCODE,
                    onClick = {
                        scanMode = ScanMode.BARCODE
                        predictionResult = null
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Reticle / Aiming Guide
        if (predictionResult == null && barcodeProduct == null) {
            Box(
                modifier = Modifier
                    .size(if (scanMode == ScanMode.FOOD_AI) 260.dp else 240.dp)
                    .align(Alignment.Center)
                    .border(
                        width = 2.dp,
                        color = if (scanMode == ScanMode.FOOD_AI) Color(0xFF4CAF50) else Color(0xFF03A9F4),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Text(
                    text = if (scanMode == ScanMode.FOOD_AI) "Align overhead food plate" else "Center barcode inside frame",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                )
            }
        }

        // Shutter Action for Food AI Mode
        if (scanMode == ScanMode.FOOD_AI && predictionResult == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
            ) {
                IconButton(
                    onClick = {
                        val capture = imageCapture ?: return@IconButton
                        isPredicting = true
                        capture.takePicture(
                            cameraExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bitmap = imageProxyToBitmap(image)
                                    image.close()

                                    scope.launch(Dispatchers.Default) {
                                        try {
                                            val result = mlEngine.predictMacros(bitmap)
                                            withContext(Dispatchers.Main) {
                                                predictionResult = result
                                                massMultiplier = 1.0f
                                                isPredicting = false
                                            }
                                        } catch (e: Exception) {
                                            Log.e("CameraScanScreen", "Inference error", e)
                                            withContext(Dispatchers.Main) {
                                                isPredicting = false
                                                Toast.makeText(context, "ML Inference error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("CameraScanScreen", "Capture error", exception)
                                    isPredicting = false
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(6.dp)
                        .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    if (isPredicting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Capture Food",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }

        // Loading overlay for barcode lookup
        if (isFetchingBarcode) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Querying Open Food Facts API...",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Barcode Error Dialog / Card
        if (barcodeErrorMessage != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Product Not Found",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = barcodeErrorMessage ?: "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            barcodeErrorMessage = null
                            scannedBarcode = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Try Another Barcode")
                    }
                }
            }
        }

        // Bottom Sheet / Result Card for Nutrition5k Food AI Result
        AnimatedVisibility(
            visible = predictionResult != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            predictionResult?.let { res ->
                val adjustedMass = (res.massGrams * massMultiplier).roundToInt()
                val adjustedCalories = (res.calories * massMultiplier).roundToInt()
                val adjustedProtein = (res.protein * massMultiplier * 10.0).roundToInt() / 10.0
                val adjustedCarbs = (res.carbohydrates * massMultiplier * 10.0).roundToInt() / 10.0
                val adjustedFat = (res.fat * massMultiplier * 10.0).roundToInt() / 10.0

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = res.dishName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Nutrition5k On-Device ML • ${(res.confidence * 100).toInt()}% Match",
                                    fontSize = 11.sp,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(onClick = { predictionResult = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }

                        // Hardware Acceleration Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${res.delegateUsed} • ${res.inferenceLatencyMs}ms (100% on phone)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Macros Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MacroPill("Calories", "$adjustedCalories", "kcal", Color(0xFF4CAF50))
                            MacroPill("Mass", "$adjustedMass", "g", Color(0xFF78909C))
                            MacroPill("Protein", "$adjustedProtein", "g", Color(0xFF29B6F6))
                            MacroPill("Carbs", "$adjustedCarbs", "g", Color(0xFFFFA726))
                            MacroPill("Fat", "$adjustedFat", "g", Color(0xFFEF5350))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Portion Slider
                        Text(
                            text = "Portion Sizing: ${(massMultiplier * 100).toInt()}% (${adjustedMass}g)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = massMultiplier,
                            onValueChange = { massMultiplier = it },
                            valueRange = 0.3f..2.5f
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Meal Selector
                        MealSelector(
                            selected = selectedMealType,
                            onSelected = { selectedMealType = it }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Log Button
                        Button(
                            onClick = {
                                scope.launch {
                                    val item = FoodItem(
                                        name = res.dishName,
                                        brand = "Nutrition5k AI",
                                        mealType = selectedMealType,
                                        calories = adjustedCalories.toDouble(),
                                        protein = adjustedProtein,
                                        carbohydrates = adjustedCarbs,
                                        fat = adjustedFat,
                                        massGrams = adjustedMass.toDouble(),
                                        source = MacroSource.NUTRITION5K_ML,
                                        confidence = res.confidence,
                                        servingSizeDescription = "${adjustedMass}g portion"
                                    )
                                    repository.logFoodItem(item)
                                    Toast.makeText(context, "Logged ${res.dishName} to ${selectedMealType.displayName}!", Toast.LENGTH_SHORT).show()
                                    predictionResult = null
                                    onNavigateBack()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Log to Diary (${adjustedCalories} kcal)")
                        }
                    }
                }
            }
        }

        // Bottom Sheet / Result Card for Barcode Open Food Facts Result
        AnimatedVisibility(
            visible = barcodeProduct != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            barcodeProduct?.let { prod ->
                val gramsRatio = barcodeServingGrams / 100.0
                val cal = (prod.calories100g * gramsRatio).roundToInt()
                val pro = (prod.protein100g * gramsRatio * 10.0).roundToInt() / 10.0
                val carb = (prod.carbs100g * gramsRatio * 10.0).roundToInt() / 10.0
                val fat = (prod.fat100g * gramsRatio * 10.0).roundToInt() / 10.0

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prod.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!prod.brand.isNullOrBlank()) {
                                    Text(
                                        text = prod.brand,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { barcodeProduct = null; scannedBarcode = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Nutri-Score badge & Barcode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NutriScoreBadge(grade = prod.nutriScore)
                            Text(
                                text = "EAN: ${prod.barcode}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Macros Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MacroPill("Calories", "$cal", "kcal", Color(0xFF4CAF50))
                            MacroPill("Portion", "${barcodeServingGrams.roundToInt()}", "g", Color(0xFF78909C))
                            MacroPill("Protein", "$pro", "g", Color(0xFF29B6F6))
                            MacroPill("Carbs", "$carb", "g", Color(0xFFFFA726))
                            MacroPill("Fat", "$fat", "g", Color(0xFFEF5350))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Portion Slider
                        Text(
                            text = "Portion: ${barcodeServingGrams.roundToInt()}g (Default: ${prod.servingSize ?: "100g"})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = barcodeServingGrams.toFloat(),
                            onValueChange = { barcodeServingGrams = it.toDouble() },
                            valueRange = 10f..400f
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Meal Selector
                        MealSelector(
                            selected = selectedMealType,
                            onSelected = { selectedMealType = it }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Log Button
                        Button(
                            onClick = {
                                scope.launch {
                                    val item = FoodItem(
                                        name = prod.name,
                                        brand = prod.brand,
                                        mealType = selectedMealType,
                                        calories = cal.toDouble(),
                                        protein = pro,
                                        carbohydrates = carb,
                                        fat = fat,
                                        massGrams = barcodeServingGrams,
                                        source = MacroSource.OPEN_FOOD_FACTS,
                                        barcode = prod.barcode,
                                        nutriScore = prod.nutriScore,
                                        servingSizeDescription = "${barcodeServingGrams.roundToInt()}g"
                                    )
                                    repository.logFoodItem(item)
                                    Toast.makeText(context, "Logged ${prod.name} to ${selectedMealType.displayName}!", Toast.LENGTH_SHORT).show()
                                    barcodeProduct = null
                                    scannedBarcode = null
                                    onNavigateBack()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Log to Diary ($cal kcal)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun MacroPill(
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = unit,
            fontSize = 9.sp,
            color = color.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun MealSelector(
    selected: MealType,
    onSelected: (MealType) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MealType.values().forEach { meal ->
            val isCurrent = meal == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelected(meal) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = meal.displayName,
                    fontSize = 11.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val bitmap = try {
        image.toBitmap()
    } catch (e: Exception) {
        val planeProxy = image.planes[0]
        val buffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
    }

    val rotation = image.imageInfo.rotationDegrees
    return if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
}
