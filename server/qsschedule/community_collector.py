"""Public metadata adapters; upstream code is never loaded or executed."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import re
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

BASE = 'https://raw.githubusercontent.com/'
SOURCES = [
    {'id': 'campus', 'name': 'campus-competitions', 'url': BASE+'hhx-gg/campus-competitions/master/update-service/public/v1/package.json', 'home': 'https://github.com/hhx-gg/campus-competitions', 'adapter': 'campus'},
    {'id': 'hello-ctf', 'name': 'Hello-CTFtime · 国内赛事', 'url': BASE+'ProbiusOfficial/Hello-CTFtime/main/CN.json', 'home': 'https://hello-ctf.com/Event/', 'adapter': 'ctf'},
    {'id': 'hello-ctf-history', 'name': 'Hello-CTFtime · 往期', 'url': BASE+'ProbiusOfficial/Hello-CTFtime/main/Achieve/CN_archive.json', 'home': 'https://hello-ctf.com/Event/', 'adapter': 'ctf'},
    {'id': 'exam-reference', 'name': 'dsh-exam-countdown · 参考考期', 'url': BASE+'zimai233/dsh-exam-countdown/master/src/index.js', 'home': 'https://github.com/zimai233/dsh-exam-countdown', 'adapter': 'exam'},
] + [
    {'id': 'radar-'+key, 'name': 'AI 赛事雷达 · '+key, 'url': BASE+'ye4wzp/ai-contest-radar/main/data/sources/'+key+'.json', 'home': 'https://ye4wzp.github.io/ai-contest-radar/', 'adapter': 'radar'}
    for key in ('tianchi', 'datafountain', 'devpost', 'mlcontests')
]


def safe_url(value):
    try:
        parsed = urlsplit(value or '')
        if parsed.scheme == 'https' and parsed.hostname and not parsed.username and not parsed.password:
            return value
    except (TypeError, ValueError):
        pass
    return None


def day(value):
    match = re.search(r'(20\d{2})[-/年.](\d{1,2})[-/月.](\d{1,2})', str(value or ''))
    try:
        return date(*map(int, match.groups())).isoformat() if match else None
    except ValueError:
        return None


def item(source, key, name, kind='competition', **values):
    return {'id': source['id']+':'+str(key), 'name': str(name).strip()[:200], 'kind': kind,
            'sourceId': source['id'], 'sourceName': source['name'], 'sourceUrl': source['home'],
            'estimated': False, 'precision': 'day', **values}


def phase(name, value, precision='day', estimated=False):
    label = str(value or '日期未公布')[:160]
    if precision != 'day' and day(value):
        label = day(value)[:7] + ' 月内 · 具体日未公布'
    return {'name': name, 'date': day(value) if precision == 'day' else None,
            'label': label, 'estimated': estimated}


def parse(source, raw, today=None):
    today = today or date.today()
    adapter = source['adapter']
    if adapter == 'exam':
        # Extract only literal metadata fields, not JS functions or tool code.
        text = raw.decode('utf-8') if isinstance(raw, bytes) else raw
        block = text.split('export const EXAMS = [', 1)[1].split('\n]', 1)[0]
        rows = []
        for literal in re.findall(r'\{([^{}]+)\}', block):
            fields = dict(re.findall(r"(\w+):\s*'([^']*)'", literal))
            numbers = dict(re.findall(r'(\w+):\s*(\d+)', literal))
            if 'id' not in fields or 'm' not in numbers:
                continue
            month = int(numbers['m'])
            def occurrence(year):
                if fields.get('rule'):
                    weekday, ordinal = (5, 2) if fields['rule'] == '2nd-sat' else (6, 1)
                    first = date(year, month, 1)
                    return first + timedelta(days=(weekday-first.weekday()) % 7 + (ordinal-1)*7)
                return date(year, month, int(numbers['d']))
            at = occurrence(today.year)
            if at < today:
                at = occurrence(today.year + 1)
            rows.append(item(source, fields['id'], fields['name'], kind='exam', category=fields.get('cat'),
                estimated=True, note=fields.get('note', ''), phases=[phase('参考考期（非官方公告）', at.isoformat(), estimated=True)],
                officialUrl=None, registrationUrl=None, queryUrl=None))
        if len(rows) != 64:
            raise ValueError('exam literal schema/count changed')
        return rows
    payload = json.loads(raw) if isinstance(raw, (str, bytes)) else raw
    if adapter == 'campus':
        records = payload.get('records')
        if not isinstance(records, list):
            raise ValueError('missing campus records')
        result = []
        for record in records:
            precision = record.get('datePrecision', 'unknown')
            exact = precision in ('day', 'exact') and not record.get('needsReview')
            edition_year=record.get('editionYear')
            result.append(item(source, record['id'], record['name'], organizer=record.get('organizer'), category=record.get('category'),
                year=edition_year, historical=bool(edition_year and int(edition_year) < today.year), officialUrl=safe_url(record.get('source')),
                registrationUrl=safe_url(record.get('registrationUrl')), queryUrl=safe_url(record.get('officialUrl') or record.get('source')),
                precision='day' if exact else precision, note=record.get('dateNote', '具体安排以当届官方通知为准'),
                phases=[phase('报名开始', record.get('start'), 'day' if exact else 'month'),
                        phase('报名截止', record.get('deadline'), 'day' if exact else 'month')]))
        return result
    if adapter == 'radar':
        if not isinstance(payload.get('competitions'), list):
            raise ValueError('missing radar competitions')
        return [item(source, record['id'], record['name'], organizer=record.get('organizer'), category=record.get('type'),
            officialUrl=safe_url(record.get('official_url')), registrationUrl=safe_url(record.get('registration_url') or record.get('official_url')),
            queryUrl=safe_url(record.get('official_url')), signup=record.get('signup'),
            phases=[phase('开始', record.get('start')), phase('报名/提交截止', record.get('deadline')), phase('结束', record.get('end'))])
            for record in payload['competitions']]
    if adapter == 'ctf':
        container = payload.get('data', payload.get('archive'))
        if payload.get('success') is not True or not isinstance(container, dict) or not isinstance(container.get('result'), list):
            raise ValueError('invalid CTF response')
        return [item(source, hashlib.sha256((record['name']+str(record.get('comp_time_start'))).encode()).hexdigest()[:16], record['name'],
            category='CTF', officialUrl=safe_url(record.get('link')), registrationUrl=safe_url(record.get('link')),
            queryUrl=safe_url(record.get('link')), historical=source['id'].endswith('history'), note=record.get('type', ''),
            phases=[phase('报名开始', record.get('reg_time_start')), phase('报名截止', record.get('reg_time_end')),
                    phase('比赛开始', record.get('comp_time_start')), phase('比赛结束', record.get('comp_time_end'))])
            for record in container['result']]
    raise ValueError('unsupported adapter')


def fetch(source):
    with urlopen(Request(source['url'], headers={'User-Agent': 'QSScheduleCommunity/1.0'}), timeout=25) as response:
        if urlsplit(response.url).hostname != 'raw.githubusercontent.com':
            raise ValueError('unexpected source redirect')
        raw = response.read(2_000_001)
        if len(raw) > 2_000_000:
            raise ValueError('source exceeds size limit')
        return raw


def collect(previous=None, fetcher=fetch, today=None):
    previous = previous or {}
    checked = datetime.now(timezone.utc).isoformat(timespec='seconds')
    def one(source):
        try:
            rows = parse(source, fetcher(source), today)
            return rows, {**source, 'checkedAt': checked, 'status': 'ok', 'count': len(rows)}
        except Exception as error:
            cached = [item for item in previous.get('items', []) if item['sourceId'] == source['id']]
            return cached, {**source, 'checkedAt': checked, 'status': 'cached' if cached else 'unavailable', 'count': len(cached), 'error': str(error)[:160]}
    with ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(one, SOURCES))
    rows = {row['id']: row for group, _ in results for row in group}
    return {'schemaVersion': 1, 'checkedAt': checked, 'items': list(rows.values()), 'sources': [status for _, status in results]}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', default=str(Path(os.environ.get('QSS_DATA', Path(__file__).parent)) / 'community-feed.json'))
    args = parser.parse_args()
    target = Path(args.output)
    previous = json.loads(target.read_text(encoding='utf-8')) if target.exists() else {}
    payload = collect(previous)
    temporary = target.with_suffix('.json.tmp')
    temporary.write_text(json.dumps(payload, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
    temporary.replace(target)
    print(json.dumps({'items': len(payload['items']), 'sources': [{key: source.get(key) for key in ('id','status','count')} for source in payload['sources']]}, ensure_ascii=False))
