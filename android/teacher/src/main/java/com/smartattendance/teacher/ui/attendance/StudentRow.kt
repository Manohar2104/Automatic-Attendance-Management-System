package com.smartattendance.teacher.ui.attendance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartattendance.teacher.ui.components.InfoRow
import com.smartattendance.teacher.ui.components.StatusChip

@Composable
fun StudentRow(

    student: StudentAttendanceUi,

    onDetails: (StudentAttendanceUi) -> Unit = {},

    onToggleAttendance: (StudentAttendanceUi) -> Unit = {}

) {

    Card(

        modifier = Modifier
            .fillMaxWidth()
            .clickable {

                onDetails(student)

            }

    ) {

        Column(

            modifier = Modifier.padding(16.dp)

        ) {

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceBetween,

                verticalAlignment = Alignment.CenterVertically

            ) {

                Column(

                    modifier = Modifier.weight(1f)

                ) {

                    Text(

                        student.studentName,

                        style = MaterialTheme.typography.titleMedium,

                        fontWeight = FontWeight.Bold

                    )

                    Text(

                        student.usn,

                        style = MaterialTheme.typography.bodyMedium

                    )

                    Text(

                        student.studentEmail,

                        style = MaterialTheme.typography.bodySmall

                    )

                }

                StatusChip(

                    student.status.name.replace('_', ' ')

                )

            }

            Spacer(Modifier.height(12.dp))

            InfoRow(

                "Confidence",

                "${student.confidence}%"

            )

            InfoRow(

                "RSSI",

                "${student.averageRssi} dBm"

            )

            InfoRow(

                "Packets",

                student.packetsReceived.toString()

            )

            InfoRow(

                "Last Seen",

                student.lastSeen

            )

            InfoRow(

                "Last Accepted",

                student.lastAcceptedPacketTime

            )

            InfoRow(

                "Anonymous Code",

                student.anonymousCode

            )

            Spacer(Modifier.height(12.dp))

            if (student.status == AttendanceStatus.LOW_CONFIDENCE) {

                Row(

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Icon(

                        Icons.Default.Warning,

                        contentDescription = null,

                        tint = Color(0xFFFF9800)

                    )

                    Spacer(Modifier.width(8.dp))

                    Text(

                        "Low confidence detection",

                        color = Color(0xFFFF9800)

                    )

                }

                Spacer(Modifier.height(12.dp))

            }

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.End

            ) {

                OutlinedButton(

                    onClick = {

                        onDetails(student)

                    }

                ) {

                    Text("Details")

                }

                Spacer(Modifier.width(8.dp))

                Button(

                    onClick = {

                        onToggleAttendance(student)

                    }

                ) {

                    Text(

                        if (student.status == AttendanceStatus.PRESENT)

                            "Mark Absent"

                        else

                            "Mark Present"

                    )

                }

            }

        }

    }

}