"""Collect school competition notices, retaining source links and explicit uncertainty."""
import json
import os
import re
from pathlib import Path
from urllib.parse import urlsplit
import exam_collector as common

SOURCES = (
    {'id':'qztc-competitions','name':'泉师教务处 · 学科竞赛','url':'https://www.qztc.edu.cn/jwc/xkjs/list.htm','region':'泉州','kind':'school','maxPages':9},
    {'id':'qztc-innovation','name':'泉师教务处 · 创新创业','url':'https://www.qztc.edu.cn/jwc/cxcy/list.htm','region':'泉州','kind':'school','maxPages':14},
)


def notice_category(title):
    if re.search(r'创新创业训练计划|训练计划', title):
        return '创新创业训练'
    if re.search(r'目录|认定|获奖统计|获奖名单|奖金|综测', title):
        return '认定与综测'
    return '赛事通知'


def parse_index(raw, source):
    from bs4 import BeautifulSoup
    soup = BeautifulSoup(raw, 'html.parser')
    nodes = soup.select('.news_title a[href]')
    if not nodes:
        raise ValueError('school index layout changed')
    entries = {}
    for node in nodes:
        url = common.official_url(source['url'], node['href'])
        match = re.search(r'/jwc/\d{4}/\d{4}/c\d+a(\d+)/page\.htm$', urlsplit(url).path) if url else None
        title = common.clean_title(node.get('title') or node.get_text(' ', strip=True))
        if not match or not title:
            continue
        # Award lists and publicity are links on the source site, not student records to index.
        if re.search(r'名单|公示|获奖情况|竞赛成绩', title):
            continue
        if not re.search(r'竞赛|赛事|大赛|挑战杯|创新创业|训练计划', title):
            continue
        parent = node.find_parent('li')
        meta = parent.select_one('.news_meta') if parent else None
        entries[url] = {'id':'qztc-article-'+match[1], 'title':title, 'url':url, 'category':notice_category(title), 'region':'泉州', 'sourceId':source['id'], 'sourceName':source['name'], 'publishedAt':common.date_text(meta.get_text()) if meta else None}
    if not entries:
        raise ValueError('no recognized competition notices')
    return list(entries.values())[:20]


def parse_article(raw, source, url):
    from bs4 import BeautifulSoup
    detail = common.parse_article(raw, source, url)
    soup = BeautifulSoup(raw, 'html.parser')
    body = soup.select_one('.wp_articlecontent')
    for node in body.select('script, style'):
        node.decompose()
    paragraphs = [re.sub(r'\s+', ' ', x.get_text(' ', strip=True)) for x in body.select('p')]
    paragraphs = [x for x in paragraphs if x]
    text = '\n'.join(paragraphs) or body.get_text(' ', strip=True)
    detail['content'] = text[:16000]
    # Preserve date-bearing sentences, never convert a publication date into a deadline.
    detail['scheduleNotes'] = [x for x in paragraphs if re.search(r'报名|截止|初赛|复赛|决赛|提交|选拔|比赛时间', x) and re.search(r'\d{1,2}月\d{1,2}日|\d{4}[-./]\d{1,2}[-./]\d{1,2}', x)][:12]
    detail['examDate'] = None
    # Keep official attachments as first-class knowledge-base sources. The app
    # can show the article and each attachment without copying private files.
    assets = []
    for node in body.select('img[src], a[href], [pdfsrc], embed[src], object[data], iframe[src]'):
        link = node.get('pdfsrc') or node.get('src') or node.get('data') or node.get('href')
        target = common.official_url(url, link)
        if not target:
            continue
        if node.name == 'img' and '/_ueditor/themes/default/images/' in target:
            continue
        if node.name == 'img' or re.search(r'\.(pdf|docx?|xlsx?|zip)(?:$|\?)', target, re.I):
            assets.append({'type':'image' if node.name == 'img' else 'attachment',
                           'url':target,
                           'name':node.get('alt') or node.get('title') or
                                  (node.get('id') if node.has_attr('pdfsrc') else None) or
                                  node.get_text(' ', strip=True) or '原文附件'})
    detail['assets'] = assets[:40]
    detail['needsReview'] = bool(assets)
    return detail


def _page_url(source, page):
    if page == 1:
        return source['url']
    return source['url'].rsplit('/', 1)[0] + f'/list{page}.htm'


