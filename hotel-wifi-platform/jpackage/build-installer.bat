@echo off
REM =============================================
REM 酒店 WiFi 管理计费系统 — 二进制安装包构建脚本
REM Windows 版本 (使用 jpackage)
REM =============================================

set APP_NAME=HotelWiFi
set APP_VERSION=1.0.0
set MAIN_CLASS=com.alenwifidata.api.HotelWifiApplication
set MAIN_JAR=hotel-wifi-api-%APP_VERSION%.jar
set OUTPUT_DIR=installer
set VENDOR=AlenWiFiData

echo ========================================
echo 构建 %APP_NAME% %APP_VERSION% 安装包
echo ========================================

REM 1. 先构建后端 JAR
cd ..
call mvn clean package -DskipTests -pl hotel-wifi-api -am
if %ERRORLEVEL% NEQ 0 (
    echo Maven 构建失败！
    exit /b 1
)

REM 2. 准备 jpackage 输入目录
if exist %OUTPUT_DIR% rmdir /s /q %OUTPUT_DIR%
mkdir %OUTPUT_DIR%\input
copy hotel-wifi-api\target\%MAIN_JAR% %OUTPUT_DIR%\input\

REM 3. 构建前端 (可选)
cd ..\hotel-wifi-frontend
if exist package.json (
    call pnpm install
    call pnpm build
    REM 将前端静态文件复制到后端的 static 目录
    mkdir ..\hotel-wifi-platform\%OUTPUT_DIR%\input\static
    xcopy /E /Y dist\* ..\hotel-wifi-platform\%OUTPUT_DIR%\input\static\
)
cd ..\hotel-wifi-platform

REM 4. 使用 jpackage 生成安装包
echo 正在生成 Windows 安装包...
jpackage ^
    --name %APP_NAME% ^
    --app-version %APP_VERSION% ^
    --vendor %VENDOR% ^
    --input %OUTPUT_DIR%\input ^
    --main-jar %MAIN_JAR% ^
    --main-class %MAIN_CLASS% ^
    --type msi ^
    --dest %OUTPUT_DIR% ^
    --win-dir-chooser ^
    --win-menu ^
    --win-shortcut ^
    --win-menu-group "%VENDOR%" ^
    --description "酒店互联网管理计费系统" ^
    --resource-dir resources ^
    --java-options "-Xms256m -Xmx1024m -Dfile.encoding=UTF-8"

if %ERRORLEVEL% EQU 0 (
    echo ========================================
    echo 安装包生成成功！
    echo 输出: %OUTPUT_DIR%\%APP_NAME%-%APP_VERSION%.msi
    echo ========================================
) else (
    echo 安装包生成失败！
    exit /b 1
)
