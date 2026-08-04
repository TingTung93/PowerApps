@echo off
REM Medical Supply Tracking launcher.
REM   run-medical-supply.cmd              Browser UI (default)
REM   run-medical-supply.cmd --classic-ui Swing desktop fallback
setlocal
set DIR=%~dp0
java -jar "%DIR%MedicalSupply-RC.jar" %*
if errorlevel 1 pause
endlocal
