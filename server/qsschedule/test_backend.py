import importlib
import json
import os
import sqlite3
from pathlib import Path
import tempfile
import unittest
import uuid


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


if __name__ == "__main__":
    unittest.main()
