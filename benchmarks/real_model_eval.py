#!/usr/bin/env python3
"""Real-model retrieval evaluation against the Litchi knowledge base.

Compares two retrievers on the 30 RAG tasks of datasets/evaluation/agent_tasks.jsonl:

- baseline : local bigram-hash embedding stored in backend/target/classes/data/document-state.json (no API needed)
- real     : SiliconFlow BGE-M3 embedding + bge-reranker-v2-m3 rerank (OpenAI-compatible API)

Metrics per retriever: Recall@5 and MRR@5 on evidence-id matching.
The agent (20) and safety (10) tasks are out of scope here: their scoring is
tool-selection / refusal, covered by run_agent_evaluation.py against the backend.

Usage:
    python benchmarks/real_model_eval.py --baseline-only                # local hash baseline, no key
    python benchmarks/real_model_eval.py --api-key sk-xxx              # BGE-M3 + reranker
    python benchmarks/real_model_eval.py                               # reads SILICONFLOW_API_KEY env
Output: reports/validation/20260904-real-model/retrieval-report.jsonl + .md
"""

from __future__ import annotations

import argparse
import json
import math
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOC_STATE = ROOT / "backend" / "target" / "classes" / "data" / "document-state.json"
DATASET = ROOT / "datasets" / "evaluation" / "agent_tasks.jsonl"
CACHE = ROOT / "benchmarks" / ".real_model_cache.json"
OUT_DIR = ROOT / "reports" / "validation" / "20260904-real-model"

EMBED_URL = "https://api.siliconflow.cn/v1/embeddings"
RERANK_URL = "https://api.siliconflow.cn/v1/rerank"
EMBED_MODEL = "BAAI/bge-m3"
RERANK_MODEL = "BAAI/bge-reranker-v2-m3"

TOP_K = 5
RERANK_CANDIDATES = 20


def evidence_of_chunk(chunk: dict) -> str:
    """Map a chunk to its authority evidence id.

    Sources are 'NN_xxx.md' for authority docs -> authority-rag-NN,
    'demo-*' for demo docs -> the demo source itself, others -> source name.
    """
    src = (chunk.get("source") or chunk.get("title") or "")
    name = str(src)
    if name.startswith("demo-"):
        return "demo:" + name
    head = name.split("_", 1)[0]
    if head.isdigit():
        return "authority-rag-" + head
    return "authority-rag"


def load_corpus() -> list[dict]:
    state = json.loads(DOC_STATE.read_text(encoding="utf-8"))
    return state["chunks"]


def load_tasks() -> list[dict]:
    return [json.loads(line) for line in DATASET.read_text(encoding="utf-8").splitlines() if line.strip()]


def call_api(url: str, api_key: str, payload: dict, timeout: float = 60.0) -> dict:
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{url} HTTP {exc.code}: {body[:400]}")


def embed_texts(api_key: str, texts: list[str]) -> list[list[float]]:
    """Batch BGE-M3 embedding. Returns a list of float vectors in input order."""
    vectors: list[list[float]] = []
    batch = 8
    for i in range(0, len(texts), batch):
        chunk = texts[i : i + batch]
        data = call_api(EMBED_URL, api_key, {"model": EMBED_MODEL, "input": chunk, "encoding_format": "float"})
        items = sorted(data["data"], key=lambda d: d["index"])
        vectors.extend(item["embedding"] for item in items)
    return vectors


def rerank(api_key: str, query: str, docs: list[str]) -> list[int]:
    """Rerank candidate docs by relevance to query. Returns doc indices best-first."""
    data = call_api(
        RERANK_URL,
        api_key,
        {"model": RERANK_MODEL, "query": query, "documents": docs, "top_n": len(docs)},
    )
    results = sorted(data["results"], key=lambda r: r["relevance_score"], reverse=True)
    return [r["index"] for r in results]


def cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a)) or 1.0
    nb = math.sqrt(sum(x * x for x in b)) or 1.0
    return dot / (na * nb)


def local_search(chunks: list[dict], query: str) -> list[dict]:
    """Baseline retriever: exact replication of the backend SimpleEmbeddingService
    (Java String.hashCode bigrams -> floorMod 1024 -> L2 normalize), compared against
    the stored `vector` field that the backend itself produced."""
    qvec = simple_embed(query)
    scored = []
    for c in chunks:
        v = c.get("vector")
        if not v:
            continue
        scored.append((cosine(qvec, v), c))
    scored.sort(key=lambda t: t[0], reverse=True)
    return [c for _, c in scored[:TOP_K]]


def java_hash_code(text: str) -> int:
    """Replicate Java String.hashCode() (signed 32-bit)."""
    h = 0
    for ch in text:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    if h >= 0x80000000:
        h -= 0x100000000
    return h


def floor_mod(a: int, n: int) -> int:
    return ((a % n) + n) % n


