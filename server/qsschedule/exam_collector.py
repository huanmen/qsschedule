"""Fetch public official exam notices. Never infer exam dates from publication dates."""
import hashlib
import html
import json
import os
import re
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlsplit, urlunsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

SOURCES = (
    {"id": "qztc", "name": "泉师教务处", "url": "https://www.qztc.edu.cn/jwc/ksbm/list.htm", "region": "泉州", "kind": "school", "maxPages": 3},
    {"id": "cet", "name": "教育部教育考试院 · 四六级", "url": "https://cet.neea.edu.cn/", "region": "全国", "kind": "neea"},
    {"id": "ncre", "name": "教育部教育考试院 · 计算机等级", "url": "https://ncre.neea.edu.cn/", "region": "全国", "kind": "neea"},
    {"id": "fujian", "name": "福建省教育考试院", "url": "https://www.eeafj.cn/fxlks/", "region": "福建", "kind": "fujian"},
)
MAX_BYTES = 2_000_000
ARTICLE_RECHECK_SECONDS = 6 * 3600
STALE_SECONDS = 24 * 3600


def timestamp():
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def age_seconds(value):
    try:
        return (datetime.now(timezone.utc) - datetime.fromisoformat(value.replace("Z", "+00:00"))).total_seconds()
    except (ValueError, TypeError, AttributeError):
        return float("inf")


def official_url(base, link):
    parsed = urlsplit(urljoin(base, link))
    origin = urlsplit(base)
    if parsed.hostname != origin.hostname or parsed.scheme not in ("http", "https") or parsed.username or parsed.password:
        return None
    if parsed.port not in (None, 80, 443):
        return None
    return urlunsplit(("https", origin.hostname, parsed.path, parsed.query, ""))


class OfficialRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        target = official_url(request.full_url, newurl)
        if not target:
            raise ValueError("unexpected redirect host")
        return super().redirect_request(request, fp, code, msg, headers, target)


def fetch(url, validators=None):
    headers = {"User-Agent": "QSScheduleExamNotices/1.0 (+https://huanmen.com.cn/qsschedule/)", "Accept": "text/html"}
    if validators:
        for key, header in (("etag", "If-None-Match"), ("lastModified", "If-Modified-Since")):
            if validators.get(key):
                headers[header] = validators[key]
    for attempt in range(2):
        try:
            with build_opener(OfficialRedirect()).open(Request(url, headers=headers), timeout=15) as response:
                if response.headers.get_content_type() not in ("text/html", "application/xhtml+xml"):
                    raise ValueError("expected HTML")
                raw = response.read(MAX_BYTES + 1)
                if len(raw) > MAX_BYTES:
                    raise ValueError("response too large")
                # BeautifulSoup reads the actual charset declaration, including older GBK pages.
                return raw, {"etag": response.headers.get("ETag"), "lastModified": response.headers.get("Last-Modified")}, response.status
        except HTTPError as error:
            if error.code == 304:
                return None, validators or {}, 304
            if error.code < 500 or attempt:
                raise
        except (URLError, TimeoutError, OSError):
            if attempt:
                raise
        time.sleep(1)
    raise RuntimeError("fetch failed")


def category(title):
    if re.search(r"普通话|水平测试", title):
        return "普通话"
    if re.search(r"四.?六级|四、六级|大学英语|CET", title, re.I):
        return "四六级"
    if re.search(r"计算机等级|NCRE", title, re.I):
        return "计算机等级"
    if "教师资格" in title:
        return "教师资格"
    return None


def clean_title(value):
    value = html.unescape(value or "")
    value = re.sub(r"<[^>]+>", "", value)
    return re.sub(r"\s+", " ", value).strip()


def parse_index(raw, source):
    from bs4 import BeautifulSoup
    soup = BeautifulSoup(raw, "html.parser")
    nodes = soup.select(".news_title a[href]") if source["kind"] == "school" else soup.select("a[href]")
    expected = {"school": r"/jwc/\d{4}/\d{4}/c\d+a\d+/page\.htm$", "neea": r"/html1/report/\d+/[\d-]+\.htm$", "fujian": r"/[^/]+/\d{8}/\d+\.html$"}[source["kind"]]
    links = {}
    for node in nodes:
        url = official_url(source["url"], node["href"])
        title = clean_title(node.get("title") or node.get_text(" ", strip=True))
        kind = category(title)
        if not url or not re.search(expected, urlsplit(url).path) or not kind:
            continue
        published = None
        if source["kind"] == "school":
            parent = node.find_parent("li")
            meta = parent.select_one(".news_meta") if parent else None
            if meta:
                published = date_text(meta.get_text())
        elif source["kind"] == "fujian":
            match = re.search(r"/(\d{4})(\d{2})(\d{2})/", urlsplit(url).path)
            if match:
                published = "-".join(match.groups())
        links[url] = {"id": source["id"] + "-" + hashlib.sha256(url.encode()).hexdigest()[:20], "title": title, "url": url, "category": kind, "region": source["region"], "sourceId": source["id"], "sourceName": source["name"], "publishedAt": published}
    if not links:
        raise ValueError("no recognized exam articles; source layout may have changed")
    return list(links.values())[:20]


