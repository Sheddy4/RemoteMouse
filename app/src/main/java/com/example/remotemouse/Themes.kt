package com.example.remotemouse

import android.graphics.Color

/**
 * Палитра одной темы. bgDeep/bgPanel/bgPanelLight - фоны разной "глубины",
 * accent/accentDim - основной акцентный цвет и его приглушённый вариант
 * (для рамок), textPrimary/textSecondary - цвета текста.
 */
data class AppTheme(
    val id: String,
    val displayName: String,
    val bgDeep: Int,
    val bgPanel: Int,
    val bgPanelLight: Int,
    val accent: Int,
    val accentDim: Int,
    val textPrimary: Int,
    val textSecondary: Int
)

object Themes {

    val CYAN = AppTheme(
        id = "cyan", displayName = "Cyan",
        bgDeep = Color.parseColor("#070B12"),
        bgPanel = Color.parseColor("#0E1826"),
        bgPanelLight = Color.parseColor("#152538"),
        accent = Color.parseColor("#00E5FF"),
        accentDim = Color.parseColor("#0F7A8C"),
        textPrimary = Color.parseColor("#EAF6FB"),
        textSecondary = Color.parseColor("#93B8CC")
    )

    val PURPLE = AppTheme(
        id = "purple", displayName = "Purple",
        bgDeep = Color.parseColor("#0B0714"),
        bgPanel = Color.parseColor("#181026"),
        bgPanelLight = Color.parseColor("#231633"),
        accent = Color.parseColor("#B388FF"),
        accentDim = Color.parseColor("#6A4A99"),
        textPrimary = Color.parseColor("#F3EAFB"),
        textSecondary = Color.parseColor("#B79BCC")
    )

    val GREEN = AppTheme(
        id = "green", displayName = "Matrix",
        bgDeep = Color.parseColor("#050F0A"),
        bgPanel = Color.parseColor("#0C1F14"),
        bgPanelLight = Color.parseColor("#122B1B"),
        accent = Color.parseColor("#39FF88"),
        accentDim = Color.parseColor("#1F8A50"),
        textPrimary = Color.parseColor("#E8FBEF"),
        textSecondary = Color.parseColor("#8FCBA8")
    )

    val ORANGE = AppTheme(
        id = "orange", displayName = "Amber",
        bgDeep = Color.parseColor("#140D05"),
        bgPanel = Color.parseColor("#241708"),
        bgPanelLight = Color.parseColor("#33200C"),
        accent = Color.parseColor("#FFA733"),
        accentDim = Color.parseColor("#996A1F"),
        textPrimary = Color.parseColor("#FBF1E8"),
        textSecondary = Color.parseColor("#CCAE8F")
    )

    val RED = AppTheme(
        id = "red", displayName = "Alert",
        bgDeep = Color.parseColor("#140507"),
        bgPanel = Color.parseColor("#26090C"),
        bgPanelLight = Color.parseColor("#331014"),
        accent = Color.parseColor("#FF4D5E"),
        accentDim = Color.parseColor("#992630"),
        textPrimary = Color.parseColor("#FBE8EA"),
        textSecondary = Color.parseColor("#CC8F95")
    )

    val BLUE = AppTheme(
        id = "blue", displayName = "Ocean",
        bgDeep = Color.parseColor("#050914"),
        bgPanel = Color.parseColor("#0C1526"),
        bgPanelLight = Color.parseColor("#121F33"),
        accent = Color.parseColor("#3399FF"),
        accentDim = Color.parseColor("#1F5C99"),
        textPrimary = Color.parseColor("#E8F1FB"),
        textSecondary = Color.parseColor("#8FAECC")
    )

    val presets = listOf(CYAN, PURPLE, GREEN, ORANGE, RED, BLUE)

    /** Приглушённый (для рамок/pressed-состояния) вариант произвольного цвета. */
    fun dimVariant(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return Color.HSVToColor(floatArrayOf(hsv[0], 0.55f, 0.45f))
    }

    /** Строит тёмную тему на основе одного выбранного пользователем цвета. */
    fun buildCustom(accentColor: Int): AppTheme {
        val hsv = FloatArray(3)
        Color.colorToHSV(accentColor, hsv)
        val hue = hsv[0]
        fun c(s: Float, v: Float) = Color.HSVToColor(floatArrayOf(hue, s, v))
        return AppTheme(
            id = "custom",
            displayName = "Свой цвет",
            bgDeep = c(0.35f, 0.06f),
            bgPanel = c(0.35f, 0.10f),
            bgPanelLight = c(0.35f, 0.15f),
            accent = accentColor,
            accentDim = dimVariant(accentColor),
            textPrimary = c(0.08f, 0.96f),
            textSecondary = c(0.20f, 0.75f)
        )
    }
}
