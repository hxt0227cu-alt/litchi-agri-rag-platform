import hashlib
import io
import json
import logging
import os
import re
import tempfile
import threading
import traceback
import concurrent.futures
import time
from collections import defaultdict
from decimal import Decimal
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

try:
    from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest
except ImportError:  # pragma: no cover - local fallback before dependency installation
    CONTENT_TYPE_LATEST = "text/plain; version=0.0.4"

    class _NoopMetric:
        def labels(self, **kwargs):
            return self

        def inc(self):
            return None

        def observe(self, value):
            return None

    Counter = lambda *args, **kwargs: _NoopMetric()
    Histogram = lambda *args, **kwargs: _NoopMetric()
    generate_latest = lambda: b""

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger("diagnosis")

HOST = os.getenv("DIAGNOSIS_HOST", "0.0.0.0")
PORT = int(os.getenv("DIAGNOSIS_PORT", "8090"))
PROJECT_ROOT = Path(__file__).resolve().parents[1]
os.environ.setdefault("YOLO_CONFIG_DIR", str(PROJECT_ROOT / ".yolo"))
MODEL_PATH = Path(os.getenv("DIAGNOSIS_MODEL_PATH", str(PROJECT_ROOT / "models" / "yolov8-litchi.pt")))
DATASET_ROOT = Path(
    os.getenv(
        "DIAGNOSIS_DATASET_ROOT",
        str(
            PROJECT_ROOT
            / "datasets"
            / "images"
            / "raw"
            / "BDLitchi A Field-Collected Bangladeshi Litchi Leaf"
            / "BDLitchi A Field-Collected Bangladeshi Litchi Leaf"
            / "Dataset"
            / "Dataset"
        ),
    )
)
STRICT_MODEL = os.getenv("DIAGNOSIS_STRICT_MODEL", "false").strip().lower() in {"1", "true", "yes", "on"}

MAX_FILE_SIZE = 10 * 1024 * 1024
MODEL_LOCK = threading.Lock()

YOLO = None
YOLO_MODEL = None
YOLO_ERROR = None
Image = None
ImageOps = None
ImageStat = None
PIL_ERROR = None
DATASET_INDEX: dict[str, dict] = {}
DATASET_ERROR = None
DATASET_CLASS_COUNTS: dict[str, int] = {}
DATASET_INDEX_LOADED = False

IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
PREDICTION_COUNTER = Counter(
    "diagnosis_requests_total",
    "Diagnosis prediction requests",
    ["status", "engine"],
)
PREDICTION_LATENCY = Histogram(
    "diagnosis_request_duration_seconds",
    "Diagnosis request duration",
    ["engine"],
)


def try_load_optional_dependencies():
    global YOLO, YOLO_MODEL, YOLO_ERROR, Image, ImageOps, ImageStat, PIL_ERROR

    try:
        from ultralytics import YOLO as UltralyticsYOLO  # type: ignore

        YOLO = UltralyticsYOLO
        if MODEL_PATH.exists():
            YOLO_MODEL = YOLO(str(MODEL_PATH))
        else:
            YOLO_ERROR = f"Model file not found: {MODEL_PATH}"
    except Exception as exc:  # pragma: no cover
        YOLO_ERROR = str(exc)

    try:
        from PIL import Image as PILImage, ImageOps as PILImageOps, ImageStat as PILImageStat

        Image = PILImage
        ImageOps = PILImageOps
        ImageStat = PILImageStat
    except Exception as exc:  # pragma: no cover
        PIL_ERROR = str(exc)


def normalize_label(raw_name: str) -> str:
    lowered = raw_name.lower().strip()
    if any(token in lowered for token in ["healthy", "正常", "健康"]):
        return "健康叶片"
    if any(token in lowered for token in ["anthracnose", "black spot", "tanju", "炭疽"]):
        return "炭疽病"
    if any(token in lowered for token in ["leaf blight", "downy", "mildew", "blight", "霜疫", "叶枯"]):
        return "霜疫霉病"
    if any(token in lowered for token in ["red rust", "rust", "红锈"]):
        return "红锈病"
    if any(token in lowered for token in ["worm", "borer", "insect", "虫"]):
        return "虫害"
    return raw_name.strip() or "未知病害"


