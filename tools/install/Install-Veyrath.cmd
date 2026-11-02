@echo off
rem Double-click launcher for Install-Veyrath.ps1 (runs it without changing the system's PowerShell policy).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install-Veyrath.ps1" %*
echo.
pause
