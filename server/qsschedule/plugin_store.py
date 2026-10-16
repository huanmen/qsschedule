"""Transactional plugin drafts, published snapshots and immutable JSON packages."""
import hashlib
import json
import re
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

BUILTINS = {
    'adskip': ('广告辅助', '自动跳过广告', 'builtin', 2),
    'academic-records': ('赛事与考证', '公开赛事资料、考证日程与关注更新', 'academic-records', 1),
}
MAX_PACKAGE = 4_000_000
RETIRED_IDS = {'campus-vpn'}


class Conflict(ValueError):
    pass


@contextmanager
def connection(root):
    db = sqlite3.connect(Path(root) / 'plugins.sqlite', timeout=10)
    db.row_factory = sqlite3.Row
    try:
        with db:
            yield db
    finally:
        db.close()


def now():
    return datetime.now(timezone.utc).isoformat(timespec='seconds')


def initialize(root, public_base):
    Path(root).mkdir(parents=True, exist_ok=True)
    with connection(root) as db:
        db.execute('PRAGMA journal_mode=WAL')
        db.execute('CREATE TABLE IF NOT EXISTS plugins (id TEXT PRIMARY KEY, draft TEXT NOT NULL, published TEXT, status TEXT NOT NULL, revision INTEGER NOT NULL, updated TEXT NOT NULL)')
        db.execute('CREATE TABLE IF NOT EXISTS audit (seq INTEGER PRIMARY KEY, plugin TEXT, action TEXT, at TEXT, snapshot TEXT)')
        db.execute('CREATE TABLE IF NOT EXISTS releases (id TEXT, version INTEGER, sha256 TEXT, PRIMARY KEY(id,version))')
        for id in RETIRED_IDS:
            row = db.execute('SELECT draft,status FROM plugins WHERE id=?', (id,)).fetchone()
            if row and row['status'] != 'unpublished':
                db.execute("UPDATE plugins SET status='unpublished',revision=revision+1,updated=? WHERE id=?", (now(), id))
                db.execute('INSERT INTO audit(plugin,action,at,snapshot) VALUES (?,?,?,?)', (id, '移除插件', now(), row['draft']))
        for id, (name, summary, kind, version) in BUILTINS.items():
            if db.execute('SELECT 1 FROM plugins WHERE id=?', (id,)).fetchone():
                continue
            draft = dict(id=id, name=name, summary=summary, kind=kind, version=version,
                         minAppVersion=13, minAdapterVersion=1, rulesUrl='', package=None, notes='')
            filename = 'plugin.json' if id == 'adskip' else id + '.json'
            manifest = Path(root) / filename
            if manifest.exists():
                legacy = json.loads(manifest.read_text(encoding='utf-8'))
                draft['version'] = legacy['version']
                draft['minAppVersion'] = legacy.get('minAppVersion', 13)
                if kind == 'academic-records':
                    package = Path(root) / 'downloads' / f'qsschedule-{id}-{draft["version"]}.json'
                    if package.is_file():
                        draft['package'] = upload(root, package.read_bytes())
            if id == 'adskip' and (Path(root) / 'adskip-rules.json').is_file():
                draft['package'] = upload(root, (Path(root) / 'adskip-rules.json').read_bytes())
            ready = kind == 'builtin' or draft['package'] is not None
            snapshot = dict(draft, publicBase=public_base) if ready else None
            db.execute('INSERT INTO plugins VALUES (?,?,?,?,?,?)',
                       (id, json.dumps(draft), json.dumps(snapshot) if ready else None, 'published' if ready else 'draft', 1, now()))
            if snapshot:
                db.execute('INSERT INTO releases VALUES (?,?,?)', (id, snapshot['version'], snapshot['package']['sha256'] if snapshot['package'] else snapshot['rulesUrl']))
            db.execute('INSERT INTO audit(plugin,action,at,snapshot) VALUES (?,?,?,?)', (id, '导入现有插件', now(), json.dumps(draft)))


