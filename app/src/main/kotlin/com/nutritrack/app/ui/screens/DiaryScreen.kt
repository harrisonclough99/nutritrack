package com.nutritrack.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutritrack.app.data.FoodRepository
import com.nutritrack.app.model.FoodItem
import com.nutritrack.app.ui.components.MacroBar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DiaryScreen(
    repository: FoodRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var selectedEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dayItems by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    val lastUpdate by repository.diaryUpdates.collectAsState(initial = 0L)

    val targets by repository.targets.collectAsState(initial = com.nutritrack.app.model.MacroTargets())

    LaunchedEffect(selectedEpochMillis, lastUpdate) {
        dayItems = repository.getFoodItemsForDay(selectedEpochMillis)
    }

    val dateFormatted = remember(selectedEpochMillis) {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedEpochMillis }
        val today = Calendar.getInstance()

        if (calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        ) {
            "Today, " + SimpleDateFormat("MMM d", Locale.getDefault()).format(calendar.time)
        } else {
            SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(calendar.time)
        }
    }

    val totalCal = dayItems.sumOf { it.calories }
    val totalPro = dayItems.sumOf { it.protein }
    val totalCarb = dayItems.sumOf { it.carbohydrates }
    val totalFat = dayItems.sumOf { it.fat }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Date Navigator
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = selectedEpochMillis
                        add(Calendar.DAY_OF_YEAR, -1)
                    }
                    selectedEpochMillis = cal.timeInMillis
                }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Day")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { selectedEpochMillis = System.currentTimeMillis() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Today,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = dateFormatted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = selectedEpochMillis
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                    selectedEpochMillis = cal.timeInMillis
                }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Day")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Daily Total Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Day Summary",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${totalCal.roundToInt()} / ${targets.dailyCalories} kcal",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MacroBar("Protein", totalPro, targets.proteinGrams, Color(0xFF29B6F6), Modifier.weight(1f))
                    MacroBar("Carbs", totalCarb, targets.carbGrams, Color(0xFFFFA726), Modifier.weight(1f))
                    MacroBar("Fat", totalFat, targets.fatGrams, Color(0xFFEF5350), Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Logged Items (${dayItems.size})",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (dayItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No food items recorded for this date.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dayItems, key = { it.id }) { item ->
                    FoodItemRow(
                        item = item,
                        onDelete = {
                            scope.launch {
                                repository.deleteFoodItem(item.id)
                            }
                        }
                    )
                }
            }
        }
    }
}
