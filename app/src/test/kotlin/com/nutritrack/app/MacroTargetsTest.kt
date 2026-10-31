package com.nutritrack.app

import com.nutritrack.app.model.MacroTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroTargetsTest {

    @Test
    fun testTdeeCalculationForMale() {
        // 75kg, 180cm, 25 years old male
        // BMR = 10*75 + 6.25*180 - 5*25 + 5 = 750 + 1125 - 125 + 5 = 1755
        // TDEE = 1755 * 1.375 = 2413
        val targets = MacroTargets.calculateTdee(
            weightKg = 75.0,
            heightCm = 180.0,
            ageYears = 25,
            isMale = true,
            activityMultiplier = 1.375
        )

        assertEquals(2413, targets.dailyCalories)
        // Protein (30%): (2413 * 0.30) / 4 = 180g
        assertEquals(180, targets.proteinGrams)
        // Carbs (45%): (2413 * 0.45) / 4 = 271g
        assertEquals(271, targets.carbGrams)
        // Fat (25%): (2413 * 0.25) / 9 = 67g
        assertEquals(67, targets.fatGrams)
    }

    @Test
    fun testTdeeCalculationForFemale() {
        // 60kg, 165cm, 30 years old female
        // BMR = 10*60 + 6.25*165 - 5*30 - 161 = 600 + 1031.25 - 150 - 161 = 1320.25
        // TDEE = 1320.25 * 1.375 = 1815
        val targets = MacroTargets.calculateTdee(
            weightKg = 60.0,
            heightCm = 165.0,
            ageYears = 30,
            isMale = false,
            activityMultiplier = 1.375
        )

        assertEquals(1815, targets.dailyCalories)
        assertTrue(targets.proteinGrams > 0)
        assertTrue(targets.carbGrams > 0)
        assertTrue(targets.fatGrams > 0)
    }

    @Test
    fun testDefaultTargetsConsistency() {
        val targets = MacroTargets()
        assertEquals(2250, targets.dailyCalories)
        assertEquals(160, targets.proteinGrams)
        assertEquals(220, targets.carbGrams)
        assertEquals(75, targets.fatGrams)
    }
}
