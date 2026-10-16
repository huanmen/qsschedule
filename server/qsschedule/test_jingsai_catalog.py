import unittest
import jingsai_catalog_collector as c


class JingsaiCatalogTests(unittest.TestCase):
    def test_preserves_vague_calendar_dates(self):
        raw = '<div role="grid"><div role="gridcell" aria-label="2026年10月"><a href="/competitions/sample"><span>比赛阶段</span>合成比赛</a></div></div>'.encode()
        node = c.parse_calendar(raw, 2026, 10)[0]['calendarNode']
        self.assertIsNone(node['day'])
        self.assertEqual(10, node['month'])

    def test_reads_faq_pairs_without_merging_questions(self):
        raw = '''<main><h1>合成赛事</h1><section class="competition-article-section"><h2>常见问题</h2><div class="competition-rich-content"><h3>谁能报名？</h3><p>在校学生。</p><h3>何时比赛？</h3><p>时间尚未公布。</p></div></section></main>'''.encode()
        item = c.read_detail({'href': '/competitions/synthetic-test'}, raw)
        self.assertEqual(2, len(item['faqs']))
        self.assertEqual('在校学生。', item['faqs'][0]['answer'])
        self.assertEqual([], item['calendarNodes'])


if __name__ == '__main__':
    unittest.main()
