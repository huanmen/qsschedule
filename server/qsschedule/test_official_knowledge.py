import json
import os
import tempfile
import unittest
from pathlib import Path

import official_knowledge_collector as collector


class OfficialKnowledgeTest(unittest.TestCase):
    def test_normalizes_and_deduplicates_public_records(self):
        competition = {
            "checkedAt": "2026-10-09T00:00:00Z",
            "sources": [{"id": "school", "name": "泉师", "region": "泉州", "state": "ok", "pageCount": 9}],
            "items": [{
                "id": "qztc-1", "title": "学科竞赛目录通知", "url": "https://www.qztc.edu.cn/jwc/2026/1/c1a1/page.htm",
                "sourceId": "school", "sourceName": "泉师", "region": "泉州", "category": "认定与综测",
                "assets": [{"type": "attachment", "name": "目录.xlsx", "url": "https://www.qztc.edu.cn/_upload/a.xlsx"}],
            }],
        }
        exams = {
            "checkedAt": "2026-10-09T00:01:00Z",
            "sources": [{"id": "cet", "name": "四六级", "region": "全国", "state": "partial", "articleErrors": 1}],
            "items": [{
                "id": "cet-1", "title": "大学英语四级报名通知", "url": "https://cet.neea.edu.cn/html1/report/1/1.htm",
                "sourceId": "cet", "sourceName": "四六级", "region": "全国",
                "assets": [{"url": "https://evil.test/private.pdf", "name": "坏链接"}],
            }],
        }
        result = collector.build(competition, exams)
        self.assertEqual(len(result["items"]), 2)
        self.assertEqual(result["items"][0]["sourceType"], "exam")
        self.assertEqual(result["items"][1]["category"], "认定与综测")
        self.assertEqual(result["items"][1]["assets"], [{"type": "attachment", "name": "目录.xlsx", "url": "https://www.qztc.edu.cn/_upload/a.xlsx"}])
        self.assertTrue(result["degraded"])

    def test_same_official_url_is_one_record(self):
        item = {"id": "1", "title": "通知", "url": "https://www.qztc.edu.cn/a", "contentState": "ok"}
        result = collector.build({"items": [item], "sources": []}, {"items": [{**item, "id": "2"}], "sources": []})
        self.assertEqual(len(result["items"]), 1)

    def test_atomic_publish_and_no_personal_fields(self):
        with tempfile.TemporaryDirectory() as directory:
            old = os.environ.get("QSS_DATA")
            os.environ["QSS_DATA"] = directory
            try:
                collector.ROOT = Path(directory)
                (Path(directory) / "competition-notices.json").write_text(json.dumps({"items": [], "sources": [{"id": "c", "state": "ok"}]}), encoding="utf-8")
                (Path(directory) / "exam-notices.json").write_text(json.dumps({"items": [], "sources": [{"id": "e", "state": "ok"}]}), encoding="utf-8")
                collector.main()
                data = json.loads((Path(directory) / "official-knowledge.json").read_text(encoding="utf-8"))
                self.assertNotIn("username", json.dumps(data))
                self.assertNotIn("password", json.dumps(data))
            finally:
                if old is None:
                    os.environ.pop("QSS_DATA", None)
                else:
                    os.environ["QSS_DATA"] = old


if __name__ == "__main__":
    unittest.main()
