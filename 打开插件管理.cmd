@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-Plugin-Admin.ps1"
if errorlevel 1 pause
