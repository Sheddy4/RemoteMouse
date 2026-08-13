package com.example.remotemouse

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

data class DiscoveredServer(val ip: String, val hostname: String, val port: Int)

/**
 * Результат обнаружения: либо найденный сервер, либо понятное описание ошибки.
 */
sealed class DiscoveryResult {
    data class Found(val server: DiscoveredServer) : DiscoveryResult()
    data class NotFound(val reason: String) : DiscoveryResult()
}

object Discovery {

    private const val TAG = "Discovery"
    private const val UDP_PORT = 5556
    private const val MAGIC = "REMOTE_MOUSE_DISCOVER"
    private const val RESPONSE_PREFIX = "REMOTE_MOUSE_SERVER"

    /**
     * Проверяет, подключён ли телефон к Wi-Fi.
     */
    fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Получает broadcast-адрес текущей подсети (например 192.168.1.255).
     * Работает надёжнее чем 255.255.255.255 на большинстве роутеров.
     */
    private fun getSubnetBroadcast(): InetAddress? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                for (ifAddr in iface.interfaceAddresses) {
                    val broadcast = ifAddr.broadcast
                    if (broadcast != null) {
                        Log.d(TAG, "Найден subnet broadcast: ${broadcast.hostAddress}")
                        return broadcast
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось определить subnet broadcast: ${e.message}")
        }
        return null
    }

    /**
     * Основной метод обнаружения с повторными попытками и множественными адресами.
     *
     * @param context          контекст для проверки Wi-Fi и MulticastLock
     * @param maxAttempts      количество попыток (по умолчанию 3)
     * @param timeoutPerAttempt таймаут одной попытки в мс
     * @param onProgress       колбэк для обновления UI ("Попытка 1/3...")
     */
    fun discover(
        context: Context,
        maxAttempts: Int = 3,
        timeoutPerAttempt: Int = 2500,
        onProgress: ((attempt: Int, total: Int) -> Unit)? = null
    ): DiscoveryResult {

        // --- Проверка Wi-Fi ---
        if (!isOnWifi(context)) {
            return DiscoveryResult.NotFound(
                "Телефон не подключён к Wi-Fi. Подключись к той же сети, что и ПК."
            )
        }

        // --- MulticastLock ---
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = wifiManager.createMulticastLock("RemoteMouseDiscovery")
        multicastLock.setReferenceCounted(true)
        multicastLock.acquire()
        Log.d(TAG, "MulticastLock acquired")

        try {
            // Собираем broadcast-адреса для попыток
            val broadcastAddresses = mutableListOf<InetAddress>()
            getSubnetBroadcast()?.let { broadcastAddresses.add(it) }
            broadcastAddresses.add(InetAddress.getByName("255.255.255.255"))

            for (attempt in 1..maxAttempts) {
                onProgress?.invoke(attempt, maxAttempts)
                Log.d(TAG, "Попытка $attempt/$maxAttempts")

                for (broadcastAddr in broadcastAddresses) {
                    val result = singleAttempt(broadcastAddr, timeoutPerAttempt)
                    if (result != null) {
                        Log.i(TAG, "Сервер найден: ${result.ip}:${result.port} (${result.hostname})")
                        return DiscoveryResult.Found(result)
                    }
                }
            }

            return DiscoveryResult.NotFound(
                "ПК не найден после $maxAttempts попыток.\n\n" +
                "Проверь:\n" +
                "• Серверная часть запущена на ПК?\n" +
                "• Телефон и ПК в одной Wi-Fi сети?\n" +
                "• Фаерволл не блокирует UDP порт $UDP_PORT?\n\n" +
                "Попробуй ввести IP вручную."
            )
        } finally {
            if (multicastLock.isHeld) {
                multicastLock.release()
                Log.d(TAG, "MulticastLock released")
            }
        }
    }

    /**
     * Отправляет один broadcast-пакет и ждёт ответа.
     */
    private fun singleAttempt(broadcastAddr: InetAddress, timeoutMs: Int): DiscoveredServer? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = timeoutMs
                reuseAddress = true
            }

            val message = MAGIC.toByteArray(Charsets.UTF_8)
            val sendPacket = DatagramPacket(message, message.size, broadcastAddr, UDP_PORT)
            socket.send(sendPacket)
            Log.d(TAG, "Broadcast отправлен на ${broadcastAddr.hostAddress}:$UDP_PORT")

            val buf = ByteArray(512)
            val receivePacket = DatagramPacket(buf, buf.size)
            socket.receive(receivePacket)

            val text = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
            Log.d(TAG, "Получен ответ: $text от ${receivePacket.address.hostAddress}")

            // Формат ответа: REMOTE_MOUSE_SERVER:<hostname>:<port>
            val parts = text.split(":")
            if (parts.size >= 3 && parts[0] == RESPONSE_PREFIX) {
                DiscoveredServer(
                    ip = receivePacket.address.hostAddress ?: return null,
                    hostname = parts[1],
                    port = parts[2].toIntOrNull() ?: 5555
                )
            } else {
                Log.w(TAG, "Некорректный формат ответа: $text")
                null
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.d(TAG, "Таймаут на ${broadcastAddr.hostAddress}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка discovery: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            socket?.close()
        }
    }

    /**
     * Быстрая проверка: жив ли сервер по известному IP?
     * Используется для reconnect к последнему запомненному серверу.
     */
    fun ping(ip: String, port: Int = 5555, timeoutMs: Int = 1500): Boolean {
        return try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(ip, port), timeoutMs)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
