# 前端 Dockerfile - 在线课程平台
# 多阶段构建

# 阶段1: 构建阶段
FROM node:20-alpine AS builder

WORKDIR /build

# 复制 package.json 和 package-lock.json
COPY package*.json ./

# 安装依赖
RUN npm install --legacy-peer-deps

# 复制源代码
COPY . .

# Vite 在构建期注入公开的 Turnstile Site Key；禁止生产镜像回退到测试 Key。
ARG VITE_TURNSTILE_SITE_KEY
ENV VITE_TURNSTILE_SITE_KEY=${VITE_TURNSTILE_SITE_KEY}
RUN test -n "$VITE_TURNSTILE_SITE_KEY"

# 构建生产版本
RUN npm run build

# 阶段2: 运行阶段
FROM nginx:alpine

# 安装 tzdata 并设置时区
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

# 复制构建产物到 Nginx 目录
COPY --from=builder /build/dist /usr/share/nginx/html

# 复制自定义 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 非 root 运行所需的 PID 和临时目录权限；保留 Compose 的 8888:80 映射。
# Docker 默认允许非 root 绑定容器内 80 端口，无需扩大运行时权限。
RUN sed -i '/^user[[:space:]]/d; s|^pid[[:space:]].*;|pid /tmp/nginx.pid;|' /etc/nginx/nginx.conf && \
    chown -R nginx:nginx /var/cache/nginx

USER nginx

# 暴露端口
EXPOSE 80

# 与 Compose 保持一致，使用 IPv4 回环避免 localhost 优先解析为未监听的 IPv6。
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1/health || exit 1

# 启动 Nginx
CMD ["nginx", "-g", "daemon off;"]