def collect(previous, fetcher=common.fetch, pause=common.time.sleep):
    """Collect every official index page, retaining successful article caches.

    Webplus list pages are finite but do not expose a machine-readable total.
    We stop at the first empty/missing page and keep the last good snapshot if
    a later page fails. This avoids silently shrinking the feed when the site
    changes pagination while still discovering older competition notices.
    """
    checked = common.timestamp()
    old_items = {item['url']: item for item in previous.get('items', [])}
    items = dict(old_items)
    old_sources = {source['id']: source for source in previous.get('sources', [])}
    statuses = []
    for source in SOURCES:
        old = old_sources.get(source['id'], {})
        discovered_by_url = {}
        page_states = []
        article_errors = 0
        index_error = None
        for page in range(1, source.get('maxPages', 1) + 1):
            index_url = _page_url(source, page)
            try:
                raw, validators, code = fetcher(index_url, old.get('pageValidators', {}).get(str(page)))
                if code == 304:
                    page_entries = old.get('pageDiscovered', {}).get(str(page), [])
                else:
                    page_entries = parse_index(raw, source)
                if not page_entries:
                    break
                for entry in page_entries:
                    discovered_by_url[entry['url']] = entry
                page_states.append({'page': page, 'url': index_url, 'state': 'ok', 'httpStatus': code,
                                    'validators': validators, 'discovered': page_entries})
            except (OSError, ValueError, TypeError) as error:
                # Page one is required. Later pages may legitimately be absent.
                if page == 1:
                    index_error = common.error_kind(error)
                break
        if not page_states and index_error:
            statuses.append({**source, 'checkedAt': checked, 'state': 'error', 'error': index_error,
                             'lastSuccessAt': old.get('lastSuccessAt'), 'pageCount': old.get('pageCount', 0),
                             'noticeCount': sum(i.get('sourceId') == source['id'] for i in items.values())})
            continue
        discovered = list(discovered_by_url.values())
        if len(old.get('discovered', [])) >= 8 and len(discovered) < len(old['discovered']) / 2:
            statuses.append({**source, 'checkedAt': checked, 'state': 'error', 'error': 'unexpected_index_shrink',
                             'lastSuccessAt': old.get('lastSuccessAt'), 'pageCount': old.get('pageCount', 0),
                             'noticeCount': sum(i.get('sourceId') == source['id'] for i in items.values())})
            continue
        page_validators = {str(p['page']): p.get('validators', {}) for p in page_states}
        page_discovered = {str(p['page']): p.get('discovered', []) for p in page_states}
        for entry in discovered:
            cached = old_items.get(entry['url'], {})
            if cached.get('contentState') == 'ok' and cached.get('contentHash') and cached.get('title') == entry['title'] and common.age_seconds(cached.get('checkedAt')) < common.ARTICLE_RECHECK_SECONDS:
                # Refresh index-derived labels even when the article body is cached.
                assets = [asset for asset in cached.get('assets', [])
                          if '/_ueditor/themes/default/images/' not in asset.get('url', '')]
                items[entry['url']] = {**cached, **entry, 'sourceId': source['id'], 'assets': assets}
                continue
            try:
                article_raw, article_validators, article_code = fetcher(entry['url'], cached.get('validators'))
                if article_code == 304 and cached.get('contentHash'):
                    item = {**cached, **entry}
                else:
                    item = {**entry, **parse_article(article_raw, source, entry['url'])}
                item.update(checkedAt=checked, fetchedAt=cached.get('fetchedAt') or checked,
                            updatedAt=cached.get('updatedAt') if cached.get('contentHash') == item.get('contentHash') else checked,
                            validators=article_validators, contentState='ok', sourceId=source['id'])
                items[entry['url']] = item
            except (OSError, ValueError, TypeError) as error:
                article_errors += 1
                items[entry['url']] = {**cached, **entry, 'contentState':'error', 'contentError':common.error_kind(error), 'needsReview':True}
            pause(0.25)
        statuses.append({**source, 'checkedAt': checked, 'lastSuccessAt': checked,
                         'state':'partial' if article_errors else 'ok', 'error':None,
                         'pageCount':len(page_states), 'discovered':discovered,
                         'pageValidators':page_validators, 'pageDiscovered':page_discovered,
                         'noticeCount':sum(i.get('sourceId') == source['id'] for i in items.values()),
                         'articleErrors':article_errors})
    result = {'schemaVersion':1, 'checkedAt':checked,
              'lastSuccessAt':max((s.get('lastSuccessAt') for s in statuses if s.get('lastSuccessAt')), default=None),
              'refreshIntervalSeconds':3600, 'sources':statuses,
              'items':sorted(items.values(), key=lambda item:(item.get('publishedAt') or '', item['id']), reverse=True)[:400]}
    # Webplus reuses an article under several category URLs.
    # Webplus reuses an article under several category URLs.
    unique = {}
    for entry in result['items']:
        old = unique.get(entry['id'])
        if old is None or (old.get('contentState') != 'ok' and entry.get('contentState') == 'ok'):
            unique[entry['id']] = entry
    result['items'] = list(unique.values())
    return result


def main():
    root = Path(os.environ.get('QSS_DATA', '/var/lib/qsschedule'))
    path = root / 'competition-notices.json'
    previous = json.loads(path.read_text(encoding='utf-8')) if path.exists() else {}
    result = collect(previous)
    common.atomic_save(path, result)
    print(json.dumps({'items':len(result['items']), 'sources':[{ 'id':s['id'],'state':s['state']} for s in result['sources']]}))
    # A source with a few blocked article bodies still has a valid index and
    # should not make the hourly timer look failed; the API exposes `degraded`
    # and each article keeps its official URL for retry/review.
    return 0 if all(s['state'] in ('ok', 'partial') for s in result['sources']) else 1


if __name__ == '__main__':
    raise SystemExit(main())
