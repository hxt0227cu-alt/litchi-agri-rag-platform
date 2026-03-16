from __future__ import annotations

import argparse
import csv
import hashlib
import html
import re
import unicodedata
import zlib
from dataclasses import dataclass
from html.parser import HTMLParser
from pathlib import Path

try:
    from pypdf import PdfReader
except Exception:  # pragma: no cover - optional dependency at runtime
    PdfReader = None

try:
    import fitz
except Exception:  # pragma: no cover - optional dependency at runtime
    fitz = None

try:
    import numpy as np
except Exception:  # pragma: no cover - optional dependency at runtime
    np = None

try:
    from rapidocr_onnxruntime import RapidOCR
except Exception:  # pragma: no cover - optional dependency at runtime
    RapidOCR = None


ROOT = Path(__file__).resolve().parents[1]
RAW_DIR = ROOT / "datasets" / "knowledge" / "raw"
CLEAN_DIR = ROOT / "datasets" / "knowledge" / "cleaned"
REPORT_PATH = CLEAN_DIR / "cleaning-report.csv"
PDF_HTML_PREFIXES = (b"<!DOCTYPE html", b"<html", b"<?xml")
OCR_MAX_PAGES = 30

BLOCK_TAGS = {
    "address",
    "article",
    "aside",
    "blockquote",
    "br",
    "caption",
    "dd",
    "div",
    "dl",
    "dt",
    "figcaption",
    "figure",
    "footer",
    "form",
    "h1",
    "h2",
    "h3",
    "h4",
    "header",
    "hr",
    "li",
    "main",
    "nav",
    "p",
    "section",
    "table",
    "td",
    "th",
    "tr",
    "ul",
}
SKIP_TAGS = {"script", "style", "noscript", "svg"}
DROP_LINE_PATTERNS = [
    re.compile(pattern)
    for pattern in [
        r"^关闭$",
        r"^我要投稿$",
        r"^网站首页$",
        r"^当前位置[:：]?",
        r"^上一篇",
        r"^下一篇",
        r"^打印本页",
        r"^责任编辑[:：]?",
        r"^分享到[:：]?",
        r"^点击次数[:：]?\d+",
        r"^中国农垦（热作）网相关栏目",
        r"^投稿邮箱[:：]?",
        r"^联系电话[:：]?",
        r"^编辑部电话[:：]?",
        r"^\d{4}年\d{1,2}月\d{1,2}日",
        r"^无障碍浏览\|?",
        r"^信息员登录\|?",
        r"^智能问答\|?",
        r"^新媒体矩阵$",
        r"^首页$",
        r"^新闻动态$",
        r"^政府信息公开$",
        r"^解读回应$",
        r"^网上办事$",
        r"^公共服务$",
        r"^互动交流$",
        r"^访问统计 \| 站点地图$",
        r"^保存$",
        r"^复制$",
        r"^打印$",
        r"^字号[:：]",
        r"^背景[:：]",
        r"^阅读[:：]?$",
        r"^作者[:：]?$",
        r"^日期[:：]",
        r"^来源[:：]",
        r"^发布员[:：]",
        r"^审核员[:：]",
    ]
]
LITERAL_TEXT_PATTERN = re.compile(r"\(([^()]*)\)\s*Tj")
TEXT_ARRAY_PATTERN = re.compile(r"\[(.*?)]\s*TJ", re.S)
LITERAL_ITEM_PATTERN = re.compile(r"\(([^()]*)\)")
HEX_ITEM_PATTERN = re.compile(r"<([0-9A-Fa-f]+)>")
STREAM_PATTERN = re.compile(rb"stream\r?\n(.*?)\r?\nendstream", re.S)
META_PATTERN = re.compile(
    r"""<meta[^>]+(?:name|property)=["'](?P<name>[^"']+)["'][^>]+content=["'](?P<content>[^"']*)["'][^>]*>|<meta[^>]+content=["'](?P<content2>[^"']*)["'][^>]+(?:name|property)=["'](?P<name2>[^"']+)["'][^>]*>""",
    re.I,
)


@dataclass
class CleanResult:
    source_file: str
    extension: str
    status: str
    title: str
    chars: int
    output_file: str
    notes: str = ""


class ArticleHTMLParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.parts: list[str] = []
        self.skip_depth = 0
        self.page_title: list[str] = []
        self.headings: list[str] = []
        self.current_heading: list[str] = []
        self.in_title = False
        self.heading_depth = 0

    def handle_starttag(self, tag: str, attrs) -> None:
        if tag in SKIP_TAGS:
            self.skip_depth += 1
            return
        if tag == "title":
            self.in_title = True
        if tag in {"h1", "h2"}:
            self.heading_depth += 1
        if tag in BLOCK_TAGS:
            self.parts.append("\n")

    def handle_endtag(self, tag: str) -> None:
        if tag in SKIP_TAGS and self.skip_depth > 0:
            self.skip_depth -= 1
            return
        if tag == "title":
            self.in_title = False
        if tag in {"h1", "h2"} and self.heading_depth > 0:
            self.heading_depth -= 1
            if self.heading_depth == 0 and self.current_heading:
                heading = normalize_title("".join(self.current_heading), "")
                if heading:
                    self.headings.append(heading)
                self.current_heading.clear()
        if tag in BLOCK_TAGS:
            self.parts.append("\n")

    def handle_data(self, data: str) -> None:
        if self.skip_depth > 0:
            return
        if self.in_title:
            self.page_title.append(data)
        if self.heading_depth > 0:
            self.current_heading.append(data)
        self.parts.append(data)


def normalize_title(value: str | None, fallback: str) -> str:
    text = (value or "").strip()
    text = re.sub(r"\s+", " ", html.unescape(text))
    text = text.replace("_", " ").strip(" -|")
    return text or fallback


def should_drop_line(line: str) -> bool:
    if not line:
        return True
    if line in {"•", "·", "|"}:
        return True
    return any(pattern.search(line) for pattern in DROP_LINE_PATTERNS)


def dedupe_preserve_order(lines: list[str]) -> list[str]:
    cleaned: list[str] = []
    previous = None
    for line in lines:
        if line == previous:
            continue
        cleaned.append(line)
        previous = line
    return cleaned


def collapse_blank_lines(lines: list[str]) -> list[str]:
    output: list[str] = []
    blank = False
    for line in lines:
        if not line:
            if not blank and output:
                output.append("")
            blank = True
            continue
        output.append(line)
        blank = False
    while output and output[-1] == "":
        output.pop()
    return output


def normalize_text(text: str) -> str:
    text = html.unescape(text).replace("\xa0", " ")
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    lines = [re.sub(r"\s+", " ", line).strip() for line in text.splitlines()]
    lines = [line for line in lines if not should_drop_line(line)]
    lines = dedupe_preserve_order(lines)
    lines = collapse_blank_lines(lines)
    return "\n".join(lines).strip()


def extract_html_text(path: Path) -> tuple[str, str]:
    parser = ArticleHTMLParser()
    source = path.read_text(encoding="utf-8", errors="ignore")
    parser.feed(source)
    raw_text = "".join(parser.parts)
    fallback_title = normalize_title(path.stem.split("_")[0], path.stem)
    title = normalize_title((parser.headings[0] if parser.headings else "") or "".join(parser.page_title), fallback_title)
    if re.search(r"(材料和方法|结果与分析|讨\s*论|结论)", title):
        title = fallback_title
    normalized = normalize_text(raw_text)
    lines = [line for line in normalized.splitlines() if line.strip()]

    if title:
        for index, line in enumerate(lines):
            if title in line:
                lines = lines[index:]
                break

    if lines:
        body = lines[1:]
        content_start = 0
        for index, line in enumerate(body):
            if (
                line.startswith(("摘要", "简介：", "简介:", "一、", "二、", "三、", "四、", "五、"))
                or re.match(r"^\d+([．.、]\d+)*[．.、]?\s*", line)
                or len(line) >= 25
            ):
                content_start = index
                break
        body = body[content_start:]

        stop_index = len(body)
        for index, line in enumerate(body):
            if re.match(r"^(发布员|审核员|主办|承办|地址|桂ICP备|桂公网安备|注：本网)", line):
                stop_index = index
                break
        body = [line for line in body[:stop_index] if not is_html_nav_noise(line)]
        lines = [title, "", *body] if title else body

    text = "\n".join(lines).strip()
    if len(text) < 300:
        meta_text = extract_html_meta_summary(source, title or fallback_title)
        if len(meta_text) > len(text):
            text = meta_text

    return title, text


