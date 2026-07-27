package com.example.remotemouse

import android.graphics.drawable.GradientDrawable

/**
 * Строит GradientDrawable "на лету" под цвета конкретной темы. Используется
 * вместо статичных drawable-ресурсов там, где цвет должен меняться в
 * зависимости от выбранной темы оформления.
 */
class ThemeDrawables(private val density: Float) {

    private fun dp(v: Float) = (v * density).toInt()

    fun outlineButton(theme: AppTheme): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(10f).toFloat()
        setColor(theme.bgPanelLight)
        setStroke(dp(1f), theme.accentDim)
    }

    fun primaryButton(theme: AppTheme): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(10f).toFloat()
        setColor(theme.accent)
    }

    fun circleButton(theme: AppTheme): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(theme.bgPanel)
        setStroke(dp(1f), theme.accent)
    }

    fun touchpad(theme: AppTheme): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(18f).toFloat()
        setColor(theme.bgPanelLight)
        setStroke(dp(1.5f).coerceAtLeast(1), theme.accent)
    }

    fun scrollTrack(theme: AppTheme): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(14f).toFloat()
        setColor(theme.bgPanelLight)
        setStroke(dp(1f), theme.accentDim)
    }

    fun circleTrack(theme: AppTheme): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(theme.bgPanelLight)
        setStroke(dp(1.5f).coerceAtLeast(1), theme.accentDim)
    }

    fun editText(theme: AppTheme): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(8f).toFloat()
        setColor(theme.bgDeep)
        setStroke(dp(1f), theme.accentDim)
    }

    fun panel(theme: AppTheme): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(14f).toFloat()
        setColor(theme.bgPanel)
        setStroke(dp(1f), theme.accentDim)
    }

    fun tabSelected(theme: AppTheme): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(10f).toFloat()
        setColor(theme.bgPanelLight)
        setStroke(dp(1.5f).coerceAtLeast(1), theme.accent)
    }

    fun tabUnselected(theme: AppTheme): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(10f).toFloat()
        setColor(theme.bgPanel)
    }
}
