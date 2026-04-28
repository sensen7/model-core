# ---- 构建阶段 ----
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# 先复制 pom.xml，利用 Docker 层缓存，依赖未变时跳过下载
COPY pom.xml .
RUN mvn dependency:go-offline -q

# 复制源码并编译（跳过测试）
COPY src ./src
RUN mvn clean package -DskipTests -q

# ---- 运行阶段 ----
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 设置时区为上海
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

# 从构建阶段复制 jar
COPY --from=builder /build/target/model-core-1.0.0.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动命令（开启虚拟线程支持）
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
