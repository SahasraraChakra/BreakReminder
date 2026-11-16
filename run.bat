@echo off
:: Run silently in background (no console window)
if exist "BreakReminder.jar" (
    start "" javaw -jar "BreakReminder.jar"
) else (
    echo JAR not found. Run build_and_run.bat first.
    pause
)
