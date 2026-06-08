@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0bump_version.ps1"
echo.
pause
