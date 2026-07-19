package com.smartattendance.teacher.ui.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*

@Composable
fun AttendanceFilterChips(

    selected: AttendanceFilter,

    onSelected: (AttendanceFilter) -> Unit

) {

    Row(

        modifier = Modifier.fillMaxWidth(),

        horizontalArrangement = Arrangement.spacedBy(8.dp)

    ) {

        AttendanceFilter.entries.forEach { filter ->

            FilterChip(

                selected = selected == filter,

                onClick = {

                    onSelected(filter)

                },

                label = {

                    Text(

                        filter.name.replace('_', ' ')

                    )

                }

            )

        }

    }

}