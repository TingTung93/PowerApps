@echo off
setlocal
set "APP_DIR=%~dp0"
java -version >nul 2>&1
if errorlevel 1 (
  echo Java was not found on this workstation.
  echo Contact support and provide this folder location: %APP_DIR%
  pause
  exit /b 1
)
java -jar "%APP_DIR%CommercialTracking-RC.jar"
if errorlevel 1 (
  echo.
  echo Commercial Tracking ended with an error.
  pause
)
endlocal
