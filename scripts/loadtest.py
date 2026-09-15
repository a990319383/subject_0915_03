#!/usr/bin/env python3
"""门店底账压测：180 并发分别压工单建档与按工单号检索。

用法:
  python3 scripts/loadtest.py --mode create --concurrency 180 --requests 3600
  python3 scripts/loadtest.py --mode query  --concurrency 180 --requests 18000
"""
import argparse
import json
import random
import statistics
import threading
import time
import urllib.request
import urllib.error

_run_tag = str(int(time.time()))[-8:]


def percentile(sorted_vals, p):
    if not sorted_vals:
        return 0.0
    idx = max(0, min(len(sorted_vals) - 1, int(len(sorted_vals) * p / 100.0 + 0.999) - 1))
    return sorted_vals[idx]


def one_create(base, i):
    order_no = "LT%s%06d" % (_run_tag, i)
    body = {
        "orderNo": order_no,
        "storeCode": "S001",
        "bayCode": "B%02d" % (i % 10 + 1),
        "cardNo": "CARD%06d" % (i % 5000 + 1),
        "plateNo": "沪B%05d" % (i % 100000),
        "items": [
            {"itemName": "外观精洗", "qty": 1, "unitPrice": 30},
            {"itemName": "内饰清洁", "qty": 1, "unitPrice": 15},
        ],
    }
    return _post(base + "/api/wash/orders", body, "REQ-LT%s-%06d" % (_run_tag, i))


def one_query(base, i):
    seq = random.randint(5, 85000)
    return _get(base + "/api/wash/orders/WO%010d" % seq)


def _post(url, body, req_id):
    req = urllib.request.Request(
        url, method="POST", data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json",
                 "X-Request-Id": req_id, "X-Operator": "loadtest"})
    return _execute(req)


def _get(url):
    return _execute(urllib.request.Request(url, method="GET"))


def _execute(req):
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = json.loads(resp.read().decode())
            ok = bool(payload.get("success"))
    except Exception:
        ok = False
    return time.perf_counter() - start, ok


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", choices=["create", "query"], required=True)
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--concurrency", type=int, default=180)
    ap.add_argument("--requests", type=int, default=3600)
    args = ap.parse_args()

    worker = one_create if args.mode == "create" else one_query
    latencies = [0.0] * args.requests
    oks = [False] * args.requests
    idx_lock = threading.Lock()
    next_idx = [0]

    def run():
        while True:
            with idx_lock:
                i = next_idx[0]
                next_idx[0] += 1
            if i >= args.requests:
                return
            lat, ok = worker(args.base, i)
            latencies[i] = lat
            oks[i] = ok

    from concurrent.futures import ThreadPoolExecutor
    wall_start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        list(pool.map(lambda _: run(), range(args.concurrency)))
    wall = time.perf_counter() - wall_start

    ok_count = sum(1 for o in oks if o)
    fail_count = args.requests - ok_count
    lat_sorted = sorted(latencies)
    report = {
        "mode": args.mode,
        "concurrency": args.concurrency,
        "requests": args.requests,
        "success": ok_count,
        "failed": fail_count,
        "wall_seconds": round(wall, 2),
        "qps": round(args.requests / wall, 1),
        "latency_ms": {
            "avg": round(statistics.mean(latencies) * 1000, 1),
            "p50": round(percentile(lat_sorted, 50) * 1000, 1),
            "p95": round(percentile(lat_sorted, 95) * 1000, 1),
            "p99": round(percentile(lat_sorted, 99) * 1000, 1),
            "max": round(lat_sorted[-1] * 1000, 1),
        },
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