def date_text(value):
    match = re.search(r"(?<!\d)(20\d{2})[-年/.](\d{1,2})[-月/.](\d{1,2})", value)
    if match:
        try:
            return datetime(*map(int, match.groups())).date().isoformat()
        except ValueError:
            pass
    return None


def parse_article(raw, source, url):
    from bs4 import BeautifulSoup
    soup = BeautifulSoup(raw, "html.parser")
    selector, meta_selector = {"school": (".wp_articlecontent", ".arti_update"), "neea": ("#ReportIDtext", "#ReportIDIssueTime"), "fujian": ("#Info_Content", ".summary")}[source["kind"]]
    body = soup.select_one(selector)
    if body is None:
        raise ValueError("article content missing; source layout may have changed")
    for unwanted in body.select("script, style"):
        unwanted.decompose()
    text = body.get_text(" ", strip=True)
    assets = []
    for node in body.select("img[src], a[href], [pdfsrc], embed[src], object[data], iframe[src]"):
        link = node.get("pdfsrc") or node.get("src") or node.get("data") or node.get("href")
        target = official_url(url, link)
        if not target:
            continue
        if node.name == "img" or re.search(r"\.(pdf|docx?|xlsx?|zip)(?:$|\?)", target, re.I):
            assets.append({"type": "image" if node.name == "img" else "attachment", "url": target, "name": node.get("alt") or node.get("title") or (node.get("id") if node.has_attr("pdfsrc") else None) or node.get_text(" ", strip=True) or "原文附件"})
    if not text and not assets:
        raise ValueError("empty article")
    meta = soup.select_one(meta_selector)
    digest = hashlib.sha256(json.dumps([text, assets], ensure_ascii=False, sort_keys=True).encode()).hexdigest()
    return {"publishedAt": date_text(meta.get_text()) if meta else None, "excerpt": text[:220], "content": text[:16000], "assets": assets[:30], "contentHash": digest, "needsReview": bool(assets), "dateStatus": "review_required", "examDate": None}


def error_kind(error):
    if isinstance(error, HTTPError):
        return "http_" + str(error.code)
    if isinstance(error, (URLError, TimeoutError, OSError)):
        return "network_error"
    return "parse_error"


def _page_url(source, page):
    if page == 1:
        return source["url"]
    return source["url"].rsplit("/", 1)[0] + f"/list{page}.htm"


def collect(previous, fetcher=fetch, pause=time.sleep, sources=SOURCES, index_parser=parse_index, article_parser=parse_article):
    checked = timestamp()
    old_items = {item["url"]: item for item in previous.get("items", [])}
    items = dict(old_items)
    old_sources = {source["id"]: source for source in previous.get("sources", [])}
    statuses = []
    for source in sources:
        old = old_sources.get(source["id"], {})
        status = {**source, "checkedAt": checked, "lastSuccessAt": old.get("lastSuccessAt"), "state": "error", "httpStatus": None, "error": None, "validators": old.get("validators", {}), "articleErrors": 0}
        try:
            page_limit = source.get("maxPages", 1) if source.get("kind") == "school" else 1
            discovered = []
            page_validators = {}
            page_discovered = {}
            for page in range(1, page_limit + 1):
                index_url = _page_url(source, page)
                old_validators = old.get("pageValidators", {}).get(str(page), old.get("validators", {}) if page == 1 else {})
                try:
                    raw, validators, code = fetcher(index_url, old_validators)
                    if code == 304:
                        cached_page = old.get("pageDiscovered", {}).get(str(page), [])
                        if not cached_page and page == 1:
                            cached_page = old.get("discovered", [])
                        if not cached_page:
                            break
                        page_entries = [{**entry, "title": clean_title(entry["title"])} for entry in cached_page]
                    else:
                        page_entries = index_parser(raw, source)
                    if not page_entries:
                        break
                except (OSError, ValueError, TypeError):
                    # A missing later page is normal when a school has fewer
                    # pages than the configured discovery ceiling.
                    if page > 1:
                        break
                    raise
                status["httpStatus"] = code
                page_validators[str(page)] = validators
                page_discovered[str(page)] = page_entries
                discovered.extend(page_entries)
            if not discovered:
                raise ValueError("no discovered index entries")
            old_discovered = old.get("discovered", [])
            if len(old_discovered) >= 8 and len(discovered) < len(old_discovered) / 2:
                raise ValueError("unexpected index shrink")
            # The flattened fields retain compatibility with older snapshots;
            # page fields let the next run use conditional requests per page.
            status.update(validators=page_validators.get("1", {}), pageValidators=page_validators,
                          pageDiscovered=page_discovered, discovered=discovered,
                          pageCount=len(page_discovered), lastSuccessAt=checked, state="ok")
            for entry in discovered:
                cached = old_items.get(entry["url"], {})
                if "content" in cached and cached.get("contentState") == "ok" and cached.get("contentHash") and cached.get("title") == entry["title"] and age_seconds(cached.get("checkedAt")) < ARTICLE_RECHECK_SECONDS:
                    continue
                try:
                    article_raw, article_validators, article_code = fetcher(entry["url"], cached.get("validators") if "content" in cached else None)
                    if article_code == 304 and cached.get("contentHash"):
                        item = {**cached, **entry, "publishedAt": cached.get("publishedAt") or entry.get("publishedAt")}
                    else:
                        detail = article_parser(article_raw, source, entry["url"])
                        item = {**entry, **detail, "publishedAt": detail.get("publishedAt") or entry.get("publishedAt")}
                    item.update(checkedAt=checked, fetchedAt=cached.get("fetchedAt") or checked, updatedAt=cached.get("updatedAt") if cached.get("contentHash") == item.get("contentHash") else checked, validators=article_validators, contentState="ok")
                    items[entry["url"]] = item
                except (OSError, ValueError, TypeError) as error:
                    status["articleErrors"] += 1
                    items[entry["url"]] = {**cached, **entry, "publishedAt": cached.get("publishedAt") or entry.get("publishedAt"), "contentState": "error", "contentError": error_kind(error), "needsReview": True, "examDate": None}
                pause(0.25)
            if status["articleErrors"]:
                status["state"] = "partial"
        except (OSError, ValueError, TypeError) as error:
            status.update(error=error_kind(error), discovered=old.get("discovered", []))
        status["noticeCount"] = sum(item["sourceId"] == source["id"] for item in items.values())
        statuses.append(status)
    ordered = sorted(items.values(), key=lambda item: (item.get("publishedAt") or "", item["id"]), reverse=True)[:300]
    return {"schemaVersion": 1, "checkedAt": checked, "lastSuccessAt": max((s["lastSuccessAt"] for s in statuses if s.get("lastSuccessAt")), default=None), "refreshIntervalSeconds": 3600, "sources": statuses, "items": ordered}