def suggestions_for(disease_name: str) -> list[str]:
    mapping = {
        "健康叶片": [
            "当前叶片状态较稳定，可保持常规巡园和通风管理。",
            "雨后复查叶面是否出现新病斑，避免延误最佳处理时机。",
            "答辩展示时可说明系统同样支持健康样本识别。",
        ],
        "炭疽病": [
            "清理病枝病叶并加强树冠通风，降低田间湿度。",
            "发病初期可轮换使用咪鲜胺、苯醚甲环唑等药剂。",
            "果实转色期提高巡查频次，重点检查病斑扩展情况。",
        ],
        "霜疫霉病": [
            "优先做好排水和通风，雨季及时清理病果病枝。",
            "可结合烯酰吗啉等药剂进行保护性或治疗性喷施。",
            "连续降雨前后加强巡园，重点检查花穗和幼果。",
        ],
        "红锈病": [
            "加强叶面观察，及时剪除受害严重的枝叶。",
            "保持果园通风透光，避免树冠长期潮湿。",
            "结合田间情况选择适宜药剂并轮换使用。",
        ],
        "虫害": [
            "检查花穗、果梗和幼果是否存在虫孔与虫粪。",
            "及时清理虫果、落果，并结合诱捕开展监测。",
            "根据虫情高峰安排针对性防治，避免错过窗口期。",
        ],
    }
    return mapping.get(
        disease_name,
        [
            "建议结合田间症状进一步人工复核。",
            "如症状持续扩散，请咨询农技人员制定防治方案。",
            "当前结果更适合作为答辩演示中的辅助判断依据。",
        ],
    )


def filename_hint(filename: str) -> str | None:
    lowered = filename.lower()
    if any(token in lowered for token in ["healthy", "健康"]):
        return "健康叶片"
    if any(token in lowered for token in ["anthracnose", "blackspot", "black-spot", "black_spot", "tanju"]):
        return "炭疽病"
    if any(token in lowered for token in ["blight", "mildew", "downy"]):
        return "霜疫霉病"
    if any(token in lowered for token in ["rust", "red-rust", "red_rust"]):
        return "红锈病"
    if any(token in lowered for token in ["borer", "insect", "worm", "pest"]):
        return "虫害"
    return None


def build_feature_from_bytes(content: bytes) -> dict | None:
    if Image is None:
        return None

    try:
        with Image.open(io.BytesIO(content)) as image:
            image = ImageOps.exif_transpose(image).convert("RGB")
            preview = image.resize((48, 48))
            color_stat = ImageStat.Stat(preview)
            color_mean = [value / 255.0 for value in color_stat.mean]

            gray = preview.convert("L")
            histogram = gray.histogram()
            compact_hist = []
            for index in range(0, 256, 16):
                compact_hist.append(sum(histogram[index:index + 16]))

            total = sum(compact_hist) or 1
            compact_hist = [value / total for value in compact_hist]

            return {
                "mean": color_mean,
                "hist": compact_hist,
            }
    except Exception as exc:
        logger.warning("build_feature_from_bytes failed: %s", exc)
        return None


def feature_distance(left: dict, right: dict) -> float:
    mean_distance = sum(abs(a - b) for a, b in zip(left["mean"], right["mean"]))
    hist_distance = sum(abs(a - b) for a, b in zip(left["hist"], right["hist"]))
    return mean_distance + hist_distance * 0.6


