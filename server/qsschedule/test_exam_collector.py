import json
import tempfile
import unittest
from pathlib import Path
from urllib.error import URLError

import exam_collector as collector


INDEX = {
    "school": '<ul><li><span class="news_title"><a href="/jwc/2026/0921/c7784a295587/page.htm" title="2026年下半年普通话水平测试报名通知">普通话...</a></span><span class="news_meta">2026-09-21</span></li></ul>',
    "neea": '<div><a href="/html1/report/2609/1-1.htm" title="2026年下半年大学英语四、六级考试报名启动">报名</a><a href="/html1/report/2609/1-1.htm" title="2026年下半年大学英语四、六级考试报名启动">重复</a><a href="https://evil.test/html1/report/2609/2-1.htm" title="大学英语考试">外部</a></div>',
    "fujian": '<ul><li><span>08-17</span><a href="http://www.eeafj.cn:80/fkgsgk/20260817/14810.html" title="全国计算机等级考试福建省补报名通告">NCRE</a></li></ul>',
}
BODY = {
    "school": '<span class="arti_update">时间：2026-09-21</span><div class="wp_articlecontent"><p>测试在10—11月，具体日期另行通知。</p><img src="/notice.jpg"><a href="/students.xlsx">报名名单</a></div>',
    "neea": '<span id="ReportIDIssueTime">2026-09-02</span><span id="ReportIDtext"><p>考试在12月12日举行。<script>danger()</script></p></span>',
    "fujian": '<div class="summary">发布时间：2026-08-17</div><div id="Info_Content"><p>福建省补报名通知。</p></div>',
}


