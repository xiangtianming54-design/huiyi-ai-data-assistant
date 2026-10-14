FROM python:3.11-slim

WORKDIR /app

# pip 源可通过构建参数覆盖（默认阿里云镜像，CI 可切回官方源）
ARG PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i ${PIP_INDEX_URL}

COPY app ./app

EXPOSE 8001

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
