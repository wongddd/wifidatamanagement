#!/bin/bash
# =============================================
# 酒店 WiFi 管理计费系统 — 二进制安装包构建脚本
# Linux 版本 (使用 jpackage)
# =============================================

APP_NAME="HotelWiFi"
APP_VERSION="1.0.0"
MAIN_CLASS="com.alenwifidata.api.HotelWifiApplication"
MAIN_JAR="hotel-wifi-api-${APP_VERSION}.jar"
OUTPUT_DIR="installer"
VENDOR="AlenWiFiData"

echo "========================================"
echo "构建 ${APP_NAME} ${APP_VERSION} 安装包"
echo "========================================"

# 1. 构建后端 JAR
cd ..
mvn clean package -DskipTests -pl hotel-wifi-api -am
if [ $? -ne 0 ]; then
    echo "Maven 构建失败！"
    exit 1
fi

# 2. 准备 jpackage 输入目录
rm -rf "${OUTPUT_DIR}"
mkdir -p "${OUTPUT_DIR}/input"
cp "hotel-wifi-api/target/${MAIN_JAR}" "${OUTPUT_DIR}/input/"

# 3. 构建前端（可选）
if [ -f "../hotel-wifi-frontend/package.json" ]; then
    cd ../hotel-wifi-frontend
    pnpm install && pnpm build
    mkdir -p "../hotel-wifi-platform/${OUTPUT_DIR}/input/static"
    cp -r dist/* "../hotel-wifi-platform/${OUTPUT_DIR}/input/static/"
    cd ../hotel-wifi-platform
fi

# 4. 使用 jpackage 生成安装包
echo "正在生成 Linux 安装包..."
jpackage \
    --name "${APP_NAME}" \
    --app-version "${APP_VERSION}" \
    --vendor "${VENDOR}" \
    --input "${OUTPUT_DIR}/input" \
    --main-jar "${MAIN_JAR}" \
    --main-class "${MAIN_CLASS}" \
    --type rpm \
    --dest "${OUTPUT_DIR}" \
    --description "酒店互联网管理计费系统" \
    --resource-dir resources \
    --java-options "-Xms256m -Xmx1024m -Dfile.encoding=UTF-8"

if [ $? -eq 0 ]; then
    echo "========================================"
    echo "安装包生成成功！"
    echo "RPM: ${OUTPUT_DIR}/${APP_NAME}-${APP_VERSION}-1.x86_64.rpm"
    echo "========================================"
else
    echo "安装包生成失败！"
    exit 1
fi
