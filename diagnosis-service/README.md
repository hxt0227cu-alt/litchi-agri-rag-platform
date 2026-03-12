# Diagnosis Service

独立识病服务，默认监听 `http://localhost:8090`。

## 启动

```bash
cd diagnosis-service
python server.py
```

## 模式

- 如果存在 `../models/yolov8-litchi.pt` 且安装了 `ultralytics`，服务会尝试使用 YOLO 推理。
- 如果模型或依赖不存在，服务会自动降级为 `demo-rule`，并在返回结果里显式标记 `demoMode=true`。

## 接口

- `GET /health`
- `POST /predict`
  - `multipart/form-data`
  - 字段名：`file`
