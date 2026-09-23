@echo off
rem HotelsX one-click build launcher.
rem This file is intentionally pure ASCII: cmd.exe mis-parses batch files that mix
rem multi-byte characters with a mid-file chcp. All Chinese messages and the real
rem logic live in build.ps1 (UTF-8 with BOM), which PowerShell reads correctly.
cd /d "%~dp0"

where powershell >nul 2>nul
if errorlevel 1 (
    echo [ERROR] PowerShell not found. Please build manually:
    echo         mvnw.cmd clean package -DskipTests
    echo         Requires JDK 21 or newer.
    echo.
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
set "HX_EXIT=%ERRORLEVEL%"

echo.
pause
exit /b %HX_EXIT%