def is_html_nav_noise(line: str) -> bool:
    if re.match(
        r"^(首页|新闻动态|政府信息公开|解读回应|网上办事|公共服务|互动交流|农垦中心|中心简介|中心领导|机构设置|中心风采|综合服务|联系我们|工作动态|垦区新闻|热作新闻|一线来风|媒体报道|通知公告|重要通知|标准公告|信息公开|政策与改革|政策法规|农垦情况|理论探讨|形势分析|调查研究|农垦经济|现代农业|产业经济|对外合作|网站标识码：4500000028)$",
        line,
    ):
        return True
    if len(line) <= 6 and not re.match(r"^[一二三四五六七八九十]+、", line) and not re.search(r"\d", line):
        return True
    return False


def extract_html_meta_summary(source: str, fallback_title: str) -> str:
    meta: dict[str, str] = {}
    for match in META_PATTERN.finditer(source):
        name = (match.group("name") or match.group("name2") or "").strip().lower()
        content = html.unescape(match.group("content") or match.group("content2") or "").strip()
        if name and content and name not in meta:
            meta[name] = content

    title = normalize_title(
        meta.get("citation_title")
        or meta.get("dc.title")
        or meta.get("title")
        or fallback_title,
        fallback_title,
    )

    fields = [title]
    author = meta.get("citation_authors") or meta.get("citation_author") or meta.get("dc.creator") or meta.get("dc.contributor")
    if author:
        fields.append(f"作者：{author}")

    journal = meta.get("citation_journal_title") or meta.get("dc.source")
    if journal:
        fields.append(f"来源：{journal}")

    date = meta.get("citation_date") or meta.get("dc.date")
    if date:
        fields.append(f"日期：{date}")

    keywords = meta.get("citation_keywords") or meta.get("dc.keywords")
    if keywords:
        fields.append(f"关键词：{keywords.strip(' ,')}")

    description = meta.get("dc.description") or meta.get("description")
    if description:
        fields.append("")
        fields.append(description)

    return normalize_text("\n".join(fields))


def decode_pdf_literal(value: str) -> str:
    return (
        value.replace(r"\(", "(")
        .replace(r"\)", ")")
        .replace(r"\n", " ")
        .replace(r"\r", " ")
        .replace(r"\t", " ")
        .replace(r"\\", "\\")
    )


def decode_pdf_hex(value: str) -> str:
    try:
        raw = bytes.fromhex(value)
    except ValueError:
        return ""

    for encoding in ("utf-16-be", "utf-8", "gb18030", "latin1"):
        try:
            text = raw.decode(encoding)
            if text.strip():
                return text
        except UnicodeDecodeError:
            continue
    return ""


def extract_pdf_text_with_pypdf(path: Path) -> tuple[str, str]:
    if PdfReader is None:
        return "", ""

    try:
        reader = PdfReader(str(path))
    except Exception:
        return "", ""

    pages: list[str] = []
    for page in reader.pages:
        try:
            pages.append(page.extract_text() or "")
        except Exception:
            pages.append("")

    text = normalize_text("\n\n".join(pages))
    title = ""
    if reader.metadata and getattr(reader.metadata, "title", None):
        title = normalize_title(reader.metadata.title, "")
    title = select_pdf_title(text, title, path.stem)
    return title, text


def iter_pdf_candidates(pdf_bytes: bytes) -> list[str]:
    candidates: list[str] = [pdf_bytes.decode("latin1", "ignore")]
    for match in STREAM_PATTERN.finditer(pdf_bytes):
        stream = match.group(1).lstrip(b"\r\n")
        for wbits in (zlib.MAX_WBITS, -zlib.MAX_WBITS):
            try:
                decoded = zlib.decompress(stream, wbits)
            except zlib.error:
                continue
            text = decoded.decode("latin1", "ignore")
            if text:
                candidates.append(text)
            break
    return candidates