def upload(root, raw):
    if not 0 < len(raw) <= MAX_PACKAGE:
        raise ValueError('资料包须为 4 MB 以内的 JSON 文件')
    try:
        value = json.loads(raw)
    except (ValueError, UnicodeError):
        raise ValueError('资料包不是有效的 UTF-8 JSON') from None
    if not isinstance(value, dict):
        raise ValueError('资料包顶层须为 JSON 对象')
    digest = hashlib.sha256(raw).hexdigest()
    folder = Path(root) / 'plugin-packages'
    folder.mkdir(exist_ok=True)
    path = folder / (digest + '.json')
    # Content-addressed files never overwrite an existing release.
    if not path.exists():
        with path.open('xb') as output:
            output.write(raw)
    return dict(sha256=digest, bytes=len(raw))


def https(value):
    url = urlsplit(value)
    if url.scheme != 'https' or not url.hostname or url.username or url.password or url.fragment or url.port not in (None, 443):
        raise ValueError('来源网址须为 HTTPS 直链')
    return value


def validate(root, data, publishing=False):
    fields = {'id', 'name', 'summary', 'kind', 'version', 'minAppVersion', 'minAdapterVersion', 'rulesUrl', 'package', 'notes'}
    if not isinstance(data, dict) or set(data) != fields:
        raise ValueError('插件字段不完整或有未知字段')
    if not isinstance(data['id'], str) or not re.fullmatch(r'[a-z][a-z0-9.-]{2,63}', data['id']):
        raise ValueError('插件 ID 须为 3–64 位小写字母、数字、点或横线')
    if data['id'] in RETIRED_IDS:
        raise ValueError('该插件已移除')
    for field, limit in [('name', 40), ('summary', 80), ('notes', 2000)]:
        if not isinstance(data[field], str) or len(data[field]) > limit or (field != 'notes' and not data[field].strip()):
            raise ValueError('名称、简介或更新说明长度不符合要求')
    for field in ('version', 'minAppVersion', 'minAdapterVersion'):
        if type(data[field]) is not int or not 1 <= data[field] <= 2147483647:
            raise ValueError('版本号须为正整数')
    kind = data['kind']
    if kind not in ('builtin', 'academic-records', 'adskip-rule-pack'):
        raise ValueError('不支持的插件类型')
    if data['id'] in BUILTINS:
        if kind != BUILTINS[data['id']][2]:
            raise ValueError('已接入插件的类型不能更换')
        if kind == 'builtin' and data['id'] != 'adskip' and data['version'] > BUILTINS[data['id']][3]:
            raise ValueError('原生版本需先在宿主中接入')
    elif kind != 'adskip-rule-pack':
        raise ValueError('新插件目前支持广告规则数据包；原生功能需先接入宿主')
    if kind == 'adskip-rule-pack' and data['minAdapterVersion'] > 3:
        raise ValueError('当前规则适配器最高版本为 3')
    if not isinstance(data['rulesUrl'], str) or len(data['rulesUrl']) > 2048:
        raise ValueError('来源网址格式无效')
    if data['rulesUrl']:
        https(data['rulesUrl'])
    package = data['package']
    if package is not None:
        if not isinstance(package, dict) or set(package) != {'sha256', 'bytes'} or not re.fullmatch(r'[0-9a-f]{64}', str(package['sha256'])) or type(package['bytes']) is not int or not 0 < package['bytes'] <= MAX_PACKAGE:
            raise ValueError('资料包信息无效，请重新上传')
        file = Path(root) / 'plugin-packages' / (package['sha256'] + '.json')
        if not file.is_file() or file.stat().st_size != package['bytes']:
            raise ValueError('资料包缺失，请重新上传')
        raw = file.read_bytes()
        if hashlib.sha256(raw).hexdigest() != package['sha256']:
            raise ValueError('资料包校验失败')
        if kind == 'academic-records':
            value = json.loads(raw)
            body = value.get('data', {})
            if (value.get('schemaVersion') != 1 or value.get('pluginId') != data['id'] or value.get('pluginVersion') != data['version']
                    or not isinstance(body, dict) or not isinstance(body.get('records'), list)
                    or not 1 <= len(body['records']) <= 2000 or not isinstance(body.get('aliases'), dict) or not body['aliases']):
                raise ValueError('赛事资料包类型、版本或内容不匹配')
            ids = [r.get('id') for r in body['records'] if isinstance(r, dict)]
            if len(ids) != len(body['records']) or any(not isinstance(id, str) or not id for id in ids) or len(ids) != len(set(ids)):
                raise ValueError('赛事 ID 缺失或重复')
        elif data['id'] == 'adskip' or kind == 'adskip-rule-pack':
            value = json.loads(raw)
            rules = value.get('rules')
            if value.get('schemaVersion') not in (1, 2) or type(value.get('version')) is not int or value['version'] < 1 or not isinstance(rules, list) or not 1 <= len(rules) <= 4000:
                raise ValueError('请使用宿主 v1/v2 规则 JSON 包；其他格式请先在 App 中转换')
            ids = []
            for rule in rules:
                if not isinstance(rule, dict) or not re.fullmatch(r'[a-z0-9-]{1,64}', str(rule.get('id', ''))) or not isinstance(rule.get('name'), str) or not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+', str(rule.get('packageName', ''))):
                    raise ValueError('规则 ID、名称或应用包名无效')
                if not rule.get('targetId') and not rule.get('selectors'):
                    raise ValueError('规则缺少目标条件')
                if rule['id'] == 'builtin-global-splash':
                    raise ValueError('内置通用引擎规则由宿主管理，不能通过资料包覆盖')
                if rule['id'] == 'sportworld-native-drawn-close':
                    baseline = Path(__file__).with_name('adskip-rules.json')
                    protected = next((r for r in json.loads(baseline.read_text(encoding='utf-8'))['rules'] if r['id'] == rule['id']), None) if baseline.exists() else None
                    if protected is None or rule != protected:
                        raise ValueError('绘制型广告的内置结构规则由宿主管理，不能通过资料包修改')
                ids.append(rule['id'])
            if len(ids) != len(set(ids)):
                raise ValueError('规则 ID 重复')
            if not isinstance(value.get('name'), str) or not isinstance(value.get('source'), str):
                raise ValueError('规则包缺少名称或来源')
    if kind == 'builtin' and data['id'] != 'adskip' and package is not None:
        raise ValueError('该原生插件没有可替换的数据包')
    if publishing:
        if kind == 'academic-records' and package is None:
            raise ValueError('请先上传赛事资料包')
        if kind == 'adskip-rule-pack' and package is None and not data['rulesUrl']:
            raise ValueError('请上传规则 JSON 或填写公开规则网址')


