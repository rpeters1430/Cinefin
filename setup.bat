@echo off
REM Cinefin Windows Setup Entry Point
REM This batch file bridges CMD users to the PowerShell setup script.

echo --- Cinefin Windows Setup Launcher ---

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\setup-windows.ps1"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Setup failed with exit code %ERRORLEVEL%.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [SUCCESS] You can now build with: gradlew.bat assembleDebug
pause
