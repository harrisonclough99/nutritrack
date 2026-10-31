package com.nutritrack.app.model

data class NutritionPredictionResult(
    val dishName: String,
    val confidence: Float,
    val calories: Double,
    val massGrams: Double,
    val protein: Double,
    val fat: Double,
    val carbohydrates: Double,
    val inferenceLatencyMs: Long,
    val delegateUsed: String,
    val atwaterBalanced: Boolean = true
)

data class OpenFoodFactsProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val servingSize: String?,
    val servingQuantityGrams: Double?,
    // Per 100g
    val calories100g: Double,
    val protein100g: Double,
    val carbs100g: Double,
    val fat100g: Double,
    // Per serving (if available, else calculated from 100g)
    val caloriesServing: Double?,
    val proteinServing: Double?,
    val carbsServing: Double?,
    val fatServing: Double?,
    val nutriScore: String?,
    val imageUrl: String?,
    val ingredientsText: String?
)