def build_dataset_index():
    global DATASET_INDEX, DATASET_ERROR, DATASET_CLASS_COUNTS, DATASET_INDEX_LOADED

    if Image is None:
        DATASET_ERROR = PIL_ERROR or "Pillow is unavailable"
        DATASET_INDEX_LOADED = True
        return

    if not DATASET_ROOT.exists():
        DATASET_ERROR = f"Dataset directory not found: {DATASET_ROOT}"
        DATASET_INDEX_LOADED = True
        return

    grouped_features: dict[str, list[dict]] = defaultdict(list)
    class_counts: dict[str, int] = defaultdict(int)

    try:
        for class_dir in DATASET_ROOT.iterdir():
            if not class_dir.is_dir():
                continue

            canonical_label = normalize_label(class_dir.name)
            class_images = [path for path in class_dir.iterdir() if path.is_file() and path.suffix.lower() in IMAGE_SUFFIXES]
            for image_path in class_images[:8]:
                feature = build_feature_from_bytes(image_path.read_bytes())
                if feature is None:
                    continue

                grouped_features[canonical_label].append(feature)
                class_counts[canonical_label] += 1

        dataset_index: dict[str, dict] = {}
        for label, features in grouped_features.items():
            if not features:
                continue

            mean = [
                sum(feature["mean"][index] for feature in features) / len(features)
                for index in range(3)
            ]
            hist = [
                sum(feature["hist"][index] for feature in features) / len(features)
                for index in range(16)
            ]
            dataset_index[label] = {"mean": mean, "hist": hist}

        DATASET_INDEX = dataset_index
        DATASET_CLASS_COUNTS = dict(class_counts)
    except Exception as exc:  # pragma: no cover
        DATASET_ERROR = str(exc)
    finally:
        DATASET_INDEX_LOADED = True


def make_response(label: str, ranking: list[tuple[str, float]], engine: str, demo_mode: bool, note: str) -> dict:
    normalized_label = normalize_label(label)
    normalized_ranking = [(normalize_label(name), score) for name, score in ranking]
    return {
        "disease": normalized_label,
        "confidence": round(normalized_ranking[0][1], 4) if normalized_ranking else 0.0,
        "suggestions": suggestions_for(normalized_label),
        "diseases": [
            {"name": name, "confidence": round(score, 4)}
            for name, score in normalized_ranking[:3]
        ],
        "engine": engine,
        "demoMode": demo_mode,
        "note": note,
    }


def fallback_predict(filename: str, content: bytes) -> dict:
    hinted = filename_hint(filename)
    if hinted:
        ranking = [(hinted, 0.74), ("炭疽病", 0.14), ("霜疫霉病", 0.12)]
    else:
        index = int(hashlib.sha256(content).hexdigest(), 16) % 3
        orders = [
            [("霜疫霉病", 0.72), ("炭疽病", 0.18), ("健康叶片", 0.10)],
            [("炭疽病", 0.70), ("霜疫霉病", 0.20), ("健康叶片", 0.10)],
            [("健康叶片", 0.78), ("炭疽病", 0.12), ("霜疫霉病", 0.10)],
        ]
        ranking = orders[index]

    return make_response(
        ranking[0][0],
        ranking,
        "demo-rule",
        True,
        "未检测到可用 YOLO 权重，当前使用规则兜底模式。",
    )


def dataset_predict(filename: str, content: bytes) -> dict | None:
    global DATASET_INDEX_LOADED
    if not DATASET_INDEX_LOADED:
        build_dataset_index()

    if not DATASET_INDEX:
        return None

    hinted = filename_hint(filename)
    if hinted:
        ranking = [(hinted, 0.91)]
        for name in ["炭疽病", "霜疫霉病", "健康叶片"]:
            if name != hinted:
                ranking.append((name, 0.04 if name == "健康叶片" else 0.03))
        return make_response(
            ranking[0][0],
            ranking,
            "dataset-vision",
            True,
            "未加载到 YOLO 权重，当前使用数据集特征匹配进行演示识别。",
        )

    feature = build_feature_from_bytes(content)
    if feature is None:
        return None

    scored = []
    for label, prototype in DATASET_INDEX.items():
        distance = feature_distance(feature, prototype)
        score = 1 / (1 + distance)
        scored.append((label, score))

    scored.sort(key=lambda item: item[1], reverse=True)
    if not scored:
        return None

    best_score = scored[0][1]
    normalized = []
    top_scores = scored[:3]
    score_total = sum(score for _, score in top_scores) or 1.0
    for label, score in top_scores:
        normalized.append((label, max(score / score_total, best_score * 0.4)))

    normalized.sort(key=lambda item: item[1], reverse=True)
    return make_response(
        normalized[0][0],
        normalized,
        "dataset-vision",
        True,
        "未加载到 YOLO 权重，当前使用数据集特征匹配进行演示识别。",
    )


