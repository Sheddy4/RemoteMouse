package com.example.remotemouse

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.R as AndroidR

/**
 * Строит GradientDrawable/StateListDrawable "на лету" под цвета конкретной
 * темы. Используется вместо статичных drawable-ресурсов там, где цвет должен
 * меняться в зависимости от выбранной темы оформления. Кнопочные варианты
 * возвращают StateListDrawable с отдельным цветом под нажатие - иначе кнопки
 * были бы совсем плоские и без отклика на тап.
 */
class ThemeDrawables(private val density: Float) {

    private fun dp(v: Float) = (v * density).toInt()

    private fun rect(radius: Float, fill: Int, strokeWidth: Int? = null, strokeColor: Int = 0) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            if (strokeWidth != null) setStroke(strokeWidth, strokeColor)
        }

    private fun oval(fill: Int, strokeWidth: Int? = null, strokeColor: Int = 0) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            if (strokeWidth != null) setStroke(strokeWidth, strokeColor)
        }

    fun outlineButton(theme: AppTheme): StateListDrawable = StateListDrawable().apply {
        addState(
            intArrayOf(AndroidR.attr.state_pressed),
            rect(dp(10f).toFloat(), theme.accentDim)
        )
        addState(
            intArrayOf(),
            rect(dp(10f).toFloat(), theme.bgPanelLight, dp(1f), theme.accentDim)
        )
    }

    fun primaryButton(theme: AppTheme): StateListDrawable = StateListDrawable().apply {
        addState(
            intArrayOf(AndroidR.attr.state_pressed),
            rect(dp(10f).toFloat(), theme.accentDim)
        )
        addState(
            intArrayOf(),
            rect(dp(10f).toFloat(), theme.accent)
        )
    }

    fun circleButton(theme: AppTheme): StateListDrawable = StateListDrawable().apply {
        addState(
            intArrayOf(AndroidR.attr.state_pressed),
            oval(theme.accentDim)
        )
        addState(
            intArrayOf(),
            oval(theme.bgPanelLight, dp(1f), theme.accent)
        )
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
