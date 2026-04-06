package dev.davwheat.openfuelmap.common.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val ColorScheme.warning: Color
    get() = if (surface.luminance() > 0.5f) Color(0xFFE65100) else Color(0xFFFFB74D)

val ColorScheme.warningContainer: Color
    get() = warning.copy(alpha = 0.12f)

val ColorScheme.onWarningContainer: Color
    get() = if (surface.luminance() > 0.5f) Color(0xFFBF360C) else Color(0xFFFFCC80)
