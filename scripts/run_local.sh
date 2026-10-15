#!/usr/bin/env bash
# 本地开发模式一键启动（基础设施 + Python Sidecar + Java 主服务）
# Windows 用户请按 README「方式二」分步执行；本脚本适用于 macOS / Linux / WSL。
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> 启动基础设施（MySQL 3307 / Redis 6379）"
docker compose up -d mysql redis

echo "==> 启动 Python Sidecar（8001）"
(
  cd python-service
  if [ ! -d .venv ]; then
    python -m venv .venv
  fi
  source .venv/bin/activate
  pip install -q -r requirements-dev.txt
  nohup uvicorn app.main:app --port 8001 > ../logs-python.log 2>&1 &
  echo "    python-service pid=$!"
)

echo "==> 启动 Java 主服务（8080）"
(
  cd backend
  nohup mvn spring-boot:run > ../logs-backend.log 2>&1 &
  echo "    backend pid=$!"
)

echo "==> 前端开发服务器（5173）"
(
  cd frontend
  if [ ! -d node_modules ]; then
    npm install
  fi
  nohup npm run dev > ../logs-frontend.log 2>&1 &
  echo "    frontend pid=$!"
)

echo ""
echo "全部启动中，日志在仓库根目录 logs-*.log"
echo "健康检查: curl http://localhost:8080/api/health && curl http://localhost:8001/health"
