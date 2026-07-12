package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val SophisticatedColorScheme = darkColorScheme(
  primary = SophisticatedPrimary,
  onPrimary = SophisticatedOnPrimary,
  secondary = SophisticatedSecondary,
  onSecondary = SophisticatedOnSecondary,
  background = SophisticatedBackground,
  onBackground = SophisticatedTextPrimary,
  surface = SophisticatedSurface,
  onSurface = SophisticatedTextPrimary,
  surfaceVariant = SophisticatedSurfaceVariant,
  onSurfaceVariant = SophisticatedOnSurfaceVariant,
  tertiary = PurpleGrey80
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      else -> SophisticatedColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
