#!/usr/bin/env python3
"""Forwards Blip packets between devices that are not in radio range.

The relay does not open the packets. Direct messages stay sealed.
The public channel is readable by anyone on this path, including this process.
Place rooms are dropped by the app before they are sent.
"""
import json
import threading
import time
from collections import defaultdict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

PORT = 8791
TTL = 25
MAX_QUEUE = 200
lock = threading.Lock()
last = {}
queues = defaultdict(list)


def prune(now):
    dead = [peer for peer, (_, seen) in last.items() if now - seen > TTL]
    for peer in dead:
        last.pop(peer, None)
        queues.pop(peer, None)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        return

    def answer(self, code, payload):
        raw = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def body(self):
        size = int(self.headers.get("Content-Length", "0") or 0)
        if size > 400_000:
            return None
        try:
            return json.loads(self.rfile.read(size) or b"{}")
        except json.JSONDecodeError:
            return None

    def do_POST(self):
        data = self.body()
        if not isinstance(data, dict):
            self.answer(400, {"error": "bad"})
            return
        now = time.time()
        path = urlparse(self.path).path
        with lock:
            prune(now)
            if path == "/v1/hello":
                peer = str(data.get("id") or "")
                if len(peer) < 8:
                    self.answer(400, {"error": "id"})
                    return
                last[peer] = (str(data.get("name") or "")[:80], now)
                self.answer(200, {"ok": True})
                return
            if path == "/v1/push":
                source = str(data.get("id") or "")
                target = str(data.get("to") or "")
                packet = data.get("packet")
                if not source or not isinstance(packet, str) or len(packet) > 300_000:
                    self.answer(400, {"error": "packet"})
                    return
                name = last.get(source, ("", now))[0]
                last[source] = (name, now)
                if target:
                    queues[target].append(packet)
                    if len(queues[target]) > MAX_QUEUE:
                        del queues[target][: len(queues[target]) - MAX_QUEUE]
                self.answer(200, {"ok": True})
                return
        self.answer(404, {"error": "no"})

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path != "/v1/pull":
            self.answer(404, {"error": "no"})
            return
        peer = parse_qs(parsed.query).get("id", [""])[0]
        now = time.time()
        with lock:
            prune(now)
            if peer in last:
                last[peer] = (last[peer][0], now)
            packets = queues.pop(peer, [])
            peers = [
                {"id": other, "name": name}
                for other, (name, _) in last.items()
                if other != peer
            ]
        self.answer(200, {"peers": peers, "packets": packets})


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
