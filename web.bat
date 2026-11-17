@echo off
chcp 65001 >nul
cd /d "%~dp0"

rem 用法：
rem   1) 直接双击：自动探测题库目录（当前目录下的 src/leetcode，否则当前目录）
rem   2) 把题库文件夹拖到本文件上：用拖进来的目录当题库根
if not "%~1"=="" set WORKSHOP_ROOT=%~1

echo 题库目录：%WORKSHOP_ROOT%（为空则自动探测）
echo 启动后浏览器访问： http://localhost:8080
echo 关闭服务请按 Ctrl+C
echo.
mvn -B compile exec:exec
pause
