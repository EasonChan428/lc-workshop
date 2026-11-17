@echo off
chcp 65001 >nul
cd /d "%~dp0"

rem ============================================================
rem  题解工坊 Web 端启动器（双击运行）
rem    - 用法一：直接双击，自动探测题库目录
rem    - 用法二：把题库文件夹拖到本文件上，用拖进来的目录当题库根
rem  前提：本机装了 Java 21+；jar 已用 mvn clean package 打好
rem ============================================================

set JAR=target\lc-workshop-0.1.0-SNAPSHOT-web.jar

if not exist "%JAR%" (
    echo [提示] 没找到 %JAR%，尝试用 Maven 现场打包...
    call mvn -B -q clean package -DskipTests
    if not exist "%JAR%" (
        echo [错误] 打包失败。请确认已安装 Maven，或手动执行： mvn clean package
        pause
        exit /b 1
    )
)

if not "%~1"=="" set WORKSHOP_ROOT=%~1

echo 题库目录：%WORKSHOP_ROOT%（为空则自动探测 / 使用上次在页面上选择的目录）
echo 正在启动，稍后会自动打开浏览器： http://localhost:8080
echo 关闭服务请按 Ctrl+C
echo.
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar "%JAR%"
pause
