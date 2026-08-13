#!/usr/bin/env python3
"""
RemoteMouse Server v2.0
Запускай на ПК, подключайся с телефона.

Зависимости (установить один раз):
    pip install pyautogui pillow pywin32

Запуск:
    python server.py
"""

import socket
import threading
import struct
import subprocess
import base64
import json
import os
import sys
import time
import logging

# ─── Настройка ───────────────────────────────────────────────────────────────
TCP_PORT  = 5555   # порт для команд (TCP)
UDP_PORT  = 5556   # порт для обнаружения (UDP broadcast)
HOSTNAME  = socket.gethostname()
LOG_LEVEL = logging.INFO
# ─────────────────────────────────────────────────────────────────────────────

logging.basicConfig(
    level=LOG_LEVEL,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S"
)
log = logging.getLogger("RemoteMouse")

# Импортируем нужные библиотеки
try:
    import pyautogui
    pyautogui.FAILSAFE = False
    pyautogui.PAUSE = 0
except ImportError:
    log.error("Установи pyautogui: pip install pyautogui")
    sys.exit(1)

try:
    import win32clipboard
    import win32con
    import win32gui
    import win32process
    import win32api
    import ctypes
    WINDOWS = True
except ImportError:
    WINDOWS = False
    log.warning("pywin32 не установлен — буфер обмена и список окон недоступны")
    log.warning("Установи: pip install pywin32")


# ─── UDP Discovery ────────────────────────────────────────────────────────────

