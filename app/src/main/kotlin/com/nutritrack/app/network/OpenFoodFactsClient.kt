package com.nutritrack.app.network

import com.nutritrack.app.model.OpenFoodFactsProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenFoodFactsClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val BASE_URL = "https://world.openfoodfacts.org/api/v2/product"
        private const val USER_AGENT = "NutriTrackPixel/1.0 (Google Pixel 10 Pro; Android; contact@nutritrack.app)"
    }

    /**
     * Look up a food product by barcode against Open Food Facts API.
     * Note: This is the ONLY outbound network request permitted in the entire application.
     */
    suspend fun fetchProductByBarcode(barcode: String): Result<OpenFoodFactsProduct> = withContext(Dispatchers.IO) {
        val cleanBarcode = barcode.trim()
        val url = "$BASE_URL/$cleanBarcode.json"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Open Food Facts server returned HTTP ${response.code}")
                    )
                }

                val bodyStr = response.body?.string() ?: return@withContext Result.failure(
                    IOException("Empty response body from Open Food Facts")
                )

                val root = JSONObject(bodyStr)
                val status = root.optInt("status", 0)
                if (status != 1) {
                    val statusVerbose = root.optString("status_verbose", "Product not found in Open Food Facts")
                    return@withContext Result.failure(IOException(statusVerbose))
                }

                val productObj = root.optJSONObject("product") ?: return@withContext Result.failure(
                    IOException("Missing product payload in Open Food Facts response")
                )

                val name = productObj.optString("product_name").ifEmpty {
                    productObj.optString("product_name_en").ifEmpty { "Scanned Food Product ($cleanBarcode)" }
                }

                val brand = productObj.optString("brands").ifEmpty { null }
                val servingSize = productObj.optString("serving_size").ifEmpty { null }
                val servingQuantity = if (productObj.has("serving_quantity")) {
                    productObj.optDouble("serving_quantity")
                } else null

                val nutriments = productObj.optJSONObject("nutriments") ?: JSONObject()

                // Calories per 100g (energy-kcal_100g or energy_100g converted from kJ)
                val calories100g = when {
                    nutriments.has("energy-kcal_100g") -> nutriments.optDouble("energy-kcal_100g", 0.0)
                    nutriments.has("energy-kcal") -> nutriments.optDouble("energy-kcal", 0.0)
                    nutriments.has("energy_100g") -> nutriments.optDouble("energy_100g", 0.0) / 4.184
                    else -> 0.0
                }

                val protein100g = nutriments.optDouble("proteins_100g", 0.0)
                val carbs100g = nutriments.optDouble("carbohydrates_100g", 0.0)
                val fat100g = nutriments.optDouble("fat_100g", 0.0)

                // Serving macros
                val caloriesServing = when {
                    nutriments.has("energy-kcal_serving") -> nutriments.optDouble("energy-kcal_serving")
                    servingQuantity != null && servingQuantity > 0 -> (calories100g * servingQuantity) / 100.0
                    else -> null
                }

                val proteinServing = when {
                    nutriments.has("proteins_serving") -> nutriments.optDouble("proteins_serving")
                    servingQuantity != null && servingQuantity > 0 -> (protein100g * servingQuantity) / 100.0
                    else -> null
                }

                val carbsServing = when {
                    nutriments.has("carbohydrates_serving") -> nutriments.optDouble("carbohydrates_serving")
                    servingQuantity != null && servingQuantity > 0 -> (carbs100g * servingQuantity) / 100.0
                    else -> null
                }

                val fatServing = when {
                    nutriments.has("fat_serving") -> nutriments.optDouble("fat_serving")
                    servingQuantity != null && servingQuantity > 0 -> (fat100g * servingQuantity) / 100.0
                    else -> null
                }

                val nutriScore = productObj.optString("nutriscore_grade").ifEmpty { null }
                val imageUrl = productObj.optString("image_url").ifEmpty {
                    productObj.optString("image_front_url").ifEmpty { null }
                }
                val ingredients = productObj.optString("ingredients_text").ifEmpty { null }

                val parsed = OpenFoodFactsProduct(
                    barcode = cleanBarcode,
                    name = name,
                    brand = brand,
                    servingSize = servingSize,
                    servingQuantityGrams = servingQuantity,
                    calories100g = calories100g,
                    protein100g = protein100g,
                    carbs100g = carbs100g,
                    fat100g = fat100g,
                    caloriesServing = caloriesServing,
                    proteinServing = proteinServing,
                    carbsServing = carbsServing,
                    fatServing = fatServing,
                    nutriScore = nutriScore?.uppercase(),
                    imageUrl = imageUrl,
                    ingredientsText = ingredients
                )

                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
