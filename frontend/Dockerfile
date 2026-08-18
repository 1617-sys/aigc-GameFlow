# 构建 Vue 静态资源。
FROM node:22-alpine AS build
WORKDIR /web
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 非 root Nginx 负责静态页面和后端反向代理。
FROM nginxinc/nginx-unprivileged:1.29.4-alpine-slim
COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY --from=build /web/dist /usr/share/nginx/html
EXPOSE 8080
