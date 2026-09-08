@echo off
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo Se necesitan permisos de administrador. Reintentando...
    powershell -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar.ps1"
pause
