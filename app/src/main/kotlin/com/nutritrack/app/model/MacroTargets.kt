package com.nutritrack.app.model

data class MacroTargets(
    val dailyCalories: Int = 2250,
    val proteinGrams: Int = 160,
    val carbGrams: Int = 220,
    val fatGrams: Int = 75
) {
    companion object {
        fun calculateTdee(
            weightKg: Double,
            heightCm: Double,
            ageYears: Int,
            isMale: Boolean,
            activityMultiplier: Double = 1.375 // Lightly active
        ): MacroTargets {
            // Mifflin-St Jeor Equation (calculated 100% locally on phone)
            val bmr = if (isMale) {
                (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * ageYears) + 5.0
            } else {
                (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * ageYears) - 161.0
            }
            val tdee = (bmr * activityMultiplier).toInt()

            // Recommended athletic macronutrient distribution: 30% Protein, 45% Carb, 25% Fat
            val proteinGrams = ((tdee * 0.30) / 4.0).toInt()
            val carbGrams = ((tdee * 0.45) / 4.0).toInt()
            val fatGrams = ((tdee * 0.25) / 9.0).toInt()

            return MacroTargets(
                dailyCalories = tdee,
                proteinGrams = proteinGrams,
                carbGrams = carbGrams,
                fatGrams = fatGrams
            )
        }
    }
}

data class DailyMacroSummary(
    val dateEpochDay: Long,
    val totalCalories: Double = 0.0,
    val totalProtein: Double = 0.0,
    val totalCarbs: Double = 0.0,
    val totalFat: Double = 0.0,
    val totalMassGrams: Double = 0.0,
    val items: List<FoodItem> = emptyList()
)
