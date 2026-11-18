@echo off
cd /d "%~dp0"
if exist "dist\java\bin\java.exe" (
    "dist\java\bin\java.exe" -jar "game.jar"
) else (
    java -jar "game.jar"
)
if errorlevel 1 pause
