@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist "target\lc-workshop-0.1.0-SNAPSHOT.jar" (
    echo [错误] 还没打包，请先运行： mvn clean package
    pause
    exit /b 1
)
java -jar target\lc-workshop-0.1.0-SNAPSHOT.jar index %* --write
echo.
pause
