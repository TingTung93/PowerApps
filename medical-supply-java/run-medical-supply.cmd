@echo off
REM Medical Supply Tracking launcher.
REM   run-medical-supply.cmd              Browser UI (default)
REM   run-medical-supply.cmd --classic-ui Swing desktop fallback
setlocal
set "JAR=%~dp0MedicalSupply-RC.jar"
if not exist "%JAR%" set "JAR=%~dp0dist\MedicalSupply-RC.jar"
if not exist "%JAR%" (
  echo MedicalSupply-RC.jar was not found. Run build.ps1 first.
  pause
  exit /b 1
)
java -jar "%JAR%" %*
if errorlevel 1 pause
endlocal
