package com.smartattendance.teacher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun StatusChip(
    text: String
) {

    val color = when (text.uppercase()) {

        "ACTIVE" -> Color(0xFF4CAF50)

        "READY" -> Color(0xFF1976D2)

        "RUNNING" -> Color(0xFF2E7D32)

        "STOPPED" -> Color(0xFFD32F2F)

        "CONNECTED" -> Color(0xFF388E3C)

        else -> Color.Gray

    }

    Text(
        text = text,
        modifier = Modifier
            .background(
                color,
                RoundedCornerShape(50)
            )
            .padding(
                horizontal = 14.dp,
                vertical = 6.dp
            ),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
    )

}