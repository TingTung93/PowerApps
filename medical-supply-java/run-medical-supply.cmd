@echo off
setlocal
set DIR=%~dp0
java -jar "%DIR%MedicalSupply-RC.jar" %*
if errorlevel 1 pause
endlocal
