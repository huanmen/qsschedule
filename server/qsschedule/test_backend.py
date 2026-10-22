import importlib
import json
import os
import sqlite3
from pathlib import Path
import tempfile
import unittest
import uuid
import threading
from http.client import HTTPConnection
from http.server import ThreadingHTTPServer


class BackendTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        os.environ["QSS_DATA"] = self.directory.name
        os.environ["QSS_STATS_SALT"] = "test-only"
        import app
        self.app = importlib.reload(app)
        root = Path(self.directory.name)
        (root / "update.json").write_text(json.dumps({"latestVersionCode": 13}))
        (root / "plugin.json").write_text(json.dumps({"version": 2}))
        self.item = {"installationId": str(uuid.uuid4()), "kind": "app", "version": 13}

    def tearDown(self):
        self.directory.cleanup()

    def test_duplicate_completion_counted_once(self):
        self.assertTrue(self.app.completion(self.item))
        self.assertFalse(self.app.completion(self.item))
        self.assertEqual(self.app.statistics()["uniqueInstallations"], {"app": 1})

    def test_download_request_excludes_head_resume_and_missing_files(self):
        root = Path(self.directory.name)
        (root / 'downloads').mkdir()
        (root / 'downloads/qsschedule-1.5.0-release.apk').write_bytes(b'apk')
        server = ThreadingHTTPServer(('127.0.0.1', 0), self.app.Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        client = HTTPConnection('127.0.0.1', server.server_port, timeout=5)
        try:
            for method, path, headers in [
                ('HEAD', '/downloads/qsschedule-1.5.0-release.apk', {}),
                ('GET', '/downloads/qsschedule-1.5.0-release.apk', {'Range':'bytes=1-'}),
                ('GET', '/downloads/qsschedule-1.5.0-release.apk', {}),
                ('GET', '/downloads/qsschedule-missing.apk', {})]:
                client.request(method, path, headers=headers)
                client.getresponse().read()
            self.assertEqual(self.app.statistics()['downloadRequests'], {'qsschedule-1.5.0-release.apk':1})
        finally:
            client.close();server.shutdown();server.server_close();thread.join()

    def test_content_manifest_and_nginx_resume_delivery(self):
        root = Path(self.directory.name)
        (root / 'resources.json').write_text(json.dumps({'version': 1}))
        (root / 'downloads').mkdir()
        name = 'content-' + 'a' * 64 + '.json'
        (root / 'downloads' / name).write_bytes(b'{}')
        server = ThreadingHTTPServer(('127.0.0.1', 0), self.app.Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        connection = HTTPConnection('127.0.0.1', server.server_port, timeout=5)
        try:
            connection.request('GET', '/api/resources')
            response = connection.getresponse()
            self.assertEqual(json.loads(response.read()), {'version': 1})
            connection.request('GET', '/downloads/' + name, headers={'Range': 'bytes=1-'})
            response = connection.getresponse()
            self.assertEqual(response.getheader('X-Accel-Redirect'), '/qsschedule-internal/' + name)
            self.assertEqual(response.status, 200)
            response.read()
            connection.request('GET', '/downloads/content-' + 'b' * 64 + '.json')
            response = connection.getresponse()
            self.assertEqual(response.status, 404)
            response.read()
        finally:
            connection.close(); server.shutdown(); server.server_close(); thread.join()

    def test_optional_academic_plugin_manifest_and_download(self):
        root = Path(self.directory.name)
        (root / "downloads").mkdir()
        payload = b'{"schemaVersion":1,"pluginId":"academic-records"}'
        (root / "downloads/qsschedule-academic-records-1.json").write_bytes(payload)
        (root / "academic-records.json").write_text(json.dumps({"id": "academic-records", "version": 1}))
        server = ThreadingHTTPServer(("127.0.0.1", 0), self.app.Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        connection = HTTPConnection("127.0.0.1", server.server_port, timeout=5)
        try:
            connection.request("GET", "/api/plugins/academic-records")
            response = connection.getresponse()
            self.assertEqual(response.status, 200)
            self.assertEqual(json.loads(response.read())["id"], "academic-records")
            connection.request("GET", "/downloads/qsschedule-academic-records-1.json")
            response = connection.getresponse()
            self.assertEqual(response.status, 200)
            self.assertEqual(response.read(), payload)
            connection.request("HEAD", "/downloads/qsschedule-academic-records-1.json")
            response = connection.getresponse()
            self.assertEqual(int(response.getheader("Content-Length")), len(payload))
            self.assertEqual(response.read(), b"")
            self.assertTrue(self.app.completion(dict(self.item, kind="academic-records", version=1)))
        finally:
            connection.close(); server.shutdown(); server.server_close(); thread.join()

    def test_database_is_closed_after_request_and_rollback(self):
        with self.app.database() as db:
            db.execute("SELECT 1")
        with self.assertRaises(sqlite3.ProgrammingError):
            db.execute("SELECT 1")
        with self.assertRaises(ValueError):
            with self.app.database() as db:
                db.execute("INSERT INTO requests VALUES ('rollback.apk', '2026-10-06', 1)")
                raise ValueError("abort")
        self.assertEqual(self.app.statistics()["downloadRequests"], {})

    def test_kinds_are_separate_and_no_raw_id_is_saved(self):
        self.app.completion(self.item)
        self.app.completion(dict(self.item, kind="adskip", version=2))
        self.assertEqual(self.app.statistics()["uniqueInstallations"], {"app": 1, "adskip": 1})
        with self.app.database() as db:
            value = db.execute("SELECT client_hash FROM completions LIMIT 1").fetchone()[0]
        self.assertNotEqual(value, self.item["installationId"])
        self.assertEqual(len(value), 64)

    def test_unknown_version_and_personal_fields_rejected(self):
        for value in [dict(self.item, version=999), dict(self.item, email="test@example.com"), dict(self.item, installationId="device-imei"), dict(self.item, kind="unknown")]:
            with self.assertRaises(ValueError):
                self.app.completion(value)

    def test_announcement_constraints(self):
        from admin import validate_announcements
        item = {"id": "update-test", "title": "更新", "body": "测试内容", "publishedAt": "2026-10-05T12:00:00Z", "action": "check_update"}
        validate_announcements({"schemaVersion": 1, "items": [item]})
        for entries in [[item, item], [dict(item, action="javascript")], [dict(item, expiresAt="2026-10-04T12:00:00Z")]]:
            with self.assertRaises(ValueError):
                validate_announcements({"schemaVersion": 1, "items": entries})

    def test_exam_feed_http_status_and_public_headers(self):
        server = ThreadingHTTPServer(("127.0.0.1", 0), self.app.Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        connection = HTTPConnection("127.0.0.1", server.server_port, timeout=5)
        try:
            connection.request("GET", "/api/exam-notices")
            response = connection.getresponse()
            self.assertEqual(response.status, 503)
            self.assertEqual(response.getheader("Access-Control-Allow-Origin"), "*")
            response.read()
            (Path(self.directory.name) / "exam-notices.json").write_text(json.dumps({"items": [], "sources": []}))
            connection.request("GET", "/api/exam-notices")
            response = connection.getresponse()
            self.assertEqual(response.status, 200)
            self.assertTrue(json.loads(response.read())["degraded"])
            connection.request("HEAD", "/api/exam-notices")
            response = connection.getresponse()
            self.assertEqual(response.status, 200)
            self.assertEqual(response.read(), b"")
            connection.request("GET", "/health")
            response = connection.getresponse()
            self.assertIsNone(response.getheader("Access-Control-Allow-Origin"))
            response.read()
        finally:
            connection.close()
            server.shutdown()
            server.server_close()
            thread.join()

    def test_unified_knowledge_feed(self):
        root = Path(self.directory.name)
        (root / "official-knowledge.json").write_text(json.dumps({"schemaVersion": 1, "items": [], "sources": [], "degraded": True}))
        server = ThreadingHTTPServer(("127.0.0.1", 0), self.app.Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        connection = HTTPConnection("127.0.0.1", server.server_port, timeout=5)
        try:
            connection.request("GET", "/api/official-knowledge")
            response = connection.getresponse()
            self.assertEqual(response.status, 200)
            self.assertTrue(json.loads(response.read())["degraded"])
            self.assertEqual(response.getheader("Access-Control-Allow-Origin"), "*")
        finally:
            connection.close()
            server.shutdown()
            server.server_close()
            thread.join()

    def test_community_feed_preserves_precision_and_public_access(self):
        payload = {'schemaVersion': 1, 'items': [{'id': 'reference', 'estimated': True, 'phases': [{'date': None, 'label': '月内参考'}]}], 'sources': []}
        (Path(self.directory.name) / 'community-feed.json').write_text(json.dumps(payload), encoding='utf-8')
        server = ThreadingHTTPServer(('127.0.0.1', 0), self.app.Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        connection = HTTPConnection('127.0.0.1', server.server_port, timeout=5)
        try:
            connection.request('GET', '/api/community-schedules')
            response = connection.getresponse()
            self.assertEqual(200, response.status)
            self.assertEqual('*', response.getheader('Access-Control-Allow-Origin'))
            self.assertEqual(payload, json.loads(response.read()))
        finally:
            connection.close()
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == "__main__":
    unittest.main()
