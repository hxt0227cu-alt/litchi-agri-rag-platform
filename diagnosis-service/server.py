import hashlib
import json
import os
import re
import tempfile
from decimal import Decimal
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


HOST = os.getenv("DIAGNOSIS_HOST", "0.0.0.0")
PORT = int(os.getenv("DIAGNOSIS_PORT", "8090"))
MODEL_PATH = Path(os.getenv("DIAGNOSIS_MODEL_PATH", str(Path(__file__).resolve().parents[1] / "models" / "yolov8-litchi.pt")))

YOLO = None
YOLO_MODEL = None
YOLO_ERROR = None


def try_load_yolo():
    global YOLO, YOLO_MODEL, YOLO_ERROR
    try:
        from ultralytics import YOLO as UltralyticsYOLO  # type: ignore

        YOLO = UltralyticsYOLO
        if MODEL_PATH.exists():
            YOLO_MODEL = YOLO(str(MODEL_PATH))
    except Exception as exc:  # pragma: no cover - depends on optional packages
        YOLO_ERROR = str(exc)


def clean_disease_name(name: str) -> str:
    lowered = name.lower()
    if "anthracnose" in lowered or "tanju" in lowered or "炭疽" in name:
        return "炭疽病"
    if "霜疫" in name or "mildew" in lowered or "blight" in lowered or "shuangyi" in lowered:
        return "霜疫霉病"
    if "酸腐" in name or "suanfu" in lowered:
        return "酸腐病"
    return name.strip() or "未知病害"


def suggestions_for(disease_name: str) -> list[str]:
    mapping = {
        "霜疫霉病": [
            "加强果园通风透光，及时清理病叶病果。",
            "发病初期可喷施烯酰吗啉或霜霉威盐酸盐。",
            "雨季来临前提前预防，每 7 到 10 天复查一次。",
        ],
        "炭疽病": [
            "冬季清园并剪除病枝病叶，降低越冬病源。",
            "可结合咪鲜胺或苯醚甲环唑进行防治。",
            "果实发育期重点巡查，避免高温高湿环境持续过久。",
        ],
        "酸腐病": [
            "采后减少机械损伤，及时剔除受伤果。",
            "贮运环节保持清洁并控制湿度。",
            "发现腐果后尽快隔离，避免二次感染。",
        ],
    }
    return mapping.get(
        disease_name,
        [
            "建议结合田间症状做进一步人工复核。",
            "如症状持续扩散，请咨询专业农技人员制定防治方案。",
        ],
    )


def fallback_predict(filename: str, content: bytes) -> dict:
    lowered = filename.lower()
    if any(token in lowered for token in ["tanju", "anthracnose", "炭疽"]):
        names = ["炭疽病", "霜疫霉病", "酸腐病"]
    elif any(token in lowered for token in ["shuangyi", "mildew", "霜疫", "blight"]):
        names = ["霜疫霉病", "炭疽病", "酸腐病"]
    else:
        index = int(hashlib.sha256(content).hexdigest(), 16) % 3
        orders = [
            ["霜疫霉病", "炭疽病", "酸腐病"],
            ["炭疽病", "霜疫霉病", "酸腐病"],
            ["酸腐病", "炭疽病", "霜疫霉病"],
        ]
        names = orders[index]

    scores = [Decimal("0.72"), Decimal("0.19"), Decimal("0.09")]
    return {
        "disease": names[0],
        "confidence": float(scores[0]),
        "suggestions": suggestions_for(names[0]),
        "diseases": [
            {"name": names[0], "confidence": float(scores[0])},
            {"name": names[1], "confidence": float(scores[1])},
            {"name": names[2], "confidence": float(scores[2])},
        ],
        "engine": "demo-rule",
        "demoMode": True,
        "note": "未检测到可用 YOLO 模型，当前使用独立演示规则识别服务。",
    }


