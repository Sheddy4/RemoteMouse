@echo off
title RemoteMouse Server
chcp 65001 > nul
set PYTHONIOENCODING=utf-8
cd /d "%~dp0"

:: Проверяем Python
python --version >nul 2>&1
if errorlevel 1 (
    echo Python не найден! Скачай с python.org
    pause
    exit /b 1
)

:: Ставим зависимости если нужно
echo Проверка зависимостей...
pip install -r requirements.txt -q

:: Запускаем сервер
echo.
echo Запускаю RemoteMouse Server...
echo.
python server.py
pause
