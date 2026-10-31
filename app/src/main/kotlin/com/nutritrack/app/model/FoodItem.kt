package com.nutritrack.app.model

import java.util.UUID

enum class MealType(val displayName: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack")
}

enum class MacroSource(val label: String) {
    OPEN_FOOD_FACTS("Open Food Facts (Barcode)"),
    NUTRITION5K_ML("Nutrition5k On-Device AI"),
    MANUAL("Manual Entry")
}

data class FoodItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val brand: String? = null,
    val mealType: MealType,
    val timestamp: Long = System.currentTimeMillis(),
    val calories: Double, // kcal
    val protein: Double,  // grams
    val carbohydrates: Double, // grams
    val fat: Double,      // grams
    val massGrams: Double, // grams
    val source: MacroSource,
    val barcode: String? = null,
    val nutriScore: String? = null,
    val confidence: Float? = null,
    val imageUri: String? = null,
    val servingSizeDescription: String? = null
)
