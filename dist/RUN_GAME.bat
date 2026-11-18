@echo off
cd /d "%~dp0"
"java\bin\java.exe" -jar "game.jar"
if errorlevel 1 pause
