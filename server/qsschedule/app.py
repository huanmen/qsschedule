"""Anonymous distribution API. Plugin administration uses a separate local-only server."""
import hashlib
import json
import os
import re
import sqlite3
import plugin_store
from exam_collector import public_feed
from contextlib import contextmanager
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, quote, unquote, urlsplit

ROOT = Path(os.environ.get("QSS_DATA", "/var/lib/qsschedule"))
ROOT.mkdir(parents=True, exist_ok=True)


@contextmanager
def database():
    db = sqlite3.connect(ROOT / "stats.sqlite", timeout=10)
    try:
        with db:
            db.execute("PRAGMA journal_mode=WAL")
            db.execute("CREATE TABLE IF NOT EXISTS completions (client_hash TEXT, kind TEXT, version INTEGER, day TEXT, PRIMARY KEY(client_hash,kind,version))")
            db.execute("CREATE TABLE IF NOT EXISTS requests (file TEXT, day TEXT, count INTEGER, PRIMARY KEY(file,day))")
            yield db
    finally:
        db.close()


def completion(data):
    if set(data) != {"installationId", "kind", "version"}:
        raise ValueError("invalid fields")
    client, kind, version = data["installationId"], data["kind"], data["version"]
    if not isinstance(client, str) or not re.fullmatch(r"[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}", client):
        raise ValueError("invalid id")
    allowed_plugin_kinds = {"adskip", "schedule-qa", "exam-schedule", "academic-records"}
    if kind not in ({"app"} | allowed_plugin_kinds) or type(version) is not int or not 1 <= version <= 2147483647:
        raise ValueError("invalid release")
    # Accept only versions actually offered by this server.
    release_file = "update.json" if kind == "app" else ("plugin.json" if kind == "adskip" else kind + ".json")
    if kind != 'app' and plugin_store.managed(ROOT):
        item = next((p for p in plugin_store.catalog(ROOT) if p['id'] == kind), None)
        if item is None:
            raise ValueError('plugin not published')
        release = plugin_store.release(item)
    else:
        release = json.loads((ROOT / release_file).read_text())
    if version != release["latestVersionCode" if kind == "app" else "version"]:
        raise ValueError("unknown release")
    digest = hashlib.sha256((os.environ["QSS_STATS_SALT"] + client).encode()).hexdigest()
    with database() as db:
        cur = db.execute("INSERT OR IGNORE INTO completions VALUES (?,?,?,?)", (digest, kind, version, datetime.now(timezone.utc).date().isoformat()))
        return cur.rowcount == 1


def statistics():
    with database() as db:
        rows = db.execute("SELECT kind,version,COUNT(*) FROM completions GROUP BY kind,version ORDER BY kind,version").fetchall()
        totals = db.execute("SELECT kind,COUNT(DISTINCT client_hash) FROM completions GROUP BY kind").fetchall()
        requests = db.execute("SELECT file,SUM(count) FROM requests GROUP BY file").fetchall()
    return {"uniqueInstallations": dict(totals), "completedDownloads": [{"kind": k, "version": v, "count": c} for k, v, c in rows], "downloadRequests": dict(requests)}


