@echo off
rem Double-click this file to open the Internet Access Control checklist window.
rem It runs the PowerShell GUI; a UAC (Administrator) prompt will appear.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0InternetAccessControl.ps1"
