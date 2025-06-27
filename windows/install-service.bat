@echo off
rem Install jNode as Windows service using NSSM
rem Download NSSM from https://nssm.cc/download

echo Installing jNode as Windows service...
echo.
echo Prerequisites:
echo - Download NSSM from https://nssm.cc/download
echo - Extract nssm.exe to this directory or add to PATH
echo - Run this script as Administrator
echo.

rem Check if running as admin
net session >nul 2>&1
if errorlevel 1 (
    echo ERROR: Please run as Administrator
    pause
    exit /b 1
)

rem Check if NSSM is available
nssm version >nul 2>&1
if errorlevel 1 (
    echo ERROR: NSSM not found. Please download from https://nssm.cc/download
    pause
    exit /b 1
)

rem Set service parameters
set "SERVICE_NAME=jNode"
set "JNODE_HOME=%~dp0.."
set "JAVA_PATH=java"

rem Stop and remove existing service if it exists
nssm stop "%SERVICE_NAME%" >nul 2>&1
nssm remove "%SERVICE_NAME%" confirm >nul 2>&1

rem Install service
nssm install "%SERVICE_NAME%" "%JAVA_PATH%"
nssm set "%SERVICE_NAME%" AppParameters "-Xms128m -Xmx512m -server -Dfile.encoding=UTF-8 -cp \"%JNODE_HOME%\lib\*\" jnode.main.Main \"%JNODE_HOME%\etc\jnode.conf\""
nssm set "%SERVICE_NAME%" AppDirectory "%JNODE_HOME%"
nssm set "%SERVICE_NAME%" DisplayName "jNode FTN Mailer"
nssm set "%SERVICE_NAME%" Description "jNode FidoNet Technology Network mailer and tosser service"
nssm set "%SERVICE_NAME%" Start SERVICE_AUTO_START

echo Service installed successfully!
echo Use these commands to manage the service:
echo   net start jNode     - Start service
echo   net stop jNode      - Stop service
echo   nssm edit jNode     - Edit service configuration
echo   nssm remove jNode   - Remove service
pause