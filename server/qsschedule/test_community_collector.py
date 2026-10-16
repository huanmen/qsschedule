import hashlib
import unittest
from datetime import date
from pathlib import Path
from community_collector import SOURCES, collect, day, parse


class CommunityCollectorTest(unittest.TestCase):
    def source(self, adapter):
        return next(s for s in SOURCES if s['adapter'] == adapter)

    def test_month_is_not_an_exact_day_and_past_year_is_historical(self):
        rows = parse(self.source('campus'), {'records': [{
            'id': 'one', 'name': '往期赛事', 'editionYear': 2025,
            'datePrecision': 'month', 'start': '2025-02-01', 'deadline': '2025-02-28',
        }]}, date(2026, 10, 12))
        self.assertTrue(rows[0]['historical'])
        self.assertIsNone(rows[0]['phases'][0]['date'])
        self.assertIn('具体日未公布', rows[0]['phases'][0]['label'])
        self.assertIsNone(day('2026-02-30'))

    def test_ctf_identity_is_compact_stable_and_empty_is_valid(self):
        source = self.source('ctf')
        record = {'name': '示例赛事', 'comp_time_start': '2025-11-01 09:00:00', 'link': 'https://example.org'}
        row = parse(source, {'success': True, 'data': {'result': [record]}})[0]
        key = hashlib.sha256((record['name'] + record['comp_time_start']).encode()).hexdigest()[:16]
        self.assertEqual(source['id'] + ':' + key, row['id'])
        self.assertEqual([], parse(source, {'success': True, 'data': {'result': []}}))

    def test_failures_retain_cache_while_successful_empty_clears_source(self):
        previous = {'items': [
            {'id': 'campus:old', 'sourceId': 'campus'},
            {'id': 'hello-ctf:old', 'sourceId': 'hello-ctf'},
        ]}
        def fetcher(source):
            if source['id'] == 'hello-ctf':
                return {'success': True, 'data': {'result': []}}
            raise OSError('offline')
        payload = collect(previous, fetcher)
        self.assertEqual(['campus:old'], [r['id'] for r in payload['items']])
        statuses = {s['id']: s['status'] for s in payload['sources']}
        self.assertEqual('cached', statuses['campus'])
        self.assertEqual('ok', statuses['hello-ctf'])

    def test_exam_rules_are_estimates_and_all_64_literals_parse(self):
        raw = (Path(__file__).resolve().parents[2] / 'docs/source-review/dsh-index.txt').read_text(encoding='utf-8')
        rows = parse(self.source('exam'), raw, date(2026, 10, 12))
        self.assertEqual(64, len(rows))
        self.assertTrue(all(r['estimated'] and r['phases'][0]['estimated'] for r in rows))
        self.assertTrue(all(r['phases'][0]['date'] >= '2026-10-12' for r in rows))


if __name__ == '__main__':
    unittest.main()
