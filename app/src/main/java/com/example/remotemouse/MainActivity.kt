package com.example.remotemouse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private var client: NetworkClient? = null
    private var gyroMouse: GyroMouse? = null
    private var touchpadGestures: TouchpadGestures? = null

    private var leftHanded = false
    private var dragHeld = false
    private var scrollSpeed = 4.0f
    private lateinit var themeDrawables: ThemeDrawables
    private var activeTheme: AppTheme = Themes.CYAN
    private var currentTabIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- Подключение ---
        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
        themeDrawables = ThemeDrawables(resources.displayMetrics.density)

        val etIp = findViewById<EditText>(R.id.etIp)
        val etPort = findViewById<EditText>(R.id.etPort)
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        val btnAutoFind = findViewById<Button>(R.id.btnAutoFind)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val statusDot = findViewById<View>(R.id.statusDot)

        // --- Тачпад ---
        val touchpad = findViewById<FrameLayout>(R.id.touchpad)
        val scrollTrack = findViewById<View>(R.id.scrollTrack)

        // --- Кнопки мыши ---
        val btnLeftClick = findViewById<Button>(R.id.btnLeftClick)
        val btnRightClick = findViewById<Button>(R.id.btnRightClick)
        val btnDrag = findViewById<Button>(R.id.btnDrag)

        // --- Вкладки ---
        val tabMouse = findViewById<TextView>(R.id.tabMouse)
        val tabApps = findViewById<TextView>(R.id.tabApps)
        val tabBrowser = findViewById<TextView>(R.id.tabBrowser)
        val tabKeyboard = findViewById<TextView>(R.id.tabKeyboard)
        val tabMedia = findViewById<TextView>(R.id.tabMedia)
        val tabSystem = findViewById<TextView>(R.id.tabSystem)
        val tabSettings = findViewById<TextView>(R.id.tabSettings)

        val pageMouse = findViewById<LinearLayout>(R.id.pageMouse)
        val pageApps = findViewById<ScrollView>(R.id.pageApps)
        val pageBrowser = findViewById<ScrollView>(R.id.pageBrowser)
        val pageKeyboard = findViewById<ScrollView>(R.id.pageKeyboard)
        val pageMedia = findViewById<ScrollView>(R.id.pageMedia)
        val pageSystem = findViewById<ScrollView>(R.id.pageSystem)
        val pageSettings = findViewById<ScrollView>(R.id.pageSettings)

        val tabs = listOf(tabMouse, tabApps, tabBrowser, tabKeyboard, tabMedia, tabSystem, tabSettings)
        val pages: List<View> = listOf(pageMouse, pageApps, pageBrowser, pageKeyboard, pageMedia, pageSystem, pageSettings)

        fun selectTab(index: Int) {
            currentTabIndex = index
            tabs.forEachIndexed { i, tab ->
                val color = if (i == index) activeTheme.accent else activeTheme.textSecondary
                tab.background = if (i == index) themeDrawables.tabSelected(activeTheme) else themeDrawables.tabUnselected(activeTheme)
                tab.setTextColor(color)
                tab.compoundDrawables.getOrNull(1)?.mutate()?.setTint(color)
            }
            pages.forEachIndexed { i, page -> page.visibility = if (i == index) View.VISIBLE else View.GONE }
        }
        tabMouse.setOnClickListener { selectTab(0) }
        tabApps.setOnClickListener { selectTab(1) }
        tabBrowser.setOnClickListener { selectTab(2) }
        tabKeyboard.setOnClickListener { selectTab(3) }
        tabMedia.setOnClickListener { selectTab(4) }
        tabSystem.setOnClickListener { selectTab(5) }
        tabSettings.setOnClickListener { selectTab(6) }
        selectTab(0)

        // --- Приложения ---
        val btnRefreshApps = findViewById<Button>(R.id.btnRefreshApps)
        val appsListContainer = findViewById<LinearLayout>(R.id.appsListContainer)
        val launcherListContainer = findViewById<LinearLayout>(R.id.launcherListContainer)
        val btnAddLauncher = findViewById<Button>(R.id.btnAddLauncher)

        // --- Браузер ---
        val btnBrowserBack = findViewById<Button>(R.id.btnBrowserBack)
        val btnBrowserForward = findViewById<Button>(R.id.btnBrowserForward)
        val btnBrowserRefresh = findViewById<Button>(R.id.btnBrowserRefresh)
        val btnBrowserHome = findViewById<Button>(R.id.btnBrowserHome)
        val btnBrowserNewTab = findViewById<Button>(R.id.btnBrowserNewTab)
        val btnBrowserCloseTab = findViewById<Button>(R.id.btnBrowserCloseTab)
        val btnBrowserZoomOut = findViewById<Button>(R.id.btnBrowserZoomOut)
        val btnBrowserZoomReset = findViewById<Button>(R.id.btnBrowserZoomReset)
        val btnBrowserZoomIn = findViewById<Button>(R.id.btnBrowserZoomIn)
        val bookmarksListContainer = findViewById<LinearLayout>(R.id.bookmarksListContainer)
        val btnAddBookmark = findViewById<Button>(R.id.btnAddBookmark)

        // --- Клавиатура ---
        val etKeyboard = findViewById<EditText>(R.id.etKeyboard)
        val btnKeyEnter = findViewById<Button>(R.id.btnKeyEnter)
        val btnKeyBackspace = findViewById<Button>(R.id.btnKeyBackspace)
        val btnKeySpace = findViewById<Button>(R.id.btnKeySpace)
        val btnClearKeyboard = findViewById<Button>(R.id.btnClearKeyboard)

        val btnHkCopy = findViewById<Button>(R.id.btnHkCopy)
        val btnHkPaste = findViewById<Button>(R.id.btnHkPaste)
        val btnHkCut = findViewById<Button>(R.id.btnHkCut)
        val btnHkUndo = findViewById<Button>(R.id.btnHkUndo)
        val btnHkRedo = findViewById<Button>(R.id.btnHkRedo)
        val btnHkSave = findViewById<Button>(R.id.btnHkSave)
        val btnHkAltTab = findViewById<Button>(R.id.btnHkAltTab)
        val btnHkWinD = findViewById<Button>(R.id.btnHkWinD)
        val btnHkAltF4 = findViewById<Button>(R.id.btnHkAltF4)

        // --- Медиа ---
        val btnMediaPrev = findViewById<Button>(R.id.btnMediaPrev)
        val btnMediaPlayPause = findViewById<Button>(R.id.btnMediaPlayPause)
        val btnMediaNext = findViewById<Button>(R.id.btnMediaNext)
        val btnMediaStop = findViewById<Button>(R.id.btnMediaStop)
        val btnVolDown = findViewById<Button>(R.id.btnVolDown)
        val btnMute = findViewById<Button>(R.id.btnMute)
        val btnVolUp = findViewById<Button>(R.id.btnVolUp)

        // --- Система: буфер обмена + питание ---
        val btnClipboardGet = findViewById<Button>(R.id.btnClipboardGet)
        val btnClipboardSend = findViewById<Button>(R.id.btnClipboardSend)
        val btnSleep = findViewById<Button>(R.id.btnSleep)
        val btnRestart = findViewById<Button>(R.id.btnRestart)
        val btnShutdown = findViewById<Button>(R.id.btnShutdown)

        // --- Настройки ---
        val tvSensitivityValue = findViewById<TextView>(R.id.tvSensitivityValue)
        val seekSensitivity = findViewById<SeekBar>(R.id.seekSensitivity)
        val tvScrollSpeedValue = findViewById<TextView>(R.id.tvScrollSpeedValue)
        val seekScrollSpeed = findViewById<SeekBar>(R.id.seekScrollSpeed)
        val tvGyroSensitivityValue = findViewById<TextView>(R.id.tvGyroSensitivityValue)
        val seekGyroSensitivity = findViewById<SeekBar>(R.id.seekGyroSensitivity)
        val switchLeftHanded = findViewById<Switch>(R.id.switchLeftHanded)
        val switchGyro = findViewById<Switch>(R.id.switchGyro)
        val themeSwatchesContainer = findViewById<LinearLayout>(R.id.themeSwatchesContainer)
        val etCustomColor = findViewById<EditText>(R.id.etCustomColor)
        val btnApplyCustomColor = findViewById<Button>(R.id.btnApplyCustomColor)

        scrollSpeed = 1.0f + seekScrollSpeed.progress * 0.1f
        touchpadGestures = TouchpadGestures(
            { client },
            sensitivity = 1.0f + seekSensitivity.progress * 0.1f,
            scrollSensitivity = scrollSpeed,
            leftHanded = leftHanded
        )
        gyroMouse = GyroMouse(this) { client }
        gyroMouse?.sensitivity = 100f + seekGyroSensitivity.progress * 10f

        fun setStatusColor(colorRes: Int) {
            (statusDot.background as? GradientDrawable)?.setColor(getColor(colorRes))
        }

        fun connectTo(ip: String, port: Int) {
            client?.stop()
            val newClient = NetworkClient(ip, port)
            newClient.onConnected = {
                runOnUiThread {
                    tvStatus.text = "Подключено: $ip:$port"
                    setStatusColor(R.color.status_online)
                }
            }
            newClient.onError = { msg ->
                runOnUiThread {
                    tvStatus.text = "Ошибка: $msg"
                    setStatusColor(R.color.status_offline)
                }
            }
            newClient.onClipboard = { text ->
                runOnUiThread {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("remote_mouse", text))
                    Toast.makeText(this, "Скопировано с ПК в буфер телефона", Toast.LENGTH_SHORT).show()
                }
            }
            newClient.onApps = { entries ->
                runOnUiThread {
                    appsListContainer.removeAllViews()
                    if (entries.isEmpty()) {
                        val empty = TextView(this).apply {
                            text = "Не удалось получить список окон"
                            setTextColor(activeTheme.textSecondary)
                            setPadding(8, 16, 8, 16)
                        }
                        appsListContainer.addView(empty)
                    } else {
                        val iconSizePx = (28 * resources.displayMetrics.density).toInt()
                        for (entry in entries) {
                            val icon = android.widget.ImageView(this).apply {
                                layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx).apply {
                                    marginEnd = (12 * resources.displayMetrics.density).toInt()
                                }
                                val bmp = entry.iconBase64?.let { b64 ->
                                    try {
                                        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                if (bmp != null) {
                                    setImageBitmap(bmp)
                                } else {
                                    setImageResource(android.R.drawable.ic_menu_recent_history)
                                }
                            }
                            val label = TextView(this).apply {
                                text = entry.title
                                setTextColor(activeTheme.textPrimary)
                                textSize = 14f
                                layoutParams = LinearLayout.LayoutParams(
                                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                                )
                            }
                            val row = LinearLayout(this).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                setPadding(16, 20, 16, 20)
                                background = themeDrawables.outlineButton(activeTheme)
                                addView(icon)
                                addView(label)
                                setOnClickListener { newClient.focusApp(entry.title) }
                            }
                            val rowParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            rowParams.topMargin = 8
                            appsListContainer.addView(row, rowParams)
                        }
                    }
                }
            }
            newClient.start()
            client = newClient
            tvStatus.text = "Подключение..."
        }

        btnAutoFind.setOnClickListener {
            tvStatus.text = "Поиск ПК в сети..."
            thread {
                val found = Discovery.discover(3000)
                runOnUiThread {
                    if (found != null) {
                        etIp.setText(found.ip)
                        etPort.setText(found.port.toString())
                        connectTo(found.ip, found.port)
                    } else {
                        tvStatus.text = "Не найден. Проверь сервер и Wi-Fi, либо введи IP вручную"
                        setStatusColor(R.color.status_offline)
                    }
                }
            }
        }

        btnConnect.setOnClickListener {
            val ip = etIp.text.toString().trim()
            val port = etPort.text.toString().trim().toIntOrNull() ?: 5555
            if (ip.isEmpty()) {
                Toast.makeText(this, "Введи IP или нажми «Найти ПК»", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            connectTo(ip, port)
        }

        // Автоподключение при старте приложения
        thread {
            val found = Discovery.discover(2500)
            if (found != null) {
                runOnUiThread {
                    etIp.setText(found.ip)
                    etPort.setText(found.port.toString())
                }
                connectTo(found.ip, found.port)
            }
        }

        // --- Тачпад ---
        touchpad.setOnTouchListener(touchpadGestures)

        // --- Полоса скролла: один жест протяжки по всей полосе ---
        var scrollLastY = 0f
        scrollTrack.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    scrollLastY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = event.y - scrollLastY
                    if (kotlin.math.abs(delta) > 4f) {
                        client?.scroll((-delta / 6f * scrollSpeed).toInt())
                        scrollLastY = event.y
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            true
        }

        // --- Кнопки мыши (с учётом режима для левшей) ---
        btnLeftClick.setOnClickListener { client?.clickLeft() }
        btnRightClick.setOnClickListener { client?.clickRight() }
        btnDrag.setOnClickListener {
            dragHeld = !dragHeld
            if (dragHeld) {
                client?.mouseDown()
                btnDrag.text = "Отпустить"
                btnDrag.background = themeDrawables.primaryButton(activeTheme)
                btnDrag.setTextColor(activeTheme.bgDeep)
            } else {
                client?.mouseUp()
                btnDrag.text = "Зажать"
                btnDrag.background = themeDrawables.outlineButton(activeTheme)
                btnDrag.setTextColor(activeTheme.textPrimary)
            }
        }

        // --- Приложения ---
        btnRefreshApps.setOnClickListener {
            if (client == null) {
                Toast.makeText(this, "Сначала подключись к ПК", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Загружаю список окон...", Toast.LENGTH_SHORT).show()
                client?.requestApps()
            }
        }

        // --- Клавиатура: живой ввод, каждый символ сразу летит на ПК ---
        etKeyboard.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                client?.pressKey("enter")
                true
            } else false
        }
        etKeyboard.addTextChangedListener(object : TextWatcher {
            private var previous = ""
            override fun afterTextChanged(s: Editable?) {
                val current = s?.toString() ?: ""
                if (current == previous) return
                when {
                    current.startsWith(previous) -> {
                        val added = current.substring(previous.length)
                        if (added.isNotEmpty()) client?.typeText(added)
                    }
                    previous.startsWith(current) -> {
                        val deleted = previous.length - current.length
                        repeat(deleted) { client?.pressKey("backspace") }
                    }
                    else -> {
                        // Сложное изменение (автозамена и т.п.) - на всякий случай
                        // просто досылаем разницу как есть
                        client?.typeText(current)
                    }
                }
                previous = current
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        btnKeyEnter.setOnClickListener { client?.pressKey("enter") }
        btnKeyBackspace.setOnClickListener { client?.pressKey("backspace") }
        btnKeySpace.setOnClickListener { client?.pressKey("space") }
        btnClearKeyboard.setOnClickListener { etKeyboard.setText("") }

        btnHkCopy.setOnClickListener { client?.hotkey("copy") }
        btnHkPaste.setOnClickListener { client?.hotkey("paste") }
        btnHkCut.setOnClickListener { client?.hotkey("cut") }
        btnHkUndo.setOnClickListener { client?.hotkey("undo") }
        btnHkRedo.setOnClickListener { client?.hotkey("redo") }
        btnHkSave.setOnClickListener { client?.hotkey("save") }
        btnHkAltTab.setOnClickListener { client?.hotkey("alttab") }
        btnHkWinD.setOnClickListener { client?.hotkey("showdesktop") }
        btnHkAltF4.setOnClickListener { client?.hotkey("altf4") }

        // --- Медиа ---
        btnMediaPrev.setOnClickListener { client?.media("PREV") }
        btnMediaPlayPause.setOnClickListener { client?.media("PLAYPAUSE") }
        btnMediaNext.setOnClickListener { client?.media("NEXT") }
        btnMediaStop.setOnClickListener { client?.media("STOP") }
        btnVolDown.setOnClickListener { client?.media("VOLDOWN") }
        btnMute.setOnClickListener { client?.media("MUTE") }
        btnVolUp.setOnClickListener { client?.media("VOLUP") }

        // --- Буфер обмена ---
        btnClipboardGet.setOnClickListener {
            client?.requestClipboard()
                ?: Toast.makeText(this, "Сначала подключись к ПК", Toast.LENGTH_SHORT).show()
        }
        btnClipboardSend.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (text.isNullOrEmpty()) {
                Toast.makeText(this, "В буфере телефона пусто", Toast.LENGTH_SHORT).show()
            } else {
                client?.sendClipboard(text)
                Toast.makeText(this, "Отправлено на ПК", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Пульт браузера ---
        btnBrowserBack.setOnClickListener { client?.hotkey("browserback") }
        btnBrowserForward.setOnClickListener { client?.hotkey("browserforward") }
        btnBrowserRefresh.setOnClickListener { client?.hotkey("browserrefresh") }
        btnBrowserHome.setOnClickListener { client?.hotkey("browserhome") }
        btnBrowserNewTab.setOnClickListener { client?.hotkey("browsernewtab") }
        btnBrowserCloseTab.setOnClickListener { client?.hotkey("browserclosetab") }
        btnBrowserZoomOut.setOnClickListener { client?.hotkey("browserzoomout") }
        btnBrowserZoomReset.setOnClickListener { client?.hotkey("browserzoomreset") }
        btnBrowserZoomIn.setOnClickListener { client?.hotkey("browserzoomin") }

        // --- Хранилище пар "имя -> значение" на телефоне (закладки, быстрый запуск) ---
        fun savePairs(key: String, list: List<Pair<String, String>>) {
            val arr = org.json.JSONArray()
            for ((name, value) in list) {
                val obj = org.json.JSONObject()
                obj.put("name", name)
                obj.put("value", value)
                arr.put(obj)
            }
            getSharedPreferences("remote_mouse_prefs", MODE_PRIVATE).edit()
                .putString(key, arr.toString()).apply()
        }
        fun loadPairs(key: String, defaults: List<Pair<String, String>> = emptyList()): MutableList<Pair<String, String>> {
            val prefs = getSharedPreferences("remote_mouse_prefs", MODE_PRIVATE)
            if (!prefs.contains(key)) {
                // Первый запуск - заполняем список популярными значениями по умолчанию.
                // Если потом пользователь их удалит - обратно они не появятся,
                // так как prefs.contains(key) станет true после первого сохранения.
                savePairs(key, defaults)
                return defaults.toMutableList()
            }
            val json = prefs.getString(key, "[]") ?: "[]"
            val list = mutableListOf<Pair<String, String>>()
            try {
                val arr = org.json.JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(obj.getString("name") to obj.getString("value"))
                }
            } catch (e: Exception) { /* пусто - начнём с чистого списка */ }
            return list
        }

        val defaultLauncherApps = listOf(
            "Блокнот" to "notepad.exe",
            "Калькулятор" to "calc.exe",
            "Проводник" to "explorer.exe",
            "Диспетчер задач" to "taskmgr.exe",
            "Paint" to "mspaint.exe",
            "Командная строка" to "cmd.exe"
        )
        val defaultBookmarks = listOf(
            "Google" to "google.com",
            "YouTube" to "youtube.com",
            "VK" to "vk.com",
            "Discord" to "discord.com",
            "Telegram Web" to "web.telegram.org",
            "Wikipedia" to "wikipedia.org"
        )

        fun rebuildPairsUI(container: LinearLayout, key: String, defaults: List<Pair<String, String>>, onOpen: (String) -> Unit) {
            container.removeAllViews()
            val list = loadPairs(key, defaults)
            for ((index, pair) in list.withIndex()) {
                val (name, value) = pair
                val label = TextView(this).apply {
                    text = if (name.isNotBlank()) name else value
                    setTextColor(activeTheme.textPrimary)
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val delete = TextView(this).apply {
                    text = "✕"
                    setTextColor(getColor(R.color.danger))
                    textSize = 16f
                    setPadding(24, 0, 8, 0)
                    setOnClickListener {
                        val updated = loadPairs(key, defaults).toMutableList()
                        updated.removeAt(index)
                        savePairs(key, updated)
                        rebuildPairsUI(container, key, defaults, onOpen)
                    }
                }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(16, 20, 16, 20)
                    background = themeDrawables.outlineButton(activeTheme)
                    addView(label)
                    addView(delete)
                    setOnClickListener { onOpen(value) }
                }
                val rowParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                rowParams.topMargin = 6
                container.addView(row, rowParams)
            }
        }

        fun showAddDialog(dialogTitle: String, hintName: String, hintValue: String, key: String, defaults: List<Pair<String, String>>, container: LinearLayout, onOpen: (String) -> Unit) {
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 24, 48, 0)
            }
            val nameInput = EditText(this).apply { hint = hintName }
            val valueInput = EditText(this).apply { hint = hintValue }
            layout.addView(nameInput)
            layout.addView(valueInput)

            AlertDialog.Builder(this)
                .setTitle(dialogTitle)
                .setView(layout)
                .setPositiveButton("Добавить") { _, _ ->
                    val name = nameInput.text.toString().trim()
                    val value = valueInput.text.toString().trim()
                    if (value.isNotEmpty()) {
                        val list = loadPairs(key, defaults)
                        list.add(name to value)
                        savePairs(key, list)
                        rebuildPairsUI(container, key, defaults, onOpen)
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        rebuildPairsUI(launcherListContainer, "launcher_items", defaultLauncherApps) { command -> client?.launch(command) }
        btnAddLauncher.setOnClickListener {
            showAddDialog("Добавить программу", "Название", "Путь к программе (например notepad.exe)", "launcher_items", defaultLauncherApps, launcherListContainer) { command ->
                client?.launch(command)
            }
        }

        rebuildPairsUI(bookmarksListContainer, "bookmarks", defaultBookmarks) { url -> client?.openUrl(url) }
        btnAddBookmark.setOnClickListener {
            showAddDialog("Добавить закладку", "Название", "URL (например google.com)", "bookmarks", defaultBookmarks, bookmarksListContainer) { url ->
                client?.openUrl(url)
            }
        }

        // --- Темы оформления ---
        fun saveThemeChoice(themeId: String, customHex: String?) {
            val prefs = getSharedPreferences("remote_mouse_prefs", MODE_PRIVATE)
            val editor = prefs.edit().putString("theme_id", themeId)
            if (customHex != null) editor.putString("theme_custom_hex", customHex)
            editor.apply()
        }
        fun loadThemeChoice(): AppTheme {
            val prefs = getSharedPreferences("remote_mouse_prefs", MODE_PRIVATE)
            val id = prefs.getString("theme_id", "cyan") ?: "cyan"
            if (id == "custom") {
                val hex = prefs.getString("theme_custom_hex", null)
                if (hex != null) {
                    return try {
                        Themes.buildCustom(android.graphics.Color.parseColor(hex))
                    } catch (e: Exception) {
                        Themes.CYAN
                    }
                }
            }
            return Themes.presets.find { it.id == id } ?: Themes.CYAN
        }

        fun applyTheme(theme: AppTheme) {
            activeTheme = theme
            rootLayout.setBackgroundColor(theme.bgDeep)

            listOf(statusPanel, leftHandedPanel, gyroPanel).forEach {
                it.background = themeDrawables.panel(theme)
            }

            val outlineAccentButtons = listOf(btnConnect, btnAddLauncher, btnRefreshApps, btnAddBookmark, btnApplyCustomColor)
            outlineAccentButtons.forEach {
                it.background = themeDrawables.outlineButton(theme)
                it.setTextColor(theme.accent)
            }

            val outlineNeutralButtons = listOf(
                btnLeftClick, btnRightClick, btnKeyEnter, btnKeyBackspace, btnKeySpace, btnClearKeyboard,
                btnHkCopy, btnHkPaste, btnHkCut, btnHkUndo, btnHkRedo, btnHkSave, btnHkAltTab, btnHkWinD, btnHkAltF4,
                btnBrowserBack, btnBrowserForward, btnBrowserRefresh, btnBrowserHome, btnBrowserNewTab, btnBrowserCloseTab,
                btnBrowserZoomOut, btnBrowserZoomReset, btnBrowserZoomIn, btnSleep, btnRestart,
                btnClipboardGet, btnClipboardSend
            )
            outlineNeutralButtons.forEach {
                it.background = themeDrawables.outlineButton(theme)
                it.setTextColor(theme.textPrimary)
            }

            // Кнопка "Зажать" зависит от текущего состояния (зажата/нет)
            btnDrag.background = if (dragHeld) themeDrawables.primaryButton(theme) else themeDrawables.outlineButton(theme)
            btnDrag.setTextColor(if (dragHeld) theme.bgDeep else theme.textPrimary)

            listOf(btnAutoFind, btnMediaPlayPause).forEach {
                it.background = themeDrawables.primaryButton(theme)
                it.setTextColor(theme.bgDeep)
            }

            listOf(btnVolUp, btnVolDown).forEach {
                it.background = themeDrawables.circleButton(theme)
                it.setTextColor(theme.accent)
            }
            listOf(btnMediaPrev, btnMediaNext, btnMute, btnMediaStop).forEach {
                it.background = themeDrawables.circleButton(theme)
                it.setTextColor(theme.textPrimary)
            }

            touchpad.background = themeDrawables.touchpad(theme)
            scrollTrack.background = themeDrawables.scrollTrack(theme)
            mediaCircleTrack.background = themeDrawables.circleTrack(theme)

            listOf(etIp, etPort, etKeyboard, etCustomColor).forEach {
                it.background = themeDrawables.editText(theme)
                it.setTextColor(theme.textPrimary)
                it.setHintTextColor(theme.textSecondary)
            }

            selectTab(currentTabIndex)
        }

        fun rebuildSwatches() {
            themeSwatchesContainer.removeAllViews()
            val size = (44 * resources.displayMetrics.density).toInt()
            val margin = (10 * resources.displayMetrics.density).toInt()
            for (preset in Themes.presets) {
                val swatch = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = margin }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(preset.accent)
                        if (preset.id == activeTheme.id) {
                            setStroke((3 * resources.displayMetrics.density).toInt(), android.graphics.Color.WHITE)
                        }
                    }
                    setOnClickListener {
                        saveThemeChoice(preset.id, null)
                        applyTheme(preset)
                        rebuildSwatches()
                    }
                }
                themeSwatchesContainer.addView(swatch)
            }
        }

        btnApplyCustomColor.setOnClickListener {
            val raw = etCustomColor.text.toString().trim()
            if (raw.isEmpty()) {
                Toast.makeText(this, "Введи цвет в формате #RRGGBB", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val hex = if (raw.startsWith("#")) raw else "#$raw"
            val colorInt = try {
                android.graphics.Color.parseColor(hex)
            } catch (e: Exception) {
                Toast.makeText(this, "Некорректный цвет, формат #RRGGBB", Toast.LENGTH_SHORT).show()
                null
            }
            if (colorInt != null) {
                val theme = Themes.buildCustom(colorInt)
                saveThemeChoice("custom", hex)
                applyTheme(theme)
                rebuildSwatches()
            }
        }

        applyTheme(loadThemeChoice())
        rebuildSwatches()

        // --- Питание ---
        fun confirmPower(title: String, action: String) {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("Точно выполнить это действие на ПК?")
                .setPositiveButton("Да") { _, _ -> client?.power(action) }
                .setNegativeButton("Отмена", null)
                .show()
        }
        btnSleep.setOnClickListener { confirmPower("Сон", "SLEEP") }
        btnRestart.setOnClickListener { confirmPower("Перезагрузка", "RESTART") }
        btnShutdown.setOnClickListener { confirmPower("Выключение", "SHUTDOWN") }

        // --- Настройки ---
        tvSensitivityValue.text = "x%.1f".format(1.0f + seekSensitivity.progress * 0.1f)
        seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 1.0f + progress * 0.1f
                tvSensitivityValue.text = "x%.1f".format(value)
                touchpadGestures?.sensitivity = value
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        tvScrollSpeedValue.text = "x%.1f".format(scrollSpeed)
        seekScrollSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 1.0f + progress * 0.1f
                scrollSpeed = value
                tvScrollSpeedValue.text = "x%.1f".format(value)
                touchpadGestures?.scrollSensitivity = value
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        tvGyroSensitivityValue.text = "${(100f + seekGyroSensitivity.progress * 10f).toInt()}"
        seekGyroSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 100f + progress * 10f
                tvGyroSensitivityValue.text = "${value.toInt()}"
                gyroMouse?.sensitivity = value
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        switchLeftHanded.setOnCheckedChangeListener { _, isChecked ->
            leftHanded = isChecked
            touchpadGestures?.leftHanded = isChecked
        }

        switchGyro.setOnCheckedChangeListener { _, isChecked ->
            val gyro = gyroMouse
            if (gyro == null || !gyro.isAvailable) {
                Toast.makeText(this, "На этом телефоне нет гироскопа", Toast.LENGTH_SHORT).show()
                switchGyro.isChecked = false
                return@setOnCheckedChangeListener
            }
            if (isChecked) {
                gyro.start()
                Toast.makeText(this, "Gyro-mouse включен — наклоняй телефон", Toast.LENGTH_SHORT).show()
            } else {
                gyro.stop()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gyroMouse?.stop()
        client?.stop()
    }
}
