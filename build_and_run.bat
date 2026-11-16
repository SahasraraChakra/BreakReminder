@echo off
setlocal

set "SRC=src\BreakReminder.java"
set "OUT=out"
set "JAR=BreakReminder.jar"
set "MAIN=BreakReminder"

echo ================================================
echo   Break Reminder - Build Script
echo ================================================

:: Check Java
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found. Please install Java 8 or later.
    pause & exit /b 1
)

:: Compile
echo [1/3] Compiling...
if not exist "%OUT%" mkdir "%OUT%"
javac -source 8 -target 8 -d "%OUT%" "%SRC%"
if errorlevel 1 (
    echo [ERROR] Compilation failed.
    pause & exit /b 1
)
echo       OK

:: Package into JAR
echo [2/3] Packaging JAR...
echo Main-Class: %MAIN% > manifest.mf
jar cfm "%JAR%" manifest.mf -C "%OUT%" .
del manifest.mf
if errorlevel 1 (
    echo [ERROR] JAR creation failed.
    pause & exit /b 1
)
echo       OK

:: Launch
echo [3/3] Launching...
echo ================================================
start "" javaw -jar "%JAR%"
echo   App started - check the system tray!
echo ================================================

pause