def period_matches(item, period):
    """Match an explicitly requested historical year/term from notice text."""
    if not period:
        return True
    value = str(period).strip().lower()
    match = re.fullmatch(r"(20\d{2})(?:[-_ ]?(spring|autumn|fall|上半年|下半年))?", value)
    if not match:
        raise ValueError("invalid period; use YYYY or YYYY-spring/YYYY-autumn")
    year, term = match.groups()
    text = " ".join(str(item.get(key) or "") for key in ("title", "content", "excerpt", "publishedAt")).lower()
    if year not in text:
        return False
    if not term:
        return True
    aliases = {"spring": ("上半年", "春", "spring"), "autumn": ("下半年", "秋", "fall", "autumn"), "fall": ("下半年", "秋", "fall", "autumn"), "上半年": ("上半年", "春", "spring"), "下半年": ("下半年", "秋", "fall", "autumn")}
    return any(alias in text for alias in aliases[term])


def public_feed(data, period=None):
    sources = []
    for source in data.get("sources", []):
        public = {key: value for key, value in source.items() if key not in ("validators", "discovered", "kind")}
        public["stale"] = age_seconds(source.get("lastSuccessAt")) > STALE_SECONDS
        sources.append(public)
    items = [{key: value for key, value in item.items() if key != "validators"} for item in data.get("items", []) if period_matches(item, period)]
    result = {"schemaVersion": 1, "checkedAt": data.get("checkedAt"), "lastSuccessAt": data.get("lastSuccessAt"), "refreshIntervalSeconds": 3600, "degraded": not sources or any(s["state"] != "ok" or s["stale"] for s in sources), "sources": sources, "items": items}
    if period:
        result["period"] = str(period)
    return result


def atomic_save(path, data):
    temp = path.with_suffix(".tmp")
    with temp.open("w", encoding="utf-8") as file:
        json.dump(data, file, ensure_ascii=False, indent=2)
        file.flush()
        os.fsync(file.fileno())
    temp.replace(path)


def main():
    root = Path(os.environ.get("QSS_DATA", "/var/lib/qsschedule"))
    root.mkdir(parents=True, exist_ok=True)
    path = root / "exam-notices.json"
    try:
        previous = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        previous = {}
    # Invalid on-disk JSON is an operational error; preserve it for diagnosis.
    result = collect(previous)
    atomic_save(path, result)
    print(json.dumps({"items": len(result["items"]), "sources": [{"id": s["id"], "state": s["state"], "articleErrors": s["articleErrors"]} for s in result["sources"]]}))
    return 0 if all(s["state"] == "ok" for s in result["sources"]) else 1


if __name__ == "__main__":
    raise SystemExit(main())