class CollectorTest(unittest.TestCase):
    def fake(self, url, validators=None):
        source = next(s for s in collector.SOURCES if url.startswith("https://" + s["url"].split("/")[2]))
        raw = INDEX[source["kind"]] if url == source["url"] else BODY[source["kind"]]
        return raw.encode(), {"etag": "fixture"}, 200

    def collect(self, previous=None, fetcher=None):
        return collector.collect(previous or {}, fetcher or self.fake, pause=lambda _: None)

    def test_sources_and_duplicate_links(self):
        for source in collector.SOURCES:
            entries = collector.parse_index(INDEX[source["kind"]], source)
            self.assertEqual(len(entries), 1)
            self.assertTrue(entries[0]["url"].startswith("https://"))
            self.assertIsNotNone(entries[0]["publishedAt"] if source["kind"] != "neea" else "article supplied")

    def test_titles_strip_markup(self):
        source = collector.SOURCES[1]
        entries = collector.parse_index('<a href="/html1/report/2609/1-1.htm" title="四六级&lt;br&gt;成绩查询">通知</a>', source)
        self.assertEqual(entries[0]["title"], "四六级成绩查询")

    def test_attachments_are_links_not_downloaded_and_dates_not_guessed(self):
        source = collector.SOURCES[0]
        detail = collector.parse_article(BODY["school"], source, source["url"])
        self.assertEqual(detail["publishedAt"], "2026-09-21")
        self.assertEqual([a["type"] for a in detail["assets"]], ["image", "attachment"])
        self.assertTrue(detail["needsReview"])
        self.assertIsNone(detail["examDate"])
        self.assertNotIn("danger", collector.parse_article(BODY["neea"], collector.SOURCES[1], collector.SOURCES[1]["url"])["excerpt"])

    def test_network_and_changed_layout_keep_last_good_records(self):
        previous = self.collect()
        for failure in (lambda *_: (_ for _ in ()).throw(URLError("offline")), lambda *_: (b'<html>new layout</html>', {}, 200)):
            result = self.collect(previous, failure)
            self.assertEqual(result["items"], previous["items"])
            self.assertTrue(collector.public_feed(result)["degraded"])
            self.assertTrue(all(s["state"] == "error" for s in result["sources"]))

    def test_webplus_pdf_player_is_valid_content(self):
        raw = '<div class="wp_articlecontent"><span id="考试安排.pdf" class="wp_pdf_player" pdfsrc="/_upload/article/notice.pdf"></span></div>'
        result = collector.parse_article(raw, collector.SOURCES[0], collector.SOURCES[0]["url"])
        self.assertEqual(result["assets"], [{"name": "考试安排.pdf", "type": "attachment", "url": "https://www.qztc.edu.cn/_upload/article/notice.pdf"}])
        self.assertTrue(result["needsReview"])
        self.assertIsNone(result["examDate"])

    def test_failed_article_keeps_previous_excerpt_and_retryable_status(self):
        previous = self.collect()
        for item in previous["items"]:
            item["checkedAt"] = "2020-01-01T00:00:00Z"
        def fail_articles(url, validators=None):
            if url not in {s["url"] for s in collector.SOURCES}:
                return b'<html>missing body</html>', {}, 200
            return self.fake(url, validators)
        result = self.collect(previous, fail_articles)
        self.assertTrue(all(item["contentState"] == "error" and item["excerpt"] for item in result["items"]))
        self.assertTrue(all(s["state"] == "partial" for s in result["sources"]))
        repaired = self.collect(result)
        self.assertTrue(all(s["state"] == "ok" for s in repaired["sources"]))

    def test_conditional_index_response_and_public_metadata(self):
        previous = self.collect()
        def not_modified(url, validators=None):
            return None, validators, 304
        result = self.collect(previous, not_modified)
        self.assertEqual(result["items"], previous["items"])
        feed = collector.public_feed(result)
        self.assertFalse(feed["degraded"])
        self.assertNotIn("validators", json.dumps(feed))
        self.assertNotIn("discovered", json.dumps(feed))

    def test_explicit_historical_period_filter(self):
        data = {"checkedAt": "2026-10-01T00:00:00Z", "sources": [{"id": "x", "state": "ok", "lastSuccessAt": "2026-10-01T00:00:00Z"}], "items": [
            {"id": "a", "sourceId": "x", "title": "2025年下半年全国计算机等级考试通知", "content": "2025年下半年安排"},
            {"id": "b", "sourceId": "x", "title": "2026年上半年全国计算机等级考试通知", "content": "2026年上半年安排"},
        ]}
        self.assertEqual([item["id"] for item in collector.public_feed(data, "2025-autumn")["items"]], ["a"])
        self.assertEqual([item["id"] for item in collector.public_feed(data, "2026")["items"]], ["b"])
        with self.assertRaises(ValueError):
            collector.public_feed(data, "this-year")

    def test_updates_same_article_replace_in_place(self):
        previous = self.collect()
        for item in previous["items"]:
            item["checkedAt"] = "2020-01-01T00:00:00Z"
        def changed(url, validators=None):
            raw, headers, code = self.fake(url, validators)
            return raw.replace('另行通知'.encode(), '请查看新通知'.encode()), headers, code
        result = self.collect(previous, changed)
        self.assertEqual(len(result["items"]), len(previous["items"]))
        self.assertIn("请查看新通知", next(i for i in result["items"] if i["sourceId"] == "qztc")["excerpt"])

    def test_stale_data_and_atomic_storage(self):
        result = self.collect()
        for source in result["sources"]:
            source["lastSuccessAt"] = "2020-01-01T00:00:00Z"
        self.assertTrue(all(s["stale"] for s in collector.public_feed(result)["sources"]))
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "notices.json"
            collector.atomic_save(path, result)
            self.assertEqual(json.loads(path.read_text(encoding="utf-8")), result)
            self.assertFalse(path.with_suffix(".tmp").exists())

    def test_untrusted_urls_and_empty_articles(self):
        for url in ("https://evil.test/foo", "javascript:alert(1)", "https://cet.neea.edu.cn:8080/foo", "https://user:pass@cet.neea.edu.cn/foo"):
            self.assertIsNone(collector.official_url(collector.SOURCES[1]["url"], url))
        with self.assertRaises(ValueError):
            collector.parse_index('<a href="/">Home</a>', collector.SOURCES[0])
        with self.assertRaises(ValueError):
            collector.parse_article('<div class="wp_articlecontent"></div>', collector.SOURCES[0], collector.SOURCES[0]["url"])


if __name__ == "__main__":
    unittest.main()