def extract_pdf_text(path: Path) -> tuple[str, str]:
    if is_html_disguised_as_pdf(path):
        return extract_html_text(path)

    pypdf_title, pypdf_text = extract_pdf_text_with_pypdf(path)
    if looks_like_good_pdf_text(pypdf_text):
        return select_pdf_title(pypdf_text, pypdf_title, path.stem), pypdf_text

    ocr_text = extract_pdf_text_with_ocr(path)
    if looks_like_good_pdf_text(ocr_text):
        return select_pdf_title(ocr_text, pypdf_title, path.stem), ocr_text

    pdf_bytes = path.read_bytes()
    fragments: list[str] = []
    seen = set()

    for candidate in iter_pdf_candidates(pdf_bytes):
        candidate_hash = hashlib.sha1(candidate.encode("latin1", "ignore")).hexdigest()
        if candidate_hash in seen:
            continue
        seen.add(candidate_hash)

        for match in LITERAL_TEXT_PATTERN.finditer(candidate):
            fragments.append(decode_pdf_literal(match.group(1)))

        for array_match in TEXT_ARRAY_PATTERN.finditer(candidate):
            array_content = array_match.group(1)
            for item_match in LITERAL_ITEM_PATTERN.finditer(array_content):
                fragments.append(decode_pdf_literal(item_match.group(1)))
            for item_match in HEX_ITEM_PATTERN.finditer(array_content):
                text = decode_pdf_hex(item_match.group(1))
                if text:
                    fragments.append(text)

    text = normalize_text("\n".join(fragment for fragment in fragments if fragment.strip()))
    title = select_pdf_title(text, "", path.stem)
    return title, text


def looks_like_good_pdf_text(text: str) -> bool:
    if not text or len(text) < 120:
        return False

    printable = sum(1 for ch in text if ch.isprintable() or ch in "\n\t")
    if printable / max(len(text), 1) < 0.95:
        return False

    control = sum(1 for ch in text if unicodedata.category(ch).startswith("C") and ch not in "\n\t")
    if control / max(len(text), 1) > 0.01:
        return False

    chinese = sum(1 for ch in text if "\u4e00" <= ch <= "\u9fff")
    ascii_letters = sum(1 for ch in text if "a" <= ch.lower() <= "z")
    digits = sum(1 for ch in text if ch.isdigit())
    signal = chinese + ascii_letters + digits
    if signal / max(len(text), 1) < 0.4:
        return False

    return True


def extract_pdf_text_with_ocr(path: Path) -> str:
    if fitz is None or np is None or RapidOCR is None:
        return ""

    engine = RapidOCR()
    pages_text: list[str] = []
    try:
        doc = fitz.open(path)
    except Exception:
        return ""

    try:
        for page_index in range(min(len(doc), OCR_MAX_PAGES)):
            page = doc.load_page(page_index)
            pix = page.get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
            channels = 1 if pix.n == 1 else min(pix.n, 3)
            image = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, pix.n)
            if channels != pix.n:
                image = image[:, :, :channels]
            result, _ = engine(image)
            if not result:
                continue
            lines = [item[1] for item in result if item and len(item) >= 2 and item[1]]
            if lines:
                pages_text.append("\n".join(lines))
    finally:
        doc.close()

    return normalize_text("\n\n".join(pages_text))


def select_pdf_title(text: str, metadata_title: str, fallback: str) -> str:
    fallback_title = normalize_title(fallback, fallback)
    title = normalize_title(metadata_title, "")
    if title and is_good_pdf_title(title):
        return title

    candidates = [line.strip() for line in text.splitlines()[:20] if line.strip()]
    best = ""
    best_score = -1
    for line in candidates:
        normalized = normalize_title(line, "")
        if not normalized:
            continue
        score = score_pdf_title_candidate(normalized)
        if score > best_score:
            best = normalized
            best_score = score

    return best if best and is_good_pdf_title(best) else fallback_title


def score_pdf_title_candidate(line: str) -> int:
    score = 0
    if "荔枝" in line or "litchi" in line.lower():
        score += 6
    if 8 <= len(line) <= 45:
        score += 3
    if re.search(r"(病|虫|害|品种|防治|检测|研究|技术|规范|选育|预测|发生)", line):
        score += 4
    if re.match(r"^(doi|DOI|20\d{2}|第?\d)", line):
        score -= 4
    if re.search(r"(学报|期刊|卷|期|abstract|关键词|中图分类号)", line.lower()):
        score -= 3
    if len(line) <= 2:
        score -= 6
    return score


