package com.smartattendance.teacher.ui.attendance

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AttendanceSearchBar(

    query: String,

    onQueryChange: (String) -> Unit

) {

    OutlinedTextField(

        value = query,

        onValueChange = onQueryChange,

        modifier = Modifier.fillMaxWidth(),

        singleLine = true,

        leadingIcon = {

            Icon(

                Icons.Default.Search,

                contentDescription = null

            )

        },

        placeholder = {

            Text("Search by Name or USN")

        }

    )

}