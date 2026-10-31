package com.nutritrack.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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

class FoodDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "nutritrack_local.db"
        private const val DATABASE_VERSION = 1

        // Table: Food Diary
        const val TABLE_DIARY = "food_diary"
        const val COL_ID = "id"
        const val COL_NAME = "name"
        const val COL_BRAND = "brand"
        const val COL_MEAL_TYPE = "meal_type"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_CALORIES = "calories"
        const val COL_PROTEIN = "protein"
        const val COL_CARBS = "carbohydrates"
        const val COL_FAT = "fat"
        const val COL_MASS_GRAMS = "mass_grams"
        const val COL_SOURCE = "source"
        const val COL_BARCODE = "barcode"
        const val COL_NUTRI_SCORE = "nutri_score"
        const val COL_CONFIDENCE = "confidence"
        const val COL_IMAGE_URI = "image_uri"
        const val COL_SERVING_DESC = "serving_desc"

        // Table: Barcode Cache (to avoid repeated network hits for already scanned items)
        const val TABLE_BARCODE_CACHE = "barcode_cache"
        const val COL_BC_CODE = "barcode"
        const val COL_BC_NAME = "name"
        const val COL_BC_BRAND = "brand"
        const val COL_BC_SERVING_SIZE = "serving_size"
        const val COL_BC_SERVING_GRAMS = "serving_grams"
        const val COL_BC_CALORIES_100G = "calories_100g"
        const val COL_BC_PROTEIN_100G = "protein_100g"
        const val COL_BC_CARBS_100G = "carbs_100g"
        const val COL_BC_FAT_100G = "fat_100g"
        const val COL_BC_CALORIES_SERVING = "calories_serving"
        const val COL_BC_PROTEIN_SERVING = "protein_serving"
        const val COL_BC_CARBS_SERVING = "carbs_serving"
        const val COL_BC_FAT_SERVING = "fat_serving"
        const val COL_BC_NUTRI_SCORE = "nutri_score"
        const val COL_BC_IMAGE_URL = "image_url"
        const val COL_BC_INGREDIENTS = "ingredients"

        // Table: Macro Targets
        const val TABLE_TARGETS = "macro_targets"
        const val COL_TARGET_CALORIES = "daily_calories"
        const val COL_TARGET_PROTEIN = "protein_grams"
        const val COL_TARGET_CARBS = "carb_grams"
        const val COL_TARGET_FAT = "fat_grams"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_DIARY (
                $COL_ID TEXT PRIMARY KEY,
                $COL_NAME TEXT NOT NULL,
                $COL_BRAND TEXT,
                $COL_MEAL_TYPE TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_CALORIES REAL NOT NULL,
                $COL_PROTEIN REAL NOT NULL,
                $COL_CARBS REAL NOT NULL,
                $COL_FAT REAL NOT NULL,
                $COL_MASS_GRAMS REAL NOT NULL,
                $COL_SOURCE TEXT NOT NULL,
                $COL_BARCODE TEXT,
                $COL_NUTRI_SCORE TEXT,
                $COL_CONFIDENCE REAL,
                $COL_IMAGE_URI TEXT,
                $COL_SERVING_DESC TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_BARCODE_CACHE (
                $COL_BC_CODE TEXT PRIMARY KEY,
                $COL_BC_NAME TEXT NOT NULL,
                $COL_BC_BRAND TEXT,
                $COL_BC_SERVING_SIZE TEXT,
                $COL_BC_SERVING_GRAMS REAL,
                $COL_BC_CALORIES_100G REAL NOT NULL,
                $COL_BC_PROTEIN_100G REAL NOT NULL,
                $COL_BC_CARBS_100G REAL NOT NULL,
                $COL_BC_FAT_100G REAL NOT NULL,
                $COL_BC_CALORIES_SERVING REAL,
                $COL_BC_PROTEIN_SERVING REAL,
                $COL_BC_CARBS_SERVING REAL,
                $COL_BC_FAT_SERVING REAL,
                $COL_BC_NUTRI_SCORE TEXT,
                $COL_BC_IMAGE_URL TEXT,
                $COL_BC_INGREDIENTS TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_TARGETS (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                $COL_TARGET_CALORIES INTEGER NOT NULL,
                $COL_TARGET_PROTEIN INTEGER NOT NULL,
                $COL_TARGET_CARBS INTEGER NOT NULL,
                $COL_TARGET_FAT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // Default targets for active adult
        db.execSQL("INSERT INTO $TABLE_TARGETS VALUES (1, 2200, 160, 220, 70)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DIARY")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BARCODE_CACHE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TARGETS")
        onCreate(db)
    }
}