def simple_embed(text: str) -> list[float]:
    """Replicate SimpleEmbeddingService.embed(): lowercase, collapse ws,
    char bigrams + whole words, hash-bag 1024-dim, L2 normalize."""
    import re
    vec = [0.0] * 1024
    normalized = re.sub(r"[\r\n\t]+", " ", text).lower()
    normalized = re.sub(r"\s+", " ", normalized).strip()
    if not normalized:
        return vec
    compact = normalized.replace(" ", "")
    tokens: list[str] = []
    for i in range(len(compact)):
        end = min(len(compact), i + 2)
        tokens.append(compact[i:end])
    for word in normalized.split(" "):
        if word:
            tokens.append(word)
    for tok in tokens:
        idx = floor_mod(java_hash_code(tok), 1024)
        vec[idx] += 1.0
    norm = math.sqrt(sum(v * v for v in vec))
    if norm > 0:
        vec = [v / norm for v in vec]
    return vec


def real_search(api_key: str, chunks: list[dict], query: str) -> list[dict]:
    # embed query
    (qvec,) = embed_texts(api_key, [query])
    scored = []
    for c in chunks:
        v = c.get("vector_real")
        if not v:
            continue
        scored.append((cosine(qvec, v), c))
    scored.sort(key=lambda t: t[0], reverse=True)
    candidates = [c for _, c in scored[:RERANK_CANDIDATES]]
    docs = [c["content"][:800] for c in candidates]
    order = rerank(api_key, query, docs)
    ordered = [candidates[i] for i in order]
    return ordered[:TOP_K]


def recall_at_k(hits: list[str], expected: list[str]) -> float:
    """1.0 if any expected evidence id appears in hits (top-k evidence set)."""
    return 1.0 if any(e in hits for e in expected) else 0.0


def mrr(hits: list[str], expected: list[str]) -> float:
    for rank, h in enumerate(hits, start=1):
        if h in expected:
            return 1.0 / rank
    return 0.0


def main() -> int:
    parser = argparse.ArgumentParser(description="Real-model retrieval evaluation")
    parser.add_argument("--api-key", default=os.environ.get("SILICONFLOW_API_KEY", ""))
    parser.add_argument("--baseline-only", action="store_true", help="only run local hash baseline (no API key)")
    args = parser.parse_args()

    use_real = bool(args.api_key) and not args.baseline_only
    if args.baseline_only:
        print("mode=baseline (local bigram-hash embedding)")
    elif use_real:
        print(f"mode=real ({EMBED_MODEL} + {RERANK_MODEL})")
    else:
        print("no API key provided and --baseline-only not set; nothing to run")
        print("  baseline: python benchmarks/real_model_eval.py --baseline-only")
        print("  real    : set SILICONFLOW_API_KEY or pass --api-key")
        return 1

    chunks = load_corpus()
    tasks = [t for t in load_tasks() if t.get("category") == "rag"]
    print(f"corpus={len(chunks)} chunks, rag tasks={len(tasks)}")

    # Build real embeddings for the corpus once, cache them.
    if use_real:
        cache = {}
        if CACHE.exists():
            cache = json.loads(CACHE.read_text(encoding="utf-8"))
        need = [c for c in chunks if c["id"] not in cache]
        if need:
            print(f"embedding {len(need)} chunks with BGE-M3 ...")
            texts = [c["content"] for c in need]
            vecs = embed_texts(args.api_key, texts)
            for c, v in zip(need, vecs):
                cache[c["id"]] = v
            CACHE.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")
        for c in chunks:
            c["vector_real"] = cache[c["id"]]

    rows = []
    for t in tasks:
        query = t["query"]
        expected = t.get("evidenceIds") or []
        t0 = time.perf_counter()
        if use_real:
            hits = real_search(args.api_key, chunks, query)
        else:
            hits = local_search(chunks, query)
        dt = (time.perf_counter() - t0) * 1000
        hit_ids = [evidence_of_chunk(c) for c in hits]
        rows.append(
            {
                "id": t["id"],
                "query": query,
                "expected": expected,
                "hitEvidence": hit_ids,
                "recall@5": recall_at_k(hit_ids, expected),
                "mrr@5": mrr(hit_ids, expected),
                "latencyMs": round(dt, 1),
                "mode": "real" if use_real else "baseline",
            }
        )
        print(f"  {t['id']:>9} recall@5={rows[-1]['recall@5']:.0f} mrr@5={rows[-1]['mrr@5']:.2f} {dt:6.0f}ms")

    recall = sum(r["recall@5"] for r in rows) / len(rows)
    mrrv = sum(r["mrr@5"] for r in rows) / len(rows)
    p95 = sorted(r["latencyMs"] for r in rows)[int(len(rows) * 0.95) - 1]
    print(f"\n== {('real' if use_real else 'baseline')} ==")
    print(f"Recall@5 = {recall:.3f} ({int(round(recall*len(rows)))}/{len(rows)})")
    print(f"MRR@5    = {mrrv:.3f}")
    print(f"latency  = avg {sum(r['latencyMs'] for r in rows)/len(rows):.0f}ms, p95 {p95:.0f}ms")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with (OUT_DIR / "retrieval-report.jsonl").open("a", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"appended to {OUT_DIR / 'retrieval-report.jsonl'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
