package com.smartattendance.teacher.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherTopBar(

    onRefresh: () -> Unit = {},

    onLogout: () -> Unit = {}

) {

    CenterAlignedTopAppBar(

        title = {

            Text(

                text = "Smart Attendance",

                style = MaterialTheme.typography.titleLarge

            )

        },

        navigationIcon = {

            Icon(

                imageVector = Icons.Default.School,

                contentDescription = "Teacher"

            )

        },

        actions = {

            IconButton(

                onClick = onRefresh

            ) {

                Icon(

                    imageVector = Icons.Default.Refresh,

                    contentDescription = "Refresh Dashboard"

                )

            }

            IconButton(

                onClick = onLogout

            ) {

                Icon(

                    imageVector = Icons.Default.ExitToApp,

                    contentDescription = "Logout"

                )

            }

        }

    )

}