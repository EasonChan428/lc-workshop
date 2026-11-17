@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem ============================================================
rem  LC Workshop - Web launcher  (double-click to run)
rem    Requires: Java 21+
rem    Usage:  double-click, or drag a problem folder onto this file
rem ============================================================

title LC Workshop Web

set "JAR=target\lc-workshop-0.1.0-SNAPSHOT-web.jar"
set "PORT=8080"

rem ---- 1) Already running? ----
netstat -ano | findstr ":%PORT%" | findstr "LISTENING" >nul
if not errorlevel 1 (
    echo [INFO] Port %PORT% is already in use - maybe LC Workshop is already running.
    echo        Opening http://localhost:%PORT% in your browser...
    start "" "http://localhost:%PORT%"
    echo.
    echo        If that is not this app, stop the other process first:
    echo          netstat -ano ^| findstr :%PORT%
    echo          taskkill /PID ^<pid^> /F
    pause
    exit /b 0
)

rem ---- 2) Build if needed ----
if not exist "%JAR%" (
    echo [INFO] %JAR% not found, building with Maven...
    call mvn -B -q clean package -DskipTests
    if not exist "%JAR%" (
        echo.
        echo [ERROR] Build failed. Install Maven, or run: mvn clean package
        pause
        exit /b 1
    )
)

rem ---- 3) Optional folder passed by drag and drop ----
if not "%~1"=="" set "WORKSHOP_ROOT=%~1"

echo.
if defined WORKSHOP_ROOT (
    echo Problem folder : %WORKSHOP_ROOT%
) else (
    echo Problem folder : auto-detect / last chosen in the page
)
echo Starting... browser will open at http://localhost:%PORT%
echo Press Ctrl+C in this window to stop the server.
echo.

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar "%JAR%"

echo.
echo [INFO] Server stopped. Exit code: %ERRORLEVEL%
pause
endlocal
