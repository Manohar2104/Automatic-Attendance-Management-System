package com.automatic.attendance.student.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable

private val DarkColorPalette = darkColors(

    primary = AccentBlue,

    primaryVariant = AccentBlue,

    secondary = AccentGreen,

    background = DarkBackground,

    surface = DarkSurface,

    onPrimary = WhiteText,

    onSecondary = WhiteText,

    onBackground = WhiteText,

    onSurface = WhiteText
)

@Composable
fun AttendanceTheme(
    content: @Composable () -> Unit
) {

    MaterialTheme(
        colors = DarkColorPalette,
        content = content
    )
}