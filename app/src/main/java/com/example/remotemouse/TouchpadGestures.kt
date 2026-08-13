package com.example.remotemouse

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Обрабатывает жесты тачпада:
 *  - 1 палец: движение -> курсор, короткий тап -> левый клик
 *  - 1 палец, double-tap -> двойной клик
 *  - 2 пальца, двигаются вместе по вертикали -> скролл (как колёсико мыши)
 *  - 2 пальца, расстояние между ними меняется -> зум (Ctrl+колёсико)
 *
 * leftHanded инвертирует движение по горизонтали.
 *
 * Добавлена тактильная обратная связь (вибрация) при кликах.
 * Добавлен double-tap для двойного клика.
 */
class TouchpadGestures(
    private val getClient: () -> NetworkClient?,
    var sensitivity: Float = 3.0f,
    var scrollSensitivity: Float = 4.0f,
    var leftHanded: Boolean = false,
    var hapticEnabled: Boolean = true
) : View.OnTouchListener {

    private var lastX = 0f
    private var lastY = 0f
    private var moved = false

    private var twoFingerLastDistance: Float? = null
    private var twoFingerLastY: Float? = null

    // --- Double-tap detection ---
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private val doubleTapTimeout = 300L  // мс
    private val doubleTapSlop = 50f       // px

    private fun distance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return hypot(dx, dy)
    }

    private fun averageY(event: MotionEvent): Float {
        return (event.getY(0) + event.getY(1)) / 2f
    }

    /**
     * Вибрация при клике — лёгкий тактильный отклик.
     */
    private fun vibrate(view: View) {
        if (!hapticEnabled) return
        try {
            val context = view.context
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(25)
                }
            }
        } catch (_: Exception) {
            // Вибрация не критична — игнорируем ошибки
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val client = getClient()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.parent?.requestDisallowInterceptTouchEvent(true)
                lastX = event.x
                lastY = event.y
                moved = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    twoFingerLastDistance = distance(event)
                    twoFingerLastY = averageY(event)
                    moved = true // второй палец - точно не одиночный тап
                }
            }

            MotionEvent.ACTION_MOVE -> {
                v.parent?.requestDisallowInterceptTouchEvent(true)
                if (event.pointerCount >= 2) {
                    val dist = distance(event)
                    val avgY = averageY(event)
                    val prevDist = twoFingerLastDistance
                    val prevY = twoFingerLastY

                    if (prevDist != null && prevY != null) {
                        val distDelta = dist - prevDist
                        if (abs(distDelta) > 6f) {
                            // Жест "щипок" -> зум
                            val zoomAmount = (distDelta / 25f).toInt().coerceIn(-3, 3)
                            if (zoomAmount != 0) client?.zoom(zoomAmount)
                        } else {
                            val dyDelta = avgY - prevY
                            if (abs(dyDelta) > 4f) {
                                client?.scroll((-dyDelta / 6f * scrollSensitivity).toInt())
                            }
                        }
                    }
                    twoFingerLastDistance = dist
                    twoFingerLastY = avgY
                } else {
                    val mirror = if (leftHanded) -1f else 1f
                    val dx = (event.x - lastX) * sensitivity * mirror
                    val dy = (event.y - lastY) * sensitivity
                    if (dx != 0f || dy != 0f) {
                        client?.move(dx, dy)
                        moved = true
                    }
                }
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_POINTER_UP -> {
                twoFingerLastDistance = null
                twoFingerLastY = null
            }

            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    val now = System.currentTimeMillis()
                    val timeSinceLastTap = now - lastTapTime
                    val distFromLastTap = hypot(
                        (event.x - lastTapX).toDouble(),
                        (event.y - lastTapY).toDouble()
                    ).toFloat()

                    if (timeSinceLastTap < doubleTapTimeout && distFromLastTap < doubleTapSlop) {
                        // Double-tap → двойной клик
                        client?.doubleClick()
                        vibrate(v)
                        lastTapTime = 0L // сброс, чтобы тройной тап не считался
                    } else {
                        // Одиночный тап → левый клик
                        client?.clickLeft()
                        vibrate(v)
                        lastTapTime = now
                        lastTapX = event.x
                        lastTapY = event.y
                    }
                }
                twoFingerLastDistance = null
                twoFingerLastY = null
                v.parent?.requestDisallowInterceptTouchEvent(false)
            }

            MotionEvent.ACTION_CANCEL -> {
                v.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