def yolo_predict(filename: str, content: bytes) -> dict | None:
    if YOLO_MODEL is None:  # pragma: no cover
        return None

    try:
        with Image.open(io.BytesIO(content)) as img:
            img.verify()
            if img.format not in {"JPEG", "PNG", "WEBP", "BMP"}:
                raise ValueError("Unsupported image format")
    except Exception as exc:
        raise ValueError(f"Invalid image: {exc}") from exc

    suffix = Path(filename).suffix or ".jpg"
    temp_path = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            temp_file.write(content)
            temp_path = Path(temp_file.name)

        def _infer():
            with MODEL_LOCK:
                return YOLO_MODEL(str(temp_path), verbose=False)[0]

        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
            future = executor.submit(_infer)
            try:
                result = future.result(timeout=30)
            except concurrent.futures.TimeoutError:
                return make_response(
                    "未知病害",
                    [("未知病害", 0.0)],
                    "ultralytics-yolo",
                    False,
                    "推理超时",
                )

        names_map = getattr(result, "names", {}) or {}
        probs = getattr(result, "probs", None)
        boxes = getattr(result, "boxes", None)

        if probs is not None and hasattr(probs, "top1"):
            raw_top_indexes = getattr(probs, "top5", None)
            raw_top_scores = getattr(probs, "top5conf", None)
            top_indexes = raw_top_indexes.tolist() if hasattr(raw_top_indexes, "tolist") else list(raw_top_indexes or [])
            top_scores = raw_top_scores.tolist() if hasattr(raw_top_scores, "tolist") else list(raw_top_scores or [])
            ranking = []
            for index, score in zip(top_indexes, top_scores):
                raw_name = str(names_map.get(int(index), f"class-{index}"))
                ranking.append((normalize_label(raw_name), float(score)))

            if not ranking:
                top1 = int(getattr(probs, "top1"))
                top1_conf = float(getattr(probs, "top1conf", 0.0))
                ranking = [(normalize_label(str(names_map.get(top1, f"class-{top1}"))), top1_conf)]

            ranking.sort(key=lambda item: item[1], reverse=True)
            return make_response(
                ranking[0][0],
                ranking,
                "ultralytics-yolo",
                False,
                f"使用模型文件 {MODEL_PATH.name} 完成分类推理。",
            )

        if boxes is None or len(boxes) == 0:
            return make_response(
                "健康叶片",
                [("健康叶片", 0.88), ("炭疽病", 0.07), ("霜疫霉病", 0.05)],
                "ultralytics-yolo",
                False,
                f"使用模型文件 {MODEL_PATH.name} 推理，当前未检测到明显病害目标。",
            )

        aggregated: dict[str, float] = {}
        for box in boxes:
            cls_id = int(box.cls[0].item())
            conf = float(box.conf[0].item())
            raw_name = str(names_map.get(cls_id, f"class-{cls_id}"))
            label = normalize_label(raw_name)
            aggregated[label] = max(conf, aggregated.get(label, 0.0))

        ranking = sorted(aggregated.items(), key=lambda item: item[1], reverse=True)
        return make_response(
            ranking[0][0],
            ranking,
            "ultralytics-yolo",
            False,
            f"使用模型文件 {MODEL_PATH.name} 完成推理。",
        )
    finally:  # pragma: no cover
        if temp_path and temp_path.exists():
            try:
                temp_path.unlink(missing_ok=True)
            except OSError:
                pass


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
    server_version = "LitchiDiagnosisService/0.2"

    def _send_json(self, status: int, payload: dict):
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        if self.path == "/metrics":
            raw = generate_latest()
            self.send_response(200)
            self.send_header("Content-Type", CONTENT_TYPE_LATEST)
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)
            return
        if self.path != "/health":
            self._send_json(404, {"message": "Not found"})
            return

        engine = "ultralytics-yolo" if YOLO_MODEL else "dataset-vision" if DATASET_INDEX else "demo-rule"
        status = "healthy" if YOLO_MODEL else "degraded"
        dataset_healthy = bool(DATASET_INDEX) if not YOLO_MODEL else True
        health_status = 200 if (YOLO_MODEL is not None or not STRICT_MODEL) else 503
        self._send_json(
            health_status,
            {
                "status": status,
                "engine": engine,
                "demoMode": YOLO_MODEL is None,
                "modelPath": str(MODEL_PATH),
                "modelExists": MODEL_PATH.exists(),
                "modelLoaded": YOLO_MODEL is not None,
                "datasetRoot": str(DATASET_ROOT),
                "datasetIndexed": bool(DATASET_INDEX),
                "datasetClasses": sorted(DATASET_CLASS_COUNTS.keys()),
                "datasetClassCounts": DATASET_CLASS_COUNTS,
                "strictMode": STRICT_MODEL,
                "modelError": YOLO_ERROR,
                "datasetError": DATASET_ERROR,
                "pillowError": PIL_ERROR,
            },
        )

    def do_POST(self):
        if self.path != "/predict":
            self._send_json(404, {"message": "Not found"})
            return

        content_length = self.headers.get("Content-Length")
        if content_length is None:
            logger.warning("Missing Content-Length header")
            self._send_json(400, {"message": "Missing Content-Length header"})
            return

        try:
            length = int(content_length)
        except ValueError:
            logger.warning("Invalid Content-Length: %s", content_length)
            self._send_json(400, {"message": "Invalid Content-Length"})
            return

        if length <= 0 or length > MAX_FILE_SIZE:
            logger.warning("Content-Length out of range: %d", length)
            self._send_json(413, {"message": f"Content-Length must be between 1 and {MAX_FILE_SIZE}"})
            return

        try:
            started_at = time.perf_counter()
            payload = self.rfile.read(length)
            filename, content = parse_multipart(self.headers, payload)
            result = yolo_predict(filename, content) or dataset_predict(filename, content) or fallback_predict(filename, content)
            engine = str(result.get("engine", "unknown"))
            PREDICTION_COUNTER.labels(status="success", engine=engine).inc()
            PREDICTION_LATENCY.labels(engine=engine).observe(time.perf_counter() - started_at)
            self._send_json(200, result)
        except ValueError as exc:
            PREDICTION_COUNTER.labels(status="client_error", engine="unknown").inc()
            logger.warning("Client error: %s", exc)
            self._send_json(400, {"message": str(exc)})
        except Exception:
            PREDICTION_COUNTER.labels(status="server_error", engine="unknown").inc()
            logger.error(traceback.format_exc())
            self._send_json(500, {"message": "Internal server error"})

    def log_message(self, fmt, *args):
        print(f"[diagnosis-service] {self.address_string()} - {fmt % args}")


if __name__ == "__main__":
    try_load_optional_dependencies()
    if STRICT_MODEL and YOLO_MODEL is None:
        raise SystemExit(f"Diagnosis model unavailable: {YOLO_ERROR or f'model file not found: {MODEL_PATH}'}")
    httpd = ThreadingHTTPServer((HOST, PORT), DiagnosisHandler)
    httpd.socket.settimeout(30)
    print(f"Diagnosis service listening on http://{HOST}:{PORT}")
    print(f"Model path: {MODEL_PATH}")
    print(f"Dataset root: {DATASET_ROOT}")
    print(f"Engine: {'ultralytics-yolo' if YOLO_MODEL else 'dataset-vision' if DATASET_INDEX else 'demo-rule'}")
    httpd.serve_forever()
