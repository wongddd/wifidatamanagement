# 多阶段构建
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY hotel-wifi-common/pom.xml hotel-wifi-common/
COPY hotel-wifi-core/pom.xml hotel-wifi-core/
COPY hotel-wifi-api/pom.xml hotel-wifi-api/
# 先下载依赖
RUN mvn dependency:go-offline -B -q || true
# 构建
COPY . .
RUN mvn clean package -DskipTests -pl hotel-wifi-api -am -q

# 运行阶段
FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache tzdata curl
ENV TZ=Asia/Shanghai
WORKDIR /app
COPY --from=build /app/hotel-wifi-api/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
