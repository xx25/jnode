@echo off
rem jNode FTN Mailer and Tosser startup script for Windows

setlocal enabledelayedexpansion

rem Configuration
set "JNODE_HOME=%~dp0.."
set "JAVA_OPTS=-Xms128m -Xmx512m -server -Dfile.encoding=UTF-8"

rem Check if Java is available
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java not found. Please install Java 11+ and add it to PATH.
    pause
    exit /b 1
)

rem Build classpath
set "CLASSPATH="
for %%a in ("%JNODE_HOME%\lib\*.jar") do (
    if defined CLASSPATH (
        set "CLASSPATH=!CLASSPATH!;%%a"
    ) else (
        set "CLASSPATH=%%a"
    )
)

rem Change to jNode directory and start
cd /d "%JNODE_HOME%"
echo Starting jNode...
java %JAVA_OPTS% -cp "%CLASSPATH%" jnode.main.Main etc\jnode.conf

if errorlevel 1 pause
endlocal