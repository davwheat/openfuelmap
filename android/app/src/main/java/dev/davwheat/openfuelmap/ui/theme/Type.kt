package dev.davwheat.openfuelmap.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.davwheat.openfuelmap.R

@OptIn(ExperimentalTextApi::class)
private fun interFont(weight: FontWeight, italic: Boolean = false): Font =
    Font(
        resId = if (italic) R.font.intervariable_italic else R.font.intervariable,
        weight = weight,
        style = if (italic) FontStyle.Italic else FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

val InterFontFamily =
    FontFamily(
        interFont(FontWeight.Thin),
        interFont(FontWeight.ExtraLight),
        interFont(FontWeight.Light),
        interFont(FontWeight.Normal),
        interFont(FontWeight.Medium),
        interFont(FontWeight.SemiBold),
        interFont(FontWeight.Bold),
        interFont(FontWeight.ExtraBold),
        interFont(FontWeight.Black),
        interFont(FontWeight.Thin, italic = true),
        interFont(FontWeight.ExtraLight, italic = true),
        interFont(FontWeight.Light, italic = true),
        interFont(FontWeight.Normal, italic = true),
        interFont(FontWeight.Medium, italic = true),
        interFont(FontWeight.SemiBold, italic = true),
        interFont(FontWeight.Bold, italic = true),
        interFont(FontWeight.ExtraBold, italic = true),
        interFont(FontWeight.Black, italic = true),
    )

val Typography = Typography(fontFamily = InterFontFamily)