def records(root):
    with connection(root) as db:
        return [dict(id=row['id'], draft=json.loads(row['draft']), published=json.loads(row['published']) if row['published'] else None,
                     status=row['status'], revision=row['revision'], updatedAt=row['updated'])
                for row in db.execute('SELECT * FROM plugins ORDER BY updated DESC,id') if row['id'] not in RETIRED_IDS]


def audit(root):
    with connection(root) as db:
        return [dict(row) for row in db.execute('SELECT seq,plugin,action,at FROM audit ORDER BY seq DESC LIMIT 100')]


def snapshot(root, seq):
    with connection(root) as db:
        row = db.execute('SELECT snapshot FROM audit WHERE seq=?', (seq,)).fetchone()
        if not row:
            raise ValueError('版本记录不存在')
        return json.loads(row[0])


def mutate(root, action, data, revision, public_base):
    id = data.get('id')
    if not isinstance(id, str) or not re.fullmatch(r'[a-z][a-z0-9.-]{2,63}', id) or type(revision) is not int or revision < 0:
        raise ValueError('插件标识或修订号无效')
    with connection(root) as db:
        db.execute('BEGIN IMMEDIATE')
        row = db.execute('SELECT * FROM plugins WHERE id=?', (id,)).fetchone()
        if (row and row['revision'] != revision) or (not row and revision != 0):
            raise Conflict('内容已被其他窗口更新，请重新载入后编辑')
        if action == 'save':
            validate(root, data)
            if not row and db.execute('SELECT COUNT(*) FROM plugins').fetchone()[0] >= 30:
                raise ValueError('最多管理 30 个插件')
            if row:
                db.execute('UPDATE plugins SET draft=?,revision=revision+1,updated=? WHERE id=?', (json.dumps(data), now(), id))
            else:
                db.execute('INSERT INTO plugins VALUES (?,?,NULL,\'draft\',1,?)', (id, json.dumps(data), now()))
        elif action in ('publish', 'unpublish'):
            if not row:
                raise ValueError('请先保存草稿')
            if action == 'publish':
                draft = json.loads(row['draft'])
                validate(root, draft, publishing=True)
                snapshot = dict(draft, publicBase=public_base)
                fingerprint = draft['package']['sha256'] if draft['package'] else draft['rulesUrl']
                old = db.execute('SELECT sha256 FROM releases WHERE id=? AND version=?', (id, draft['version'])).fetchone()
                maximum = db.execute('SELECT MAX(version) FROM releases WHERE id=?', (id,)).fetchone()[0]
                if old and old[0] != fingerprint:
                    raise ValueError('该版本已发布过不同资料，请增加版本号')
                if maximum and draft['version'] < maximum:
                    raise ValueError('发布版本不能低于已发布的最高版本')
                prospective = [json.loads(r['published']) for r in db.execute('SELECT id,published FROM plugins WHERE status=\'published\' AND published IS NOT NULL') if r['id'] != id] + [snapshot]
                if len(json.dumps(catalog_payload(prospective), ensure_ascii=False).encode()) > 65536:
                    raise ValueError('公开目录超过 64 KiB，请缩短插件简介或规则网址')
                db.execute('INSERT OR IGNORE INTO releases VALUES (?,?,?)', (id, draft['version'], fingerprint))
                db.execute('UPDATE plugins SET published=?,status=\'published\',revision=revision+1,updated=? WHERE id=?', (json.dumps(snapshot), now(), id))
            else:
                db.execute('UPDATE plugins SET status=\'unpublished\',revision=revision+1,updated=? WHERE id=?', (now(), id))
        else:
            raise ValueError('未知操作')
        saved = db.execute('SELECT draft FROM plugins WHERE id=?', (id,)).fetchone()[0]
        db.execute('INSERT INTO audit(plugin,action,at,snapshot) VALUES (?,?,?,?)', (id, {'save': '保存草稿', 'publish': '发布', 'unpublish': '下架'}[action], now(), saved))


