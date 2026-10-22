package com.whattoeat.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 品牌色：一套绿色（"吃得健康"的直觉色彩）
private val GreenPrimary = Color(0xFF2F6B3F)
private val GreenContainer = Color(0xFFB4F0BE)

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = GreenContainer,
    onPrimaryContainer = Color(0xFF00210B),
    secondary = Color(0xFF53634F),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFDDE5DA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BD4A8),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF175229),
    onPrimaryContainer = GreenContainer,
    secondary = Color(0xFFBBCBB5),
    background = Color(0xFF1A1C19),
    surface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFF41493F),
)

/** 三大营养素固定配色，全 App 统一，形成"肌肉记忆" */
object NutrientColors {
    val Protein = Color(0xFF3C6E9F)
    val Fat = Color(0xFFC98A3B)
    val Carb = Color(0xFF7A5AA8)
    val Over = Color(0xFFB3261E) // 超量时用红色
}

@Composable
fun WhatToEatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
