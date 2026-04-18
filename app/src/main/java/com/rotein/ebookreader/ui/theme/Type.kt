package com.rotein.ebookreader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rotein.ebookreader.R

@OptIn(ExperimentalTextApi::class)
val Pretendard = FontFamily(
    Font(
        R.font.pretendard_variable,
        weight = FontWeight.Thin,
        variationSettings = FontVariation.Settings(FontVariation.weight(100))
    ),
    Font(
        R.font.pretendard_variable,
        weight = FontWeight.ExtraLight,
        variationSettings = FontVariation.Settings(FontVariation.weight(200))
    ),
    Font(
        R.font.pretendard_variable,
        weight = FontWeight.Light,
        variationSettings = FontVariation.Settings(FontVariation.weight(300))
    ),
    Font(
        R.font.pretendard_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.pretendard_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.pretendard_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.pretendard_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
    Font(
        R.font.pretendard_variable,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))
    ),
    Font(
        R.font.pretendard_variable,
        weight = FontWeight.Black,
        variationSettings = FontVariation.Settings(FontVariation.weight(900))
    ),
)

private val defaultTextStyle = TextStyle(fontFamily = Pretendard)

val Typography = Typography(
    displayLarge = defaultTextStyle,
    displayMedium = defaultTextStyle,
    displaySmall = defaultTextStyle,
    headlineLarge = defaultTextStyle,
    headlineMedium = defaultTextStyle,
    headlineSmall = defaultTextStyle,
    titleLarge = defaultTextStyle,
    titleMedium = defaultTextStyle.copy(fontSize = EreaderFontSize.L),
    titleSmall = defaultTextStyle.copy(fontSize = EreaderFontSize.M),
    bodyLarge = defaultTextStyle.copy(fontSize = EreaderFontSize.L, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = defaultTextStyle.copy(fontSize = EreaderFontSize.M),
    bodySmall = defaultTextStyle.copy(fontSize = EreaderFontSize.S),
    labelLarge = defaultTextStyle.copy(fontSize = EreaderFontSize.M),
    labelMedium = defaultTextStyle.copy(fontSize = EreaderFontSize.S),
    labelSmall = defaultTextStyle.copy(fontSize = EreaderFontSize.XS),
)
