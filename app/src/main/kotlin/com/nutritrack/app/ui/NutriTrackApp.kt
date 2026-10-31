package com.nutritrack.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.nutritrack.app.data.FoodRepository
import com.nutritrack.app.ui.screens.CameraScanScreen
import com.nutritrack.app.ui.screens.DashboardScreen
import com.nutritrack.app.ui.screens.DiaryScreen
import com.nutritrack.app.ui.screens.GoalsScreen

enum class AppScreen(val title: String, val icon: ImageVector) {
    DASHBOARD("Today", Icons.Default.Dashboard),
    SCANNER("Scan", Icons.Default.CameraAlt),
    DIARY("Diary", Icons.Default.CalendarMonth),
    GOALS("Goals", Icons.Default.TrackChanges)
}

@Composable
fun NutriTrackApp(
    repository: FoodRepository,
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }

    Scaffold(
        bottomBar = {
            // Hide bottom bar when inside camera scanner for immersive viewfinder
            if (currentScreen != AppScreen.SCANNER) {
                NavigationBar {
                    AppScreen.values().forEach { screen ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen },
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = currentScreen,
            label = "screen_crossfade",
            modifier = modifier.fillMaxSize().padding(innerPadding)
        ) { screen ->
            when (screen) {
                AppScreen.DASHBOARD -> DashboardScreen(
                    repository = repository,
                    onOpenScanner = { currentScreen = AppScreen.SCANNER }
                )
                AppScreen.SCANNER -> CameraScanScreen(
                    repository = repository,
                    onNavigateBack = { currentScreen = AppScreen.DASHBOARD }
                )
                AppScreen.DIARY -> DiaryScreen(
                    repository = repository
                )
                AppScreen.GOALS -> GoalsScreen(
                    repository = repository
                )
            }
        }
    }
}
