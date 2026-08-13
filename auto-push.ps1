# ============================================================
# RemoteMouse Auto-Push — следит за папкой и пушит на GitHub
# ============================================================
# Как использовать:
#   1. Установи Git: https://git-scm.com/download/win
#   2. Создай репозиторий на GitHub (кнопка "New repository")
#   3. Открой PowerShell, перейди в папку проекта и выполни:
#        git init
#        git remote add origin https://github.com/ТВОЙ_ЮЗЕРНЕЙМ/RemoteMouse.git
#        git add .
#        git commit -m "initial"
#        git push -u origin main
#   4. Запусти этот скрипт двойным кликом или командой:
#        powershell -ExecutionPolicy Bypass -File auto-push.ps1
#
#   Готово! Теперь любое сохранение файла → автоматический push.
#   Скрипт работает в фоне. Чтобы остановить — закрой окно PowerShell.
# ============================================================

$projectPath = $PSScriptRoot  # папка где лежит этот скрипт
$cooldownSeconds = 10         # пауза после пуша (чтобы не спамить)

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  RemoteMouse Auto-Push" -ForegroundColor Cyan
Write-Host "  Слежу за: $projectPath" -ForegroundColor Cyan
Write-Host "  Ctrl+C чтобы остановить" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Проверка что git доступен
try {
    git --version | Out-Null
} catch {
    Write-Host "ОШИБКА: Git не найден! Установи его: https://git-scm.com/download/win" -ForegroundColor Red
    Read-Host "Нажми Enter для выхода"
    exit 1
}

# Проверка что это git-репозиторий
if (-not (Test-Path "$projectPath\.git")) {
    Write-Host "ОШИБКА: Папка не является git-репозиторием." -ForegroundColor Red
    Write-Host "Выполни сначала: git init && git remote add origin https://github.com/..." -ForegroundColor Yellow
    Read-Host "Нажми Enter для выхода"
    exit 1
}

$lastPushTime = [datetime]::MinValue

function Push-Changes {
    $now = Get-Date
    if (($now - $lastPushTime).TotalSeconds -lt $cooldownSeconds) {
        return  # cooldown — не спамим пушами
    }

    Set-Location $projectPath

    # Проверяем есть ли изменения
    $status = git status --porcelain 2>&1
    if ([string]::IsNullOrWhiteSpace($status)) {
        return  # нет изменений
    }

    $timestamp = $now.ToString("yyyy-MM-dd HH:mm:ss")
    $changedFiles = ($status -split "`n" | ForEach-Object { $_.Trim().Substring(3) }) -join ", "
    $commitMsg = "auto: $timestamp | $changedFiles"

    # Ограничиваем длину сообщения
    if ($commitMsg.Length -gt 120) {
        $fileCount = ($status -split "`n").Count
        $commitMsg = "auto: $timestamp | $fileCount файл(ов) изменено"
    }

    Write-Host ""
    Write-Host "[$timestamp] Обнаружены изменения:" -ForegroundColor Yellow
    Write-Host $status -ForegroundColor DarkGray

    git add -A 2>&1 | Out-Null
    $commitResult = git commit -m $commitMsg 2>&1
    Write-Host $commitResult -ForegroundColor DarkGray

    $pushResult = git push 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ Запушено!" -ForegroundColor Green
    } else {
        Write-Host "⚠️ Ошибка push: $pushResult" -ForegroundColor Red
    }

    $script:lastPushTime = Get-Date
}

# Создаём наблюдатель за файловой системой
$watcher = New-Object System.IO.FileSystemWatcher
$watcher.Path = $projectPath
$watcher.IncludeSubdirectories = $true
$watcher.EnableRaisingEvents = $false
$watcher.NotifyFilter = [System.IO.NotifyFilters]::FileName -bor
                         [System.IO.NotifyFilters]::LastWrite -bor
                         [System.IO.NotifyFilters]::DirectoryName

# Фильтруем .git папку
$ignorePatterns = @(".git", "build", ".gradle", ".idea", "*.iml")

Write-Host "Жду изменений..." -ForegroundColor Green
Write-Host ""

$watcher.EnableRaisingEvents = $true

try {
    while ($true) {
        $result = $watcher.WaitForChanged(
            [System.IO.WatcherChangeTypes]::All, 2000
        )

        if (-not $result.TimedOut) {
            $skip = $false
            foreach ($pattern in $ignorePatterns) {
                if ($result.Name -like "*$pattern*") {
                    $skip = $true
                    break
                }
            }

            if (-not $skip) {
                # Небольшая задержка — файл может ещё записываться
                Start-Sleep -Milliseconds 500
                Push-Changes
            }
        }
    }
} finally {
    $watcher.Dispose()
    Write-Host "Наблюдатель остановлен." -ForegroundColor Yellow
}
