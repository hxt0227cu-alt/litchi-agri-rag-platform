"""并发聊天压测：与 20260731-162446 报告口径一致（100 并发、200 请求、15s 超时）。

用法：python benchmarks/concurrency_chat_test.py [--concurrency 100] [--total 200] [--timeout 15]
输出：成功数/失败数、P50/P95/P99、平均延迟。
"""
import argparse
import json
import threading
import time
import urllib.request

BASE = "http://127.0.0.1:8080"


def login(username="farmer", password="demo123"):
    data = json.dumps({"username": username, "password": password}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/auth/login", data=data, method="POST")
    req.add_header("Content-Type", "application/json; charset=utf-8")
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode("utf-8"))["token"]


QUESTION = "荔枝炭疽病的防治措施有哪些？"
RESULTS = []
RESULTS_LOCK = threading.Lock()


def send_chat(token, timeout):
    data = json.dumps({
        "question": QUESTION,
        "sessionId": "perf-session",
        "useVectorSearch": True,
        "useKnowledgeGraph": True,
    }).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/chats", data=data, method="POST")
    req.add_header("Content-Type", "application/json; charset=utf-8")
    req.add_header("Authorization", "Bearer " + token)
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read()
            ok = resp.status == 200 and b"answer" in body
    except Exception as exc:
        ok = False
    elapsed = (time.perf_counter() - start) * 1000
    with RESULTS_LOCK:
        RESULTS.append((ok, elapsed))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--concurrency", type=int, default=100)
    ap.add_argument("--total", type=int, default=200)
    ap.add_argument("--timeout", type=int, default=15)
    args = ap.parse_args()

    token = login()
    threads = []
    start = time.perf_counter()
    for i in range(args.total):
        while sum(1 for t in threads if t.is_alive()) >= args.concurrency:
            time.sleep(0.01)
        t = threading.Thread(target=send_chat, args=(token, args.timeout), daemon=True)
        t.start()
        threads.append(t)
    for t in threads:
        t.join()
    total_ms = (time.perf_counter() - start) * 1000

    ok = [e for e in RESULTS if e[0]]
    fail = [e for e in RESULTS if not e[0]]
    lats = sorted(e[1] for e in RESULTS)
    def pct(p):
        if not lats:
            return 0.0
        idx = min(len(lats) - 1, int(len(lats) * p))
        return round(lats[idx], 2)

    print(f"total={len(RESULTS)} success={len(ok)} failed={len(fail)} "
          f"success_rate={len(ok)/len(RESULTS)*100:.1f}% wall_time={total_ms/1000:.1f}s")
    print(f"latency avg={sum(lats)/len(lats) if lats else 0:.2f}ms "
          f"p50={pct(0.5)}ms p90={pct(0.9)}ms p95={pct(0.95)}ms p99={pct(0.99)}ms max={max(lats) if lats else 0:.0f}ms")


if __name__ == "__main__":
    main()