def yolo_predict(filename: str, content: bytes) -> dict | None:
    if YOLO_MODEL is None:  # pragma: no cover - depends on optional packages
        return None

    suffix = Path(filename).suffix or ".jpg"
    temp_path = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            temp_file.write(content)
            temp_path = Path(temp_file.name)

        result = YOLO_MODEL(str(temp_path), verbose=False)[0]
        names_map = getattr(result, "names", {}) or {}
        boxes = getattr(result, "boxes", None)
        if boxes is None or len(boxes) == 0:
            return {
                "disease": "未知病害",
                "confidence": 0.0,
                "suggestions": suggestions_for("未知病害"),
                "diseases": [],
                "engine": "ultralytics-yolo",
                "demoMode": False,
                "note": "模型未检测到明显病害目标。",
            }

        aggregated: dict[str, float] = {}
        for box in boxes:
            cls_id = int(box.cls[0].item())
            conf = float(box.conf[0].item())
            raw_name = str(names_map.get(cls_id, f"class-{cls_id}"))
            name = clean_disease_name(raw_name)
            aggregated[name] = max(conf, aggregated.get(name, 0.0))

        ranking = sorted(aggregated.items(), key=lambda item: item[1], reverse=True)
        primary_name, primary_score = ranking[0]
        return {
            "disease": primary_name,
            "confidence": round(primary_score, 4),
            "suggestions": suggestions_for(primary_name),
            "diseases": [
                {"name": name, "confidence": round(score, 4)}
                for name, score in ranking[:3]
            ],
            "engine": "ultralytics-yolo",
            "demoMode": False,
            "note": f"使用模型文件 {MODEL_PATH.name} 进行推理。",
        }
    finally:  # pragma: no cover - temp cleanup
        if temp_path and temp_path.exists():
            temp_path.unlink(missing_ok=True)


def parse_multipart(headers, body: bytes) -> tuple[str, bytes]:
    content_type = headers.get("Content-Type", "")
    match = re.search(r"boundary=([^;]+)", content_type)
    if not match:
        raise ValueError("Missing multipart boundary")

    boundary = match.group(1).strip().strip('"').encode("utf-8")
    parts = body.split(b"--" + boundary)
    for part in parts:
        part = part.strip()
        if not part or part == b"--":
            continue

        header_block, _, data = part.partition(b"\r\n\r\n")
        disposition = ""
        for raw_header in header_block.decode("utf-8", errors="ignore").split("\r\n"):
            if raw_header.lower().startswith("content-disposition:"):
                disposition = raw_header
                break

        if 'name="file"' not in disposition:
            continue

        filename_match = re.search(r'filename="([^"]*)"', disposition)
        filename = filename_match.group(1) if filename_match else "diagnosis-image.jpg"
        return filename, data.rstrip(b"\r\n")

    raise ValueError("Multipart request does not contain file part")


class DiagnosisHandler(BaseHTTPRequestHandler):
    server_version = "LitchiDiagnosisService/0.1"

    def _send_json(self, status: int, payload: dict):
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        if self.path != "/health":
            self._send_json(404, {"message": "Not found"})
            return

        self._send_json(
            200,
            {
                "status": "ok",
                "engine": "ultralytics-yolo" if YOLO_MODEL else "demo-rule",
                "demoMode": YOLO_MODEL is None,
                "modelPath": str(MODEL_PATH),
                "modelLoaded": YOLO_MODEL is not None,
                "modelError": YOLO_ERROR,
            },
        )

    def do_POST(self):
        if self.path != "/predict":
            self._send_json(404, {"message": "Not found"})
            return

        try:
            length = int(self.headers.get("Content-Length", "0"))
            payload = self.rfile.read(length)
            filename, content = parse_multipart(self.headers, payload)
            result = yolo_predict(filename, content) or fallback_predict(filename, content)
            self._send_json(200, result)
        except Exception as exc:
            self._send_json(400, {"message": str(exc)})

    def log_message(self, fmt, *args):
        print(f"[diagnosis-service] {self.address_string()} - {fmt % args}")


if __name__ == "__main__":
    try_load_yolo()
    httpd = ThreadingHTTPServer((HOST, PORT), DiagnosisHandler)
    print(f"Diagnosis service listening on http://{HOST}:{PORT}")
    print(f"Model path: {MODEL_PATH}")
    print(f"Engine: {'ultralytics-yolo' if YOLO_MODEL else 'demo-rule'}")
    httpd.serve_forever()
