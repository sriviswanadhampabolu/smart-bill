package com.grocer.billing.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = GreenLight,
    onPrimaryContainer = GreenDark,
    secondary = OrangeAccent,
    onSecondary = Color.White,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    error = AlertRed,
    onError = Color.White,
    errorContainer = AlertRedLight,
    onErrorContainer = AlertRed
)

@Composable
fun KiranaBillingTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
