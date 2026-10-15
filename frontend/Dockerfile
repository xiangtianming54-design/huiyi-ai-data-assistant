# ===== 构建阶段 =====
FROM node:20-alpine AS build
WORKDIR /app

# npm 源可通过构建参数覆盖（默认 npmmirror，国内可达）
ARG NPM_REGISTRY=https://registry.npmmirror.com

COPY package.json package-lock.json ./
RUN npm ci --registry=${NPM_REGISTRY}

COPY . .
RUN npm run build

# ===== 运行阶段 =====
FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
