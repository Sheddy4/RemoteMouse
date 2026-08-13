package com.example.remotemouse

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.R as AndroidR

/**
 * Строит GradientDrawable/RippleDrawable "на лету" под цвета конкретной
 * темы. Кнопки теперь используют RippleDrawable для плавного Material-style
 * отклика на касание вместо резкого переключения StateListDrawable.
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

    /**
     * Создаёт ripple-drawable с заданным ripple-цветом поверх content.
     * mask определяет область ripple.
     */
    private fun ripple(rippleColor: Int, content: Drawable, mask: Drawable? = null): RippleDrawable {
        val colorStateList = ColorStateList.valueOf(adjustAlpha(rippleColor, 0.3f))
        return RippleDrawable(colorStateList, content, mask ?: content)
    }

    /** Делает цвет полупрозрачным для ripple-эффекта. */
    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun outlineButton(theme: AppTheme): Drawable {
        val content = rect(dp(12f).toFloat(), theme.bgPanelLight, dp(1f), theme.accentDim)
        return ripple(theme.accent, content)
    }

    fun primaryButton(theme: AppTheme): Drawable {
        val content = rect(dp(12f).toFloat(), theme.accent)
        return ripple(Color.WHITE, content)
    }

    fun dangerButton(theme: AppTheme): Drawable {
        val content = rect(dp(12f).toFloat(), theme.bgPanelLight, dp(1f), Color.parseColor("#FF4D5E"))
        return ripple(Color.parseColor("#FF4D5E"), content)
    }

    fun circleButton(theme: AppTheme): Drawable {
        val content = oval(theme.bgPanelLight, dp(1f), theme.accent)
        val mask = oval(Color.WHITE)
        return ripple(theme.accent, content, mask)
    }

    fun touchpad(theme: AppTheme): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(20f).toFloat()
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
        cornerRadius = dp(10f).toFloat()
        setColor(theme.bgDeep)
        setStroke(dp(1f), theme.accentDim)
    }

    fun panel(theme: AppTheme): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(16f).toFloat()
        setColor(theme.bgPanel)
        setStroke(dp(1f), theme.accentDim)
    }

    fun tabSelected(theme: AppTheme): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(12f).toFloat()
        setColor(theme.bgPanelLight)
        setStroke(dp(1.5f).coerceAtLeast(1), theme.accent)
    }

    fun tabUnselected(theme: AppTheme): Drawable {
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12f).toFloat()
            setColor(theme.bgPanel)
        }
        return ripple(theme.accentDim, content)
    }
}
