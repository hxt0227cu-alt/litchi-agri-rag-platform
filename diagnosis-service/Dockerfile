ARG PYTHON_IMAGE=swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/python:3.11-slim
FROM ${PYTHON_IMAGE} AS builder
ARG DEBIAN_MIRROR=https://mirrors.aliyun.com/debian
ARG DEBIAN_SECURITY_MIRROR=https://mirrors.aliyun.com/debian-security
WORKDIR /app
RUN set -eux; \
    if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
        sed -i "s|http://deb.debian.org/debian|${DEBIAN_MIRROR}|g; s|http://deb.debian.org/debian-security|${DEBIAN_SECURITY_MIRROR}|g" /etc/apt/sources.list.d/debian.sources; \
    fi; \
    apt-get update; \
    apt-get install -y --no-install-recommends gcc libglib2.0-0 libgl1; \
    rm -rf /var/lib/apt/lists/*
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
COPY requirements.txt .
RUN pip install --no-cache-dir --timeout 300 --retries 5 -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt

FROM ${PYTHON_IMAGE}
ARG DEBIAN_MIRROR=https://mirrors.aliyun.com/debian
ARG DEBIAN_SECURITY_MIRROR=https://mirrors.aliyun.com/debian-security
RUN set -eux; \
    if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
        sed -i "s|http://deb.debian.org/debian|${DEBIAN_MIRROR}|g; s|http://deb.debian.org/debian-security|${DEBIAN_SECURITY_MIRROR}|g" /etc/apt/sources.list.d/debian.sources; \
    fi; \
    apt-get update; \
    apt-get install -y --no-install-recommends libglib2.0-0 libgl1; \
    rm -rf /var/lib/apt/lists/*
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PATH="/opt/venv/bin:$PATH"
WORKDIR /app/diagnosis-service
COPY --from=builder /opt/venv /opt/venv
COPY server.py .
ENV DIAGNOSIS_HOST=0.0.0.0
ENV DIAGNOSIS_PORT=8090
ENV DIAGNOSIS_MODEL_PATH=/models/yolov8-litchi.pt
ENV DIAGNOSIS_STRICT_MODEL=false
RUN groupadd -r appuser && useradd -r -g appuser appuser && mkdir -p /app/.yolo && chown -R appuser:appuser /app
USER appuser
EXPOSE 8090
HEALTHCHECK CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8090/health', timeout=3)" || exit 1
CMD ["python", "server.py"]
