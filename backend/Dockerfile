# 后端 Dockerfile - 在线课程平台
# 多阶段构建，优化镜像大小（支持多模块Maven项目）

# 阶段1: 构建阶段
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# 复制所有 pom.xml 并下载依赖（利用 Docker 缓存）
COPY pom.xml .
COPY course-common/pom.xml ./course-common/
COPY course-domain/pom.xml ./course-domain/
COPY course-application/pom.xml ./course-application/
COPY course-infrastructure/pom.xml ./course-infrastructure/
COPY course-web/pom.xml ./course-web/

# 下载依赖（多模块需要先创建目录结构）
RUN mvn dependency:go-offline -B || true

# 复制所有模块源代码
COPY course-common/src ./course-common/src
COPY course-domain/src ./course-domain/src
COPY course-application/src ./course-application/src
COPY course-infrastructure/src ./course-infrastructure/src
COPY course-web/src ./course-web/src

# 构建应用（跳过测试以加快构建速度）
RUN mvn clean package -DskipTests -B -pl course-web -am

# 阶段2: 运行阶段
FROM eclipse-temurin:17-jre-alpine

# 安装必要工具和设置时区
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

# 创建应用用户（安全最佳实践）
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

# 设置工作目录
WORKDIR /app

# 从构建阶段复制 JAR 包（多模块项目从course-web模块获取）
COPY --from=builder /build/course-web/target/*.jar app.jar

# 创建日志目录
RUN mkdir -p /app/logs && \
    chown -R appuser:appuser /app

# 切换到非 root 用户
USER appuser

# 暴露端口
EXPOSE 8082

# JVM 优化参数
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8082/api/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=prod"]
