package com.smartattendance.teacher.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionCard(
    connected: Boolean
) {

    Card {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),

            verticalAlignment = Alignment.CenterVertically

        ) {

            Icon(
                imageVector =
                if (connected)
                    Icons.Default.CloudDone
                else
                    Icons.Default.CloudOff,

                contentDescription = null,

                tint =
                if (connected)
                    Color(0xFF2E7D32)
                else
                    Color.Red
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {

                Text(
                    text =
                    if (connected)
                        "Server Connected"
                    else
                        "Server Disconnected",

                    style = MaterialTheme.typography.titleMedium,

                    fontWeight = FontWeight.Bold
                )

                Text(
                    text =
                    if (connected)
                        "Backend reachable"
                    else
                        "Unable to reach backend",

                    style = MaterialTheme.typography.bodyMedium
                )

            }

        }

    }

}