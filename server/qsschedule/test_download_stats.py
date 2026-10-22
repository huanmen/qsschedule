import tempfile
import sqlite3
import unittest
from datetime import datetime, timezone
from pathlib import Path
from download_stats import report


class DownloadStatsTest(unittest.TestCase):
    def test_empty_report_does_not_create_database(self):
        with tempfile.TemporaryDirectory() as root:
            value = report(root, 7)
            self.assertEqual(value['totals']['app']['total'], 0)
            self.assertEqual(len(value['daily']), 7)
            self.assertFalse((Path(root) / 'stats.sqlite').exists())

    def test_historical_requests_and_deduplicated_completion(self):
        with tempfile.TemporaryDirectory() as root:
            with sqlite3.connect(Path(root) / 'stats.sqlite') as db:
                db.execute('CREATE TABLE requests(file TEXT,day TEXT,count INTEGER)')
                db.execute('CREATE TABLE completions(client_hash TEXT,kind TEXT,version INTEGER,day TEXT)')
                db.executemany('INSERT INTO requests VALUES (?,?,?)', [
                    ('old.apk', '2026-10-01', 10), ('new.apk', '2026-10-17', 2),
                    ('content-abc.json', '2026-10-16', 3), ('plugin-data-abc.json', '2026-10-11', 4)])
                db.executemany('INSERT INTO completions VALUES (?,?,?,?)', [('hash', 'app', 14, '2026-10-01'), ('hash', 'app', 15, '2026-10-17')])
            db.close()
            value = report(root, 7, datetime(2026, 10, 17, tzinfo=timezone.utc))
            self.assertEqual(value['totals']['app'], dict(total=12,today=2,last7Days=2,selectedPeriod=2))
            self.assertEqual(value['totals']['plugin']['last7Days'], 4)
            self.assertEqual(value['totals']['resource']['selectedPeriod'], 3)
            self.assertEqual(value['uniqueInstallations'], {'app': 1})
            self.assertEqual(value['daily'][-1]['app'], 2)
            self.assertEqual(len(value['completedDownloads']), 2)
            with self.assertRaises(ValueError):
                report(root, 999)