def managed(root):
    return (Path(root) / 'plugins.sqlite').is_file()


def catalog(root):
    if not managed(root):
        return None
    return [r['published'] for r in records(root) if r['status'] == 'published' and r['published']]


def package_url(item):
    return item['publicBase'] + '/downloads/plugin-data-' + item['package']['sha256'] + '.json'


def release(item):
    result = dict(id=item['id'], version=item['version'], minAppVersion=item['minAppVersion'])
    if item['kind'] == 'academic-records':
        result.update(dataUrl=item['publicBase'] + f'/downloads/qsschedule-academic-records-{item["version"]}.json', **item['package'])
    elif item['id'] == 'adskip':
        # Existing Android parser expects exactly these fields.
        result = dict(version=item['version'], minAppVersion=item['minAppVersion'], rulesUrl=item['publicBase'] + '/api/rules/adskip')
    return result


def public_catalog(root):
    items = catalog(root)
    if items is None:
        return None
    return catalog_payload(items)


def catalog_payload(items):
    native = [{k: item[k] for k in ('id', 'name', 'summary', 'version', 'minAppVersion')} for item in items if item['kind'] != 'adskip-rule-pack']
    rules = [{k: item[k] for k in ('id', 'name', 'summary', 'version', 'kind', 'minAppVersion', 'minAdapterVersion')} |
             {'rulesUrl': package_url(item) if item['package'] else item['rulesUrl']} for item in items if item['kind'] == 'adskip-rule-pack']
    return dict(schemaVersion=1, plugins=native, rulePlugins=rules)


def downloadable(root, path):
    for item in catalog(root) or []:
        package = item['package']
        if package and path in ('/downloads/plugin-data-' + package['sha256'] + '.json',
                               f'/downloads/qsschedule-{item["id"]}-{item["version"]}.json'):
            return Path(root) / 'plugin-packages' / (package['sha256'] + '.json')
    return None
