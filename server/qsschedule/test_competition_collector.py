import unittest
from urllib.error import URLError
import competition_collector as c


class CompetitionTests(unittest.TestCase):
    def fake(self, url, validators=None):
        raw = '<span class="arti_update">2026-09-05</span><div class="wp_articlecontent">报名时间另行通知。</div>'
        if url.endswith('list.htm'):
            cat = '5501' if '/xkjs/' in url else '5495'
            raw = f'<li><span class="news_title"><a href="/jwc/2026/0905/c{cat}a295250/page.htm" title="2026青年创新创业赛事公告">通知</a></span><span class="news_meta">2026-09-05</span></li>'
        return raw.encode(), {}, 200

    def test_deduplicate_school_categories(self):
        result = c.collect({}, self.fake, lambda _:None)
        self.assertEqual(len(result['items']), 1)
        self.assertEqual(result['items'][0]['id'], 'qztc-article-295250')
        self.assertIsNone(result['items'][0]['examDate'])
        self.assertTrue(all(s['state']=='ok' for s in result['sources']))

    def test_failure_keeps_last_good(self):
        old = c.collect({}, self.fake, lambda _:None)
        result = c.collect(old, lambda *_: (_ for _ in ()).throw(URLError('offline')), lambda _:None)
        self.assertEqual(result['items'], old['items'])
        self.assertTrue(c.common.public_feed(result)['degraded'])

    def test_no_personal_lists_or_external_sources(self):
        raw = '<span class="news_title"><a href="/jwc/2026/0905/c5501a1/page.htm">竞赛获奖名单公示</a></span><span class="news_title"><a href="https://evil.test/jwc/2026/0905/c5501a2/page.htm">创新大赛</a></span>'
        with self.assertRaises(ValueError):
            c.parse_index(raw, c.SOURCES[0])

    def test_stage_sentences_keep_context_without_guessing_year(self):
        raw='<span class="arti_update">2026-09-05</span><div class="wp_articlecontent"><p>校内报名截止：10月20日。<script>bad()</script></p><p>请提交作品。</p></div>'
        detail=c.parse_article(raw,c.SOURCES[0],c.SOURCES[0]['url'])
        self.assertEqual(detail['scheduleNotes'],['校内报名截止：10月20日。'])
        self.assertNotIn('bad()',detail['content'])
        self.assertIsNone(detail['examDate'])

    def test_pagination_and_attachment_metadata(self):
        calls = []
        def paginated(url, validators=None):
            calls.append(url)
            if url.endswith('list.htm'):
                return ('<li><span class="news_title"><a href="/jwc/2026/0905/c5501a295250/page.htm" title="创新创业赛事公告">通知</a></span><span class="news_meta">2026-09-05</span></li>'.encode(), {}, 200)
            if url.endswith('list2.htm'):
                return ('<li><span class="news_title"><a href="/jwc/2025/0702/c5501a286594/page.htm" title="学科竞赛目录通知">目录</a></span><span class="news_meta">2025-07-02</span></li>'.encode(), {}, 200)
            if url.endswith('list3.htm'):
                return (b'<html><body></body></html>', {}, 200)
            return ('<span class="arti_update">2026-09-05</span><div class="wp_articlecontent"><p>报名截止：10月20日。</p><a href="/_upload/article/files/catalog.xlsx">目录附件</a></div>'.encode(), {}, 200)
        result = c.collect({}, paginated, lambda _: None)
        self.assertEqual(result['sources'][0]['pageCount'], 2)
        self.assertEqual(len(result['items']), 2)
        self.assertEqual(result['items'][0]['assets'][0]['url'], 'https://www.qztc.edu.cn/_upload/article/files/catalog.xlsx')
        self.assertTrue(any(url.endswith('/list2.htm') for url in calls))

    def test_notice_categories_are_kept_separate(self):
        self.assertEqual(c.notice_category('关于开展2026年大学生创新创业训练计划项目申报通知'), '创新创业训练')
        self.assertEqual(c.notice_category('关于公布泉州师范学院2026年大学生学科专业竞赛目录的通知'), '认定与综测')
        self.assertEqual(c.notice_category('转发全国大学生数学竞赛报名通知'), '赛事通知')
