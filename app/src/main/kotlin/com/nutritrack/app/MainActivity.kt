package com.nutritrack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.nutritrack.app.data.FoodRepository
import com.nutritrack.app.ui.NutriTrackApp
import com.nutritrack.app.ui.theme.NutriTrackTheme

class MainActivity : ComponentActivity() {
    private lateinit var repository: FoodRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FoodRepository(this)

        enableEdgeToEdge()
        setContent {
            NutriTrackTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NutriTrackApp(repository = repository)
                }
            }
        }
    }
}
