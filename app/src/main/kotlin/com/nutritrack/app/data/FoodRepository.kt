package com.nutritrack.app.data

import android.content.ContentValues
import android.content.Context
import com.nutritrack.app.model.FoodItem
import com.nutritrack.app.model.MacroSource
import com.nutritrack.app.model.MacroTargets
import com.nutritrack.app.model.MealType
import com.nutritrack.app.model.OpenFoodFactsProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Calendar

class FoodRepository(context: Context) {
    private val dbHelper = FoodDatabaseHelper(context.applicationContext)

    private val _diaryUpdatedFlow = MutableStateFlow(System.currentTimeMillis())
    val diaryUpdates: Flow<Long> = _diaryUpdatedFlow.asStateFlow()

    private val _targetsState = MutableStateFlow(loadTargetsSync())
    val targets: Flow<MacroTargets> = _targetsState.asStateFlow()

    private fun loadTargetsSync(): MacroTargets {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            FoodDatabaseHelper.TABLE_TARGETS,
            null,
            "id = 1",
            null,
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val cal = it.getInt(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_TARGET_CALORIES))
                val pro = it.getInt(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_TARGET_PROTEIN))
                val carb = it.getInt(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_TARGET_CARBS))
                val fat = it.getInt(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_TARGET_FAT))
                return MacroTargets(cal, pro, carb, fat)
            }
        }
        return MacroTargets()
    }

    suspend fun updateTargets(targets: MacroTargets) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put(FoodDatabaseHelper.COL_TARGET_CALORIES, targets.dailyCalories)
            put(FoodDatabaseHelper.COL_TARGET_PROTEIN, targets.proteinGrams)
            put(FoodDatabaseHelper.COL_TARGET_CARBS, targets.carbGrams)
            put(FoodDatabaseHelper.COL_TARGET_FAT, targets.fatGrams)
        }
        db.update(FoodDatabaseHelper.TABLE_TARGETS, cv, "id = 1", null)
        _targetsState.value = targets
    }

    suspend fun logFoodItem(item: FoodItem) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put(FoodDatabaseHelper.COL_ID, item.id)
            put(FoodDatabaseHelper.COL_NAME, item.name)
            put(FoodDatabaseHelper.COL_BRAND, item.brand)
            put(FoodDatabaseHelper.COL_MEAL_TYPE, item.mealType.name)
            put(FoodDatabaseHelper.COL_TIMESTAMP, item.timestamp)
            put(FoodDatabaseHelper.COL_CALORIES, item.calories)
            put(FoodDatabaseHelper.COL_PROTEIN, item.protein)
            put(FoodDatabaseHelper.COL_CARBS, item.carbohydrates)
            put(FoodDatabaseHelper.COL_FAT, item.fat)
            put(FoodDatabaseHelper.COL_MASS_GRAMS, item.massGrams)
            put(FoodDatabaseHelper.COL_SOURCE, item.source.name)
            put(FoodDatabaseHelper.COL_BARCODE, item.barcode)
            put(FoodDatabaseHelper.COL_NUTRI_SCORE, item.nutriScore)
            put(FoodDatabaseHelper.COL_CONFIDENCE, item.confidence)
            put(FoodDatabaseHelper.COL_IMAGE_URI, item.imageUri)
            put(FoodDatabaseHelper.COL_SERVING_DESC, item.servingSizeDescription)
        }
        db.insertWithOnConflict(FoodDatabaseHelper.TABLE_DIARY, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        _diaryUpdatedFlow.value = System.currentTimeMillis()
    }

    suspend fun deleteFoodItem(id: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(FoodDatabaseHelper.TABLE_DIARY, "${FoodDatabaseHelper.COL_ID} = ?", arrayOf(id))
        _diaryUpdatedFlow.value = System.currentTimeMillis()
    }

    suspend fun getFoodItemsForDay(epochMillis: Long): List<FoodItem> = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = epochMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis

        val db = dbHelper.readableDatabase
        val items = mutableListOf<FoodItem>()
        val cursor = db.query(
            FoodDatabaseHelper.TABLE_DIARY,
            null,
            "${FoodDatabaseHelper.COL_TIMESTAMP} >= ? AND ${FoodDatabaseHelper.COL_TIMESTAMP} < ?",
            arrayOf(startOfDay.toString(), endOfDay.toString()),
            null,
            null,
            "${FoodDatabaseHelper.COL_TIMESTAMP} ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_ID))
                val name = it.getString(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_NAME))
                val brand = it.getString(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BRAND))
                val mealTypeStr = it.getString(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_MEAL_TYPE))
                val ts = it.getLong(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_TIMESTAMP))
                val cal = it.getDouble(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_CALORIES))
                val pro = it.getDouble(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_PROTEIN))
                val carb = it.getDouble(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_CARBS))
                val fat = it.getDouble(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_FAT))
                val mass = it.getDouble(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_MASS_GRAMS))
                val sourceStr = it.getString(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_SOURCE))
                val barcode = it.getString(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BARCODE))
                val nutri = it.getString(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_NUTRI_SCORE))
                val conf = if (it.isNull(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_CONFIDENCE))) null else it.getFloat(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_CONFIDENCE))
                val img = it.getString(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_IMAGE_URI))
                val desc = it.getString(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_SERVING_DESC))

                items.add(
                    FoodItem(
                        id = id,
                        name = name,
                        brand = brand,
                        mealType = runCatching { MealType.valueOf(mealTypeStr) }.getOrDefault(MealType.LUNCH),
                        timestamp = ts,
                        calories = cal,
                        protein = pro,
                        carbohydrates = carb,
                        fat = fat,
                        massGrams = mass,
                        source = runCatching { MacroSource.valueOf(sourceStr) }.getOrDefault(MacroSource.MANUAL),
                        barcode = barcode,
                        nutriScore = nutri,
                        confidence = conf,
                        imageUri = img,
                        servingSizeDescription = desc
                    )
                )
            }
        }
        items
    }

    suspend fun getCachedBarcodeProduct(barcode: String): OpenFoodFactsProduct? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            FoodDatabaseHelper.TABLE_BARCODE_CACHE,
            null,
            "${FoodDatabaseHelper.COL_BC_CODE} = ?",
            arrayOf(barcode),
            null,
            null,
            null
        )

        cursor.use {
            if (it.moveToFirst()) {
                return@withContext OpenFoodFactsProduct(
                    barcode = barcode,
                    name = it.getString(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_NAME)),
                    brand = it.getString(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_BRAND)),
                    servingSize = it.getString(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_SERVING_SIZE)),
                    servingQuantityGrams = if (it.isNull(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_SERVING_GRAMS))) null else it.getDouble(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_SERVING_GRAMS)),
                    calories100g = it.getDouble(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_CALORIES_100G)),
                    protein100g = it.getDouble(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_PROTEIN_100G)),
                    carbs100g = it.getDouble(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_CARBS_100G)),
                    fat100g = it.getDouble(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_FAT_100G)),
                    caloriesServing = if (it.isNull(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_CALORIES_SERVING))) null else it.getDouble(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_CALORIES_SERVING)),
                    proteinServing = if (it.isNull(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_PROTEIN_SERVING))) null else it.getDouble(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_PROTEIN_SERVING)),
                    carbsServing = if (it.isNull(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_CARBS_SERVING))) null else it.getDouble(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_CARBS_SERVING)),
                    fatServing = if (it.isNull(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_FAT_SERVING))) null else it.getDouble(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_FAT_SERVING)),
                    nutriScore = it.getString(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_NUTRI_SCORE)),
                    imageUrl = it.getString(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_IMAGE_URL)),
                    ingredientsText = it.getString(it.getColumnIndexOrThrow(FoodDatabaseHelper.COL_BC_INGREDIENTS))
                )
            }
        }
        null
    }

    suspend fun cacheBarcodeProduct(product: OpenFoodFactsProduct) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put(FoodDatabaseHelper.COL_BC_CODE, product.barcode)
            put(FoodDatabaseHelper.COL_BC_NAME, product.name)
            put(FoodDatabaseHelper.COL_BC_BRAND, product.brand)
            put(FoodDatabaseHelper.COL_BC_SERVING_SIZE, product.servingSize)
            put(FoodDatabaseHelper.COL_BC_SERVING_GRAMS, product.servingQuantityGrams)
            put(FoodDatabaseHelper.COL_BC_CALORIES_100G, product.calories100g)
            put(FoodDatabaseHelper.COL_BC_PROTEIN_100G, product.protein100g)
            put(FoodDatabaseHelper.COL_BC_CARBS_100G, product.carbs100g)
            put(FoodDatabaseHelper.COL_BC_FAT_100G, product.fat100g)
            put(FoodDatabaseHelper.COL_BC_CALORIES_SERVING, product.caloriesServing)
            put(FoodDatabaseHelper.COL_BC_PROTEIN_SERVING, product.proteinServing)
            put(FoodDatabaseHelper.COL_BC_CARBS_SERVING, product.carbsServing)
            put(FoodDatabaseHelper.COL_BC_FAT_SERVING, product.fatServing)
            put(FoodDatabaseHelper.COL_BC_NUTRI_SCORE, product.nutriScore)
            put(FoodDatabaseHelper.COL_BC_IMAGE_URL, product.imageUrl)
            put(FoodDatabaseHelper.COL_BC_INGREDIENTS, product.ingredientsText)
        }
        db.insertWithOnConflict(FoodDatabaseHelper.TABLE_BARCODE_CACHE, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }
}
