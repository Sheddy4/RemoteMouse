package com.example.remotemouse

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Использует гироскоп телефона: наклоняешь телефон — двигается курсор на ПК.
 * Включается/выключается через start()/stop(). Пока запущен, при каждом
 * замере гироскопа шлёт относительное смещение курсора через NetworkClient.
 */
class GyroMouse(
    context: Context,
    private val getClient: () -> NetworkClient?
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var lastTimestamp = 0L
    var sensitivity = 900f // настраивается слайдером в Настройках

    val isAvailable: Boolean get() = gyroscope != null

    fun start() {
        gyroscope?.let {
            lastTimestamp = 0L
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (lastTimestamp == 0L) {
            lastTimestamp = event.timestamp
            return
        }
        val dt = (event.timestamp - lastTimestamp) / 1_000_000_000f // в секундах
        lastTimestamp = event.timestamp
        if (dt <= 0f || dt > 0.5f) return // пропускаем аномальные скачки

        // values[0] = вращение вокруг X (наклон вперёд/назад -> вертикаль)
        // values[1] = вращение вокруг Y (наклон влево/вправо -> горизонталь по крену)
        // values[2] = вращение вокруг Z (поворот телефона -> горизонталь по повороту)
        val pitch = event.values[0]
        val yaw = event.values[2]

        val dx = -yaw * sensitivity * dt
        val dy = -pitch * sensitivity * dt

        if (dx != 0f || dy != 0f) {
            getClient()?.move(dx, dy)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
