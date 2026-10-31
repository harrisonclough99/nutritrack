package com.nutritrack.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NutriScoreBadge(
    grade: String?,
    modifier: Modifier = Modifier
) {
    if (grade.isNullOrBlank()) return

    val cleanGrade = grade.trim().uppercase()
    val grades = listOf("A", "B", "C", "D", "E")

    val gradeColors = mapOf(
        "A" to Color(0xFF038141),
        "B" to Color(0xFF85BB2F),
        "C" to Color(0xFFFECB02),
        "D" to Color(0xFFEE8100),
        "E" to Color(0xFFE63E11)
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF222222))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "NUTRI-SCORE",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 6.dp)
        )

        grades.forEach { g ->
            val isSelected = g == cleanGrade
            val bgColor = gradeColors[g] ?: Color.Gray

            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .size(if (isSelected) 20.dp else 14.dp)
                    .clip(RoundedCornerShape(if (isSelected) 5.dp else 3.dp))
                    .background(if (isSelected) bgColor else bgColor.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = g,
                    color = Color.White,
                    fontSize = if (isSelected) 12.sp else 8.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal
                )
            }
        }
    }
}