def is_good_pdf_title(line: str) -> bool:
    if len(line) <= 2:
        return False
    if re.match(r"^(doi|DOI|20\d{2}|第?\d)", line):
        return False
    if line in {"书", "第", "团体标准"}:
        return False
    return True


def is_html_disguised_as_pdf(path: Path) -> bool:
    head = path.read_bytes()[:256].lstrip()
    return any(head.startswith(prefix) for prefix in PDF_HTML_PREFIXES)


def pdf_failure_note(path: Path) -> str:
    head = path.read_bytes()[:4096]
    if is_html_disguised_as_pdf(path):
        return "File content is HTML, not a real PDF. Re-saved via HTML parser or re-download recommended."
    if b"/Subtype /Image" in head or b"/Filter /JBIG2Decode" in head:
        return "Likely scanned/image-based PDF. OCR is recommended."
    return "No usable text extracted. Re-download or OCR may be needed."


def extract_plain_text(path: Path) -> tuple[str, str]:
    title = normalize_title(path.stem, path.stem)
    text = normalize_text(path.read_text(encoding="utf-8", errors="ignore"))
    return title, text


def clean_file(path: Path, include_pdf: bool) -> CleanResult:
    ext = path.suffix.lower()
    title = normalize_title(path.stem, path.stem)
    output_path = CLEAN_DIR / f"{path.stem}.md"

    try:
        if ext in {".html", ".htm"}:
            title, text = extract_html_text(path)
            status = "cleaned_html"
        elif ext in {".txt", ".md"}:
            title, text = extract_plain_text(path)
            status = "cleaned_text"
        elif ext == ".pdf":
            if not include_pdf:
                output_path.unlink(missing_ok=True)
                return CleanResult(path.name, ext, "skipped_pdf", title, 0, "", "Run with --include-pdf to process PDFs.")
            title, text = extract_pdf_text(path)
            status = "cleaned_pdf" if looks_like_good_pdf_text(text) else "needs_ocr" if text else "needs_ocr"
        else:
            output_path.unlink(missing_ok=True)
            return CleanResult(path.name, ext, "skipped_unsupported", title, 0, "")
    except Exception as exc:
        output_path.unlink(missing_ok=True)
        return CleanResult(path.name, ext, "failed_error", title, 0, "", str(exc))

    if not text:
        output_path.unlink(missing_ok=True)
        if ext == ".pdf":
            return CleanResult(path.name, ext, "needs_ocr", title, 0, "", pdf_failure_note(path))
        return CleanResult(path.name, ext, "failed_no_text", title, 0, "")

    if status == "needs_ocr":
        output_path.unlink(missing_ok=True)
        return CleanResult(path.name, ext, "needs_ocr", title, len(text), "", pdf_failure_note(path))

    content = f"# {title}\n\n来源文件：{path.name}\n\n{text}\n"
    output_path.write_text(content, encoding="utf-8")
    return CleanResult(path.name, ext, status, title, len(text), output_path.name)


def write_report(results: list[CleanResult]) -> None:
    with REPORT_PATH.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["source_file", "extension", "status", "title", "chars", "output_file", "notes"])
        for result in results:
            writer.writerow(
                [
                    result.source_file,
                    result.extension,
                    result.status,
                    result.title,
                    result.chars,
                    result.output_file,
                    result.notes,
                ]
            )


def main() -> None:
    parser = argparse.ArgumentParser(description="Clean collected knowledge documents into Markdown.")
    parser.add_argument("--include-pdf", action="store_true", help="Attempt to extract text from PDF files.")
    args = parser.parse_args()

    CLEAN_DIR.mkdir(parents=True, exist_ok=True)
    raw_files = [path for path in RAW_DIR.iterdir() if path.is_file() and path.name != ".gitkeep"]
    results = [clean_file(path, include_pdf=args.include_pdf) for path in sorted(raw_files, key=lambda item: item.name.lower())]
    write_report(results)

    cleaned = sum(1 for item in results if item.status.startswith("cleaned_"))
    failed = sum(1 for item in results if item.status.startswith("failed"))
    skipped = sum(1 for item in results if item.status.startswith("skipped"))
    print(f"processed={len(results)} cleaned={cleaned} failed={failed} skipped={skipped}")
    for item in results:
        print(f"{item.status:18} {item.source_file}")


if __name__ == "__main__":
    main()