class Handler(BaseHTTPRequestHandler):
    server_version = "QSSchedule"
    def log_message(self, *_):
        pass  # No IP address, request URL, or installation ID in logs.

    def send_json(self, value, code=200, public=False):
        data = json.dumps(value, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        if public:
            self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        parsed = urlsplit(self.path)
        path = parsed.path
        files = {"/api/update": "update.json", "/api/announcements": "announcements.json", "/api/plugins/adskip": "plugin.json", "/api/rules/adskip": "adskip-rules.json",
                 "/api/plugins/schedule-qa": "schedule-qa.json", "/api/plugins/exam-schedule": "exam-schedule.json", "/api/plugins/academic-records": "academic-records.json"}
        if path == "/health":
            return self.send_json({"ok": True})
        if path == "/api/stats":
            return self.send_json(statistics())
        if path in ('/api/plugins', '/api/plugin-rules-catalog'):
            catalog = plugin_store.public_catalog(ROOT)
            if catalog is None:
                return self.send_json({'error': 'catalog unavailable'}, 503)
            if path == '/api/plugin-rules-catalog':
                catalog = dict(schemaVersion=1, plugins=catalog['rulePlugins'])
            return self.send_json(catalog)
        if plugin_store.managed(ROOT):
            items = plugin_store.catalog(ROOT)
            if path.startswith('/api/plugins/'):
                item = next((item for item in items if item['id'] == path.rsplit('/', 1)[-1]), None)
                return self.send_json(plugin_store.release(item)) if item else self.send_json({'error': 'plugin unpublished'}, 404)
            if path == '/api/rules/adskip':
                item = next((item for item in items if item['id'] == 'adskip'), None)
                if item is None:
                    return self.send_json({'error': 'plugin unpublished'}, 404)
                if item['package']:
                    file = ROOT / 'plugin-packages' / (item['package']['sha256'] + '.json')
                    return self.send_json(json.loads(file.read_text(encoding='utf-8')))
            if re.fullmatch(r'/downloads/(?:plugin-data-[0-9a-f]{64}|qsschedule-academic-records-[0-9]+)\.json', path):
                file = plugin_store.downloadable(ROOT, path)
                if not file:
                    return self.send_json({'error': 'plugin unpublished'}, 404)
                try:
                    raw = file.read_bytes()
                    self.send_response(200)
                    self.send_header('Content-Type', 'application/json; charset=utf-8')
                    self.send_header('Content-Length', str(len(raw)))
                    self.send_header('Cache-Control', 'no-store')
                    self.send_header('X-Content-Type-Options', 'nosniff')
                    self.end_headers()
                    if self.command != 'HEAD':
                        self.wfile.write(raw)
                    return
                except (ValueError, OSError):
                    return self.send_json({'error': 'package unavailable'}, 503)
        if path in ("/api/exam-notices", "/api/competition-notices", "/api/official-knowledge", "/api/competition-catalog", "/api/community-schedules"):
            try:
                if path == "/api/community-schedules":
                    feed = json.loads((ROOT / "community-feed.json").read_text(encoding="utf-8"))
                elif path == "/api/competition-catalog":
                    feed = json.loads((ROOT / "jingsai-catalog.json").read_text(encoding="utf-8"))
                elif path == "/api/official-knowledge":
                    feed = json.loads((ROOT / "official-knowledge.json").read_text(encoding="utf-8"))
                else:
                    filename = "competition-notices.json" if path == "/api/competition-notices" else "exam-notices.json"
                    period = parse_qs(parsed.query).get("period", [None])[0]
                    feed = public_feed(json.loads((ROOT / filename).read_text(encoding="utf-8")), period=period)
                return self.send_json(feed, public=True)
            except (OSError, ValueError, TypeError):
                return self.send_json({"error": "notices unavailable"}, 503, public=True)
        if path in files:
            try:
                return self.send_json(json.loads((ROOT / files[path]).read_text(encoding="utf-8")))
            except (OSError, ValueError):
                return self.send_json({"error": "not published"}, 503)
        if re.fullmatch(r"/downloads/qsschedule-academic-records-[0-9]+\.json", path):
            try:
                data = (ROOT / "downloads" / path.rsplit("/", 1)[-1]).read_bytes()
                if len(data) > 4_000_000:
                    return self.send_json({"error": "invalid package"}, 503)
                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(data)))
                self.send_header("X-Content-Type-Options", "nosniff")
                self.end_headers()
                if self.command != "HEAD":
                    self.wfile.write(data)
                return
            except OSError:
                return self.send_json({"error": "not published"}, 503)
        if path.startswith("/downloads/"):
            name = unquote(path.removeprefix("/downloads/"))
            if not re.fullmatch(r"(?:泉师课程表|qsschedule)-[0-9A-Za-z._-]+\.apk", name) or not (ROOT / "downloads" / name).is_file():
                return self.send_json({"error": "not found"}, 404)
            if self.command == "GET" and not self.headers.get("Range"):
                with database() as db:
                    db.execute("INSERT INTO requests VALUES (?,?,1) ON CONFLICT(file,day) DO UPDATE SET count=count+1", (name, datetime.now(timezone.utc).date().isoformat()))
            self.send_response(200)
            self.send_header("X-Accel-Redirect", "/qsschedule-internal/" + quote(name, safe=""))
            self.send_header("Content-Type", "application/vnd.android.package-archive")
            download_name = name.replace("qsschedule-", "泉师课程表-", 1)
            self.send_header("Content-Disposition", "attachment; filename*=UTF-8''" + quote(download_name, safe=""))
            self.end_headers()
            return
        self.send_json({"error": "not found"}, 404)

    def do_POST(self):
        if urlsplit(self.path).path != "/api/download-complete":
            return self.send_json({"error": "not found"}, 404)
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 1024 or self.headers.get("Transfer-Encoding"):
                return self.send_json({"error": "invalid length"}, 413)
            if self.headers.get_content_type() != "application/json":
                return self.send_json({"error": "JSON required"}, 415)
            data = json.loads(self.rfile.read(length))
            if not isinstance(data, dict):
                raise ValueError("invalid body")
            self.send_json({"accepted": True, "new": completion(data)})
        except (ValueError, TypeError, KeyError, OSError):
            self.send_json({"error": "invalid request"}, 400)


if __name__ == "__main__":
    with database():
        pass
    ThreadingHTTPServer(("127.0.0.1", int(os.environ.get("QSS_PORT", "18473"))), Handler).serve_forever()
