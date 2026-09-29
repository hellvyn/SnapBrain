package com.snapbrain.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.snapbrain.app.R

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: FontWeight) =
    Font(R.font.plus_jakarta_sans, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Plus Jakarta Sans ships as one variable font file; each weight is a variation of it. */
val Jakarta = FontFamily(
    jakarta(FontWeight.Normal),
    jakarta(FontWeight.Medium),
    jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold),
    jakarta(FontWeight.ExtraBold),
)

private val base = Typography()

val SnapTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Jakarta),
    displayMedium = base.displayMedium.copy(fontFamily = Jakarta),
    displaySmall = base.displaySmall.copy(fontFamily = Jakarta),
    headlineLarge = base.headlineLarge.copy(fontFamily = Jakarta),
    headlineMedium = base.headlineMedium.copy(fontFamily = Jakarta),
    headlineSmall = base.headlineSmall.copy(fontFamily = Jakarta),
    titleLarge = base.titleLarge.copy(fontFamily = Jakarta),
    titleMedium = base.titleMedium.copy(fontFamily = Jakarta),
    titleSmall = base.titleSmall.copy(fontFamily = Jakarta),
    bodyLarge = base.bodyLarge.copy(fontFamily = Jakarta),
    bodyMedium = base.bodyMedium.copy(fontFamily = Jakarta),
    bodySmall = base.bodySmall.copy(fontFamily = Jakarta),
    labelLarge = base.labelLarge.copy(fontFamily = Jakarta),
    labelMedium = base.labelMedium.copy(fontFamily = Jakarta),
    labelSmall = base.labelSmall.copy(fontFamily = Jakarta),
)
