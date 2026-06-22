package com.automatic.attendance.student.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PrimaryColor = Color(0xFF1976D2)
private val PrimaryVariant = Color(0xFF1565C0)
private val SecondaryColor = Color(0xFF03DAC5)
private val Background = Color(0xFFFAFAFA)
private val Surface = Color(0xFFFFFFFF)
private val Error = Color(0xFFB00020)
private val OnPrimary = Color.White
private val OnSecondary = Color.Black
private val OnBackground = Color.Black
private val OnSurface = Color.Black
private val OnError = Color.White

private val LightColorPalette = lightColors(
    primary = PrimaryColor,
    primaryVariant = PrimaryVariant,
    secondary = SecondaryColor,
    background = Background,
    surface = Surface,
    error = Error,
    onPrimary = OnPrimary,
    onSecondary = OnSecondary,
    onBackground = OnBackground,
    onSurface = OnSurface,
    onError = OnError
)

@Composable
fun AttendanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = LightColorPalette,
        content = content
    )
}
