FROM python:3.11-slim

WORKDIR /app

# pip 源可通过构建参数覆盖（默认腾讯云镜像：宿主机与构建容器内均可达）
ARG PIP_INDEX_URL=https://mirrors.cloud.tencent.com/pypi/simple/

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i ${PIP_INDEX_URL}

COPY app ./app

EXPOSE 8001

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
