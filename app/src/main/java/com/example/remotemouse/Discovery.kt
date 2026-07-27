package com.example.remotemouse

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class DiscoveredServer(val ip: String, val hostname: String, val port: Int)

object Discovery {

    private const val UDP_PORT = 5556

    /**
     * Шлёт широковещательный запрос в локальную сеть и ждёт ответа от сервера.
     * Возвращает null, если никто не ответил за timeoutMs.
     * Вызывать ТОЛЬКО из фонового потока (блокирующий сетевой вызов).
     */
    fun discover(timeoutMs: Int = 3000): DiscoveredServer? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = timeoutMs
            }

            val message = "REMOTE_MOUSE_DISCOVER".toByteArray()
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val sendPacket = DatagramPacket(message, message.size, broadcastAddr, UDP_PORT)
            socket.send(sendPacket)

            val buf = ByteArray(256)
            val receivePacket = DatagramPacket(buf, buf.size)
            socket.receive(receivePacket)

            val text = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
            // Формат ответа: REMOTE_MOUSE_SERVER:<hostname>:<port>
            val parts = text.split(":")
            if (parts.size >= 3 && parts[0] == "REMOTE_MOUSE_SERVER") {
                DiscoveredServer(
                    ip = receivePacket.address.hostAddress ?: return null,
                    hostname = parts[1],
                    port = parts[2].toIntOrNull() ?: 5555
                )
            } else null
        } catch (e: Exception) {
            null
        } finally {
            socket?.close()
        }
    }
}