def discovery_server():
    """Отвечает на broadcast-запросы с телефона."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("", UDP_PORT))
    log.info(f"Discovery слушает UDP порт {UDP_PORT}")
    while True:
        try:
            data, addr = sock.recvfrom(512)
            msg = data.decode("utf-8", errors="ignore").strip()
            if msg == "REMOTE_MOUSE_DISCOVER":
                response = f"REMOTE_MOUSE_SERVER:{HOSTNAME}:{TCP_PORT}"
                sock.sendto(response.encode("utf-8"), addr)
                log.debug(f"Discovery ответил → {addr[0]}")
        except Exception as e:
            log.warning(f"Discovery ошибка: {e}")


# ─── Команды ─────────────────────────────────────────────────────────────────

HOTKEYS = {
    "copy":           ("ctrl", "c"),
    "paste":          ("ctrl", "v"),
    "cut":            ("ctrl", "x"),
    "undo":           ("ctrl", "z"),
    "redo":           ("ctrl", "y"),
    "save":           ("ctrl", "s"),
    "alttab":         ("alt",  "tab"),
    "showdesktop":    ("win",  "d"),
    "altf4":          ("alt",  "f4"),
    "browserback":    ("alt",  "left"),
    "browserforward": ("alt",  "right"),
    "browserrefresh": ("ctrl", "r"),
    "browserhome":    ("alt",  "home"),
    "browsernewtab":  ("ctrl", "t"),
    "browserclosetab":("ctrl", "w"),
    "browserzoomout": ("ctrl", "-"),
    "browserzoomreset":("ctrl","0"),
    "browserzoomin":  ("ctrl", "="),
}

MEDIA_KEYS = {
    "PLAYPAUSE": "playpause",
    "PREV":      "prevtrack",
    "NEXT":      "nexttrack",
    "STOP":      "stop",
    "VOLUP":     "volumeup",
    "VOLDOWN":   "volumedown",
    "MUTE":      "volumemute",
}


def get_clipboard() -> str:
    if not WINDOWS:
        return ""
    try:
        win32clipboard.OpenClipboard()
        data = win32clipboard.GetClipboardData(win32con.CF_UNICODETEXT)
        win32clipboard.CloseClipboard()
        return data
    except Exception:
        try:
            win32clipboard.CloseClipboard()
        except Exception:
            pass
        return ""


def set_clipboard(text: str):
    if not WINDOWS:
        return
    try:
        win32clipboard.OpenClipboard()
        win32clipboard.EmptyClipboard()
        win32clipboard.SetClipboardData(win32con.CF_UNICODETEXT, text)
        win32clipboard.CloseClipboard()
    except Exception:
        try:
            win32clipboard.CloseClipboard()
        except Exception:
            pass


def get_open_windows() -> list[dict]:
    """Возвращает список открытых окон с иконками."""
    if not WINDOWS:
        return []
    results = []

    def enum_handler(hwnd, _):
        if not win32gui.IsWindowVisible(hwnd):
            return
        title = win32gui.GetWindowText(hwnd).strip()
        if not title or title in ("-", "Default IME", "MSCTFIME UI"):
            return
        # Иконка окна
        icon_b64 = None
        try:
            _, pid = win32process.GetWindowThreadProcessId(hwnd)
            hproc = win32api.OpenProcess(0x0400 | 0x0010, False, pid)
            exe = win32process.GetModuleFileNameEx(hproc, 0)
            win32api.CloseHandle(hproc)
            if exe and os.path.exists(exe):
                from PIL import Image
                import win32ui
                ico_x = win32api.GetSystemMetrics(win32con.SM_CXSMICON)
                ico_y = win32api.GetSystemMetrics(win32con.SM_CYSMICON)
                hdc = win32ui.CreateDCFromHandle(win32gui.GetDC(0))
                hbmp = win32ui.CreateBitmap()
                hbmp.CreateCompatibleBitmap(hdc, ico_x, ico_y)
                hdc2 = hdc.CreateCompatibleDC()
                hdc2.SelectObject(hbmp)
                hico = win32gui.ExtractIconEx(exe, 0, 1)[0][0] if win32gui.ExtractIconEx(exe, 0, 1) else None
                if hico:
                    win32gui.DrawIconEx(hdc2.GetHandleOutput(), 0, 0, hico, ico_x, ico_y, 0, None, win32con.DI_NORMAL)
                    bmpinfo = hbmp.GetInfo()
                    bmpstr  = hbmp.GetBitmapBits(True)
                    img = Image.frombuffer("RGBA", (bmpinfo["bmWidth"], bmpinfo["bmHeight"]), bmpstr, "raw", "BGRA", 0, 1)
                    import io
                    buf = io.BytesIO()
                    img.save(buf, format="PNG")
                    icon_b64 = base64.b64encode(buf.getvalue()).decode()
                    win32gui.DestroyIcon(hico)
                hdc2.DeleteDC()
                hdc.DeleteDC()
        except Exception:
            pass

        results.append({"title": title, "icon": icon_b64})

    try:
        win32gui.EnumWindows(enum_handler, None)
    except Exception as e:
        log.warning(f"EnumWindows: {e}")
    return results


def focus_window(title_fragment: str):
    """Выводит окно с заданным заголовком на передний план."""
    if not WINDOWS:
        return
    def enum_handler(hwnd, _):
        if title_fragment.lower() in win32gui.GetWindowText(hwnd).lower():
            win32gui.ShowWindow(hwnd, 9)  # SW_RESTORE
            win32gui.SetForegroundWindow(hwnd)
    try:
        win32gui.EnumWindows(enum_handler, None)
    except Exception:
        pass


# ─── Обработчик клиента ───────────────────────────────────────────────────────

class ClientHandler(threading.Thread):
    def __init__(self, conn: socket.socket, addr):
        super().__init__(daemon=True)
        self.conn = conn
        self.addr = addr
        self.send_lock = threading.Lock()

    def send_line(self, text: str):
        try:
            with self.send_lock:
                self.conn.sendall((text + "\n").encode("utf-8"))
        except Exception:
            pass

    def run(self):
        log.info(f"Подключено: {self.addr[0]}:{self.addr[1]}")
        self.conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        buf = ""
        try:
            while True:
                chunk = self.conn.recv(4096)
                if not chunk:
                    break
                buf += chunk.decode("utf-8", errors="ignore")
                while "\n" in buf:
                    line, buf = buf.split("\n", 1)
                    line = line.strip()
                    if line:
                        self.handle(line)
        except Exception as e:
            log.debug(f"Клиент {self.addr[0]} отключился: {e}")
        finally:
            self.conn.close()
            log.info(f"Отключено: {self.addr[0]}")

    def handle(self, cmd: str):
        log.debug(f"← {cmd}")
        parts = cmd.split(" ", 2)
        action = parts[0].upper()

        try:
            # ── Мышь ──────────────────────────────────────────────────────────
            if action == "MOVE":
                dx, dy = float(parts[1]), float(parts[2])
                pyautogui.moveRel(dx, dy, _pause=False)

            elif action == "SCROLL":
                amount = int(parts[1])
                pyautogui.scroll(amount, _pause=False)

            elif action == "ZOOM":
                amount = int(parts[1])
                pyautogui.hotkey("ctrl")
                pyautogui.scroll(amount, _pause=False)

            elif action == "CLICK":
                btn = parts[1].lower() if len(parts) > 1 else "left"
                pyautogui.click(button=btn, _pause=False)

            elif action == "DOUBLECLICK":
                pyautogui.doubleClick(_pause=False)

            elif action == "DOWN":
                btn = parts[1].lower() if len(parts) > 1 else "left"
                pyautogui.mouseDown(button=btn, _pause=False)

            elif action == "UP":
                btn = parts[1].lower() if len(parts) > 1 else "left"
                pyautogui.mouseUp(button=btn, _pause=False)

            # ── Клавиатура ────────────────────────────────────────────────────
            elif action == "TEXT":
                text = cmd[5:]  # всё после "TEXT "
                pyautogui.typewrite(text, interval=0.01, _pause=False)

            elif action == "KEY":
                key = parts[1].lower()
                key_map = {
                    "enter": "enter", "backspace": "backspace",
                    "space": "space", "tab": "tab", "escape": "escape",
                    "delete": "delete", "home": "home", "end": "end",
                    "pageup": "pageup", "pagedown": "pagedown",
                    "up": "up", "down": "down", "left": "left", "right": "right",
                }
                pyautogui.press(key_map.get(key, key), _pause=False)

            elif action == "HOTKEY":
                name = parts[1].lower()
                combo = HOTKEYS.get(name)
                if combo:
                    pyautogui.hotkey(*combo, _pause=False)
                else:
                    log.warning(f"Неизвестный hotkey: {name}")

            # ── Медиа ─────────────────────────────────────────────────────────
            elif action == "MEDIA":
                media_action = parts[1].upper()
                key = MEDIA_KEYS.get(media_action)
                if key:
                    pyautogui.press(key, _pause=False)
                else:
                    log.warning(f"Неизвестная медиа команда: {media_action}")

            # ── Буфер обмена ──────────────────────────────────────────────────
            elif action == "CLIPBOARD_GET":
                text = get_clipboard()
                b64 = base64.b64encode(text.encode("utf-8")).decode()
                self.send_line(f"CLIPBOARDB64:{b64}")

            elif action == "CLIPBOARD_SET":
                b64 = parts[1] if len(parts) > 1 else ""
                text = base64.b64decode(b64).decode("utf-8")
                set_clipboard(text)
                log.info(f"Буфер обмена установлен: {text[:50]}...")

            # ── Приложения ────────────────────────────────────────────────────
            elif action == "LISTAPPS":
                def send_apps():
                    windows = get_open_windows()
                    payload = json.dumps(windows, ensure_ascii=False)
                    b64 = base64.b64encode(payload.encode("utf-8")).decode()
                    self.send_line(f"APPSB64:{b64}")
                threading.Thread(target=send_apps, daemon=True).start()

            elif action == "FOCUSAPP":
                title = cmd[len("FOCUSAPP "):]
                focus_window(title)

            # ── Браузер / запуск ──────────────────────────────────────────────
            elif action == "OPENURL":
                b64 = parts[1] if len(parts) > 1 else ""
                url = base64.b64decode(b64).decode("utf-8")
                if not url.startswith(("http://", "https://")):
                    url = "https://" + url
                import webbrowser
                webbrowser.open(url)
                log.info(f"Открыт URL: {url}")

            elif action == "LAUNCH":
                b64 = parts[1] if len(parts) > 1 else ""
                command = base64.b64decode(b64).decode("utf-8")
                subprocess.Popen(command, shell=True)
                log.info(f"Запущено: {command}")

            # ── Питание ───────────────────────────────────────────────────────
            elif action == "POWER":
                power_action = parts[1].upper() if len(parts) > 1 else ""
                if power_action == "SLEEP":
                    if WINDOWS:
                        ctypes.windll.PowrProf.SetSuspendState(0, 1, 0)
                    else:
                        subprocess.call(["systemctl", "suspend"])
                elif power_action == "SHUTDOWN":
                    if WINDOWS:
                        subprocess.call(["shutdown", "/s", "/t", "5"])
                    else:
                        subprocess.call(["shutdown", "-h", "now"])
                elif power_action == "RESTART":
                    if WINDOWS:
                        subprocess.call(["shutdown", "/r", "/t", "5"])
                    else:
                        subprocess.call(["shutdown", "-r", "now"])

            else:
                log.warning(f"Неизвестная команда: {cmd}")

        except Exception as e:
            log.error(f"Ошибка при выполнении '{cmd}': {e}")


# ─── Точка входа ─────────────────────────────────────────────────────────────

def main():
    log.info("=" * 50)
    log.info("  RemoteMouse Server v2.0")
    log.info(f"  Имя ПК:  {HOSTNAME}")
    log.info(f"  TCP:     порт {TCP_PORT}")
    log.info(f"  UDP:     порт {UDP_PORT} (автообнаружение)")
    log.info("=" * 50)

    # Показать IP-адреса
    try:
        hostname = socket.gethostname()
        ips = socket.getaddrinfo(hostname, None, socket.AF_INET)
        for ip_info in ips:
            log.info(f"  IP:      {ip_info[4][0]}")
    except Exception:
        pass

    log.info("  Ctrl+C чтобы остановить")
    log.info("=" * 50)

    # Запускаем UDP discovery в фоне
    t = threading.Thread(target=discovery_server, daemon=True)
    t.start()

    # TCP сервер
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("", TCP_PORT))
    server.listen(5)
    log.info(f"Жду подключений...")

    try:
        while True:
            conn, addr = server.accept()
            ClientHandler(conn, addr).start()
    except KeyboardInterrupt:
        log.info("Остановлено.")
    finally:
        server.close()


if __name__ == "__main__":
    main()
