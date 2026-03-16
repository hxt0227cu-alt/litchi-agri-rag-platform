FROM python:3.11-slim

WORKDIR /app

RUN set -eux; \
    sed -i 's@http://deb.debian.org@https://mirrors.aliyun.com@g' /etc/apt/sources.list.d/debian.sources; \
    sed -i 's@http://security.debian.org@https://mirrors.aliyun.com@g' /etc/apt/sources.list.d/debian.sources; \
    apt-get update; \
    apt-get install -y --no-install-recommends libglib2.0-0 libgl1; \
    rm -rf /var/lib/apt/lists/*

COPY diagnosis-service/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY diagnosis-service/server.py ./server.py
COPY models /models

ENV DIAGNOSIS_HOST=0.0.0.0
ENV DIAGNOSIS_PORT=8090
ENV DIAGNOSIS_MODEL_PATH=/models/yolov8-litchi.pt
ENV DIAGNOSIS_STRICT_MODEL=false

EXPOSE 8090

CMD ["python", "server.py"]
