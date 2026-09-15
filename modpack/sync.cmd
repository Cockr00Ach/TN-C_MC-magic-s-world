@echo off
REM Wrapper to bypass PowerShell execution policy.
REM Usage: sync.cmd diff | push | pull
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0sync.ps1" %*
