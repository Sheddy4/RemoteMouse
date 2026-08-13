package com.example.remotemouse

import android.util.Base64
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class AppEntry(val title: String, val iconBase64: String?)

/**
 * Держит одно TCP-соединение с ПК-сервером.
 *
 * Частые "непрерывные" команды (движение мыши, скролл, зум) НЕ кладутся в
 * обычную очередь по одной штуке - они складываются (суммируются) в
 * аккумуляторы. Отправляющий поток на каждой итерации шлёт СУММУ накопленного
 * одной командой, а не проигрывает каждое мелкое движение по очереди. Это
 * защищает от эффекта "очередь забилась -> курсор дёргается/нагоняет рывками",
 * если сеть или ПК на секунду притормозили - лишние промежуточные движения
 * просто схлопываются в одно, а не копятся.
 *
 * Обычные дискретные команды (клик, клавиша, медиа, питание и т.д.) идут
 * через штатную FIFO-очередь как раньше.
 */
class NetworkClient(private val host: String, private val port: Int) {

    private val queue = LinkedBlockingQueue<String>()
    @Volatile private var running = false
    @Volatile var connected = false
        private set

    // Аккумуляторы для непрерывных команд
    private val moveLock = Object()
    private var moveDx = 0f
    private var moveDy = 0f
    private var moveDirty = false

    private var scrollAccum = 0
    private var scrollDirty = false

    private var zoomAccum = 0
    private var zoomDirty = false

    var onError: ((String) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onClipboard: ((String) -> Unit)? = null
    var onApps: ((List<AppEntry>) -> Unit)? = null

    fun start() {
        if (running) return
        running = true
        thread(isDaemon = true) {
            try {
                val socket = Socket(host, port)
                socket.tcpNoDelay = true // отключаем Nagle's algorithm - иначе
                                          // мелкие пакеты (MOVE) задерживаются
                socket.use { s ->
                    val out: OutputStream = s.getOutputStream()
                    connected = true
                    onConnected?.invoke()

                    // Поток чтения ответов сервера
                    thread(isDaemon = true) {
                        try {
                            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                            while (running) {
                                val line = reader.readLine() ?: break
                                handleIncoming(line)
                            }
                        } catch (_: Exception) {
                        }
                    }

                    fun writeLine(text: String) {
                        out.write((text + "\n").toByteArray())
                        out.flush()
                    }

                    while (running) {
                        // Ждём обычную команду недолго, чтобы параллельно
                        // успевать выгружать накопленные move/scroll/zoom
                        val cmd = queue.poll(4, TimeUnit.MILLISECONDS)
                        if (cmd != null) writeLine(cmd)

                        var moveCmd: String? = null
                        var scrollCmd: String? = null
                        var zoomCmd: String? = null
                        synchronized(moveLock) {
                            if (moveDirty) {
                                moveCmd = "MOVE $moveDx $moveDy"
                                moveDx = 0f; moveDy = 0f
                                moveDirty = false
                            }
                            if (scrollDirty) {
                                scrollCmd = "SCROLL $scrollAccum"
                                scrollAccum = 0
                                scrollDirty = false
                            }
                            if (zoomDirty) {
                                zoomCmd = "ZOOM $zoomAccum"
                                zoomAccum = 0
                                zoomDirty = false
                            }
                        }
                        moveCmd?.let { writeLine(it) }
                        scrollCmd?.let { writeLine(it) }
                        zoomCmd?.let { writeLine(it) }
                    }
                }
            } catch (e: Exception) {
                connected = false
                onError?.invoke(e.message ?: "Ошибка подключения")
            }
        }
    }

    private fun handleIncoming(line: String) {
        when {
            line.startsWith("CLIPBOARDB64:") -> {
                val b64 = line.removePrefix("CLIPBOARDB64:")
                val text = try {
                    String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                } catch (e: Exception) {
                    ""
                }
                onClipboard?.invoke(text)
            }
            line.startsWith("APPSB64:") -> {
                val b64 = line.removePrefix("APPSB64:")
                val json = try {
                    String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                } catch (e: Exception) {
                    ""
                }
                val entries = try {
                    val arr = JSONArray(json)
                    (0 until arr.length()).map { i ->
                        val obj = arr.getJSONObject(i)
                        AppEntry(
                            title = obj.optString("title"),
                            iconBase64 = if (obj.isNull("icon")) null else obj.optString("icon")
                        )
                    }
                } catch (e: Exception) {
                    emptyList()
                }
                onApps?.invoke(entries)
            }
        }
    }

    fun send(command: String) {
        if (running) queue.offer(command)
    }

    fun stop() {
        running = false
        connected = false
    }

    // --- Мышь ---
    // move/scroll/zoom НЕ идут в обычную очередь - они суммируются, см. класс выше
    fun move(dx: Float, dy: Float) {
        synchronized(moveLock) {
            moveDx += dx
            moveDy += dy
            moveDirty = true
        }
    }
    fun scroll(dy: Int) {
        synchronized(moveLock) {
            scrollAccum += dy
            scrollDirty = true
        }
    }
    fun zoom(amount: Int) {
        synchronized(moveLock) {
            zoomAccum += amount
            zoomDirty = true
        }
    }

    fun clickLeft() = send("CLICK LEFT")
    fun clickRight() = send("CLICK RIGHT")
    fun doubleClick() = send("DOUBLECLICK")
    fun mouseDown() = send("DOWN LEFT")
    fun mouseUp() = send("UP LEFT")

    // --- Клавиатура ---
    fun typeText(text: String) = send("TEXT $text")
    fun pressKey(key: String) = send("KEY $key")
    fun hotkey(name: String) = send("HOTKEY $name")

    // --- Медиа ---
    fun media(action: String) = send("MEDIA $action")

    // --- Питание ---
    fun power(action: String) = send("POWER $action")

    // --- Буфер обмена ---
    fun requestClipboard() = send("CLIPBOARD_GET")
    fun sendClipboard(text: String) {
        val b64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        send("CLIPBOARD_SET $b64")
    }

    // --- Приложения ---
    fun requestApps() = send("LISTAPPS")
    fun focusApp(title: String) = send("FOCUSAPP $title")

    // --- Браузер / быстрый запуск ---
    fun openUrl(url: String) {
        val b64 = Base64.encodeToString(url.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        send("OPENURL $b64")
    }
    fun launch(command: String) {
        val b64 = Base64.encodeToString(command.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        send("LAUNCH $b64")
    }
}
