from pathlib import Path
from urllib.request import urlopen, Request
from urllib.parse import urljoin
from bs4 import BeautifulSoup
from datetime import date
import hashlib
import json
import re
import sys
import time
import os

sys.stdout.reconfigure(encoding="utf-8")

BASE_URL = "https://jingsaitongzhi.com"
START_YEAR = date.today().year - (date.today().month < 9)
ACADEMIC_YEAR = f"{START_YEAR}-{START_YEAR + 1}"
ACADEMIC_MONTHS = [(START_YEAR + (i >= 4), (i + 8) % 12 + 1) for i in range(12)]
RETRIEVED_AT = date.today().isoformat()
ROOT = Path(os.environ["QSS_DATA"]) / "jingsai-research" if os.environ.get("QSS_DATA") else Path(__file__).resolve().parents[2] / "output/academic-preview/jingsai-research"
ROOT.mkdir(parents=True, exist_ok=True)


def fetch(url, limit=5_000_000):
    request = Request(url, headers={"User-Agent": "QSScheduleResearch/1.0"})
    with urlopen(request, timeout=30) as response:
        return response.read(limit)


def parse_calendar(raw, year, month):
    soup = BeautifulSoup(raw.decode("utf-8"), "html.parser")
    if not soup.select_one('[role="grid"]'):
        raise ValueError("Public calendar grid missing")
    entries = []
    for anchor in soup.select('a[href^="/competitions/"]'):
        href = anchor.get("href", "").split("#", 1)[0]
        if not href:
            continue
        title = anchor.get_text(" ", strip=True)
        cell = anchor.find_parent(attrs={"role": "gridcell"})
        aria = cell.get("aria-label", "") if cell else ""
        date_match = re.search(r"(?:(\d{4})年)?\s*(\d{1,2})月\s*(\d{1,2})日", aria)
        node_year = int(date_match.group(1)) if date_match and date_match.group(1) else year
        node_month = int(date_match.group(2)) if date_match else month
        node_day = int(date_match.group(3)) if date_match else None
        phase_node = anchor.find("span")
        phase = phase_node.get_text(" ", strip=True) if phase_node else ""
        if not phase:
            phase_match = re.search(r"(报名开始|报名截止|开展阶段|提交截止|比赛阶段|决赛阶段)", title)
            phase = phase_match.group(1) if phase_match else ""
        entries.append({
            "href": href,
            "calendarLabel": title,
            "calendarNode": {
                "year": node_year,
                "month": node_month,
                "day": node_day,
                "phase": phase,
            },
        })
    return entries


def load_academic_calendar():
    merged = {}
    fetched_months = []
    failed_months = []
    for year, month in ACADEMIC_MONTHS:
        url = f"{BASE_URL}/calendar?year={year}&month={month}"
        try:
            raw = fetch(url)
            (ROOT / f"calendar-{year}-{month:02d}.html").write_bytes(raw)
            month_entries = parse_calendar(raw, year, month)
            fetched_months.append({"year": year, "month": month, "items": len(month_entries)})
            for entry in month_entries:
                item = merged.setdefault(entry["href"], {
                    "href": entry["href"],
                    "calendarLabel": entry["calendarLabel"],
                    "calendarNodes": [],
                })
                if not item["calendarLabel"] and entry["calendarLabel"]:
                    item["calendarLabel"] = entry["calendarLabel"]
                node = entry["calendarNode"]
                if node not in item["calendarNodes"]:
                    item["calendarNodes"].append(node)
            print(f"calendar {year}-{month:02d}: {len(month_entries)} links")
        except Exception as error:
            failed_months.append({"year": year, "month": month, "error": f"{type(error).__name__}: {error}"})
            print(f"ERROR calendar {year}-{month:02d}: {type(error).__name__}: {error}")
        time.sleep(0.15)
    for item in merged.values():
        item["calendarNodes"].sort(key=lambda n: (
            n.get("year") or 0,
            n.get("month") or 0,
            n.get("day") or 0,
            n.get("phase") or "",
        ))
    return list(merged.values()), fetched_months, failed_months


def read_detail(entry, raw=None):
    url = urljoin(BASE_URL, entry["href"])
    raw = raw if raw is not None else fetch(url)
    detail = BeautifulSoup(raw.decode("utf-8"), "html.parser")
    main = detail.select_one("main") or detail
    heading = main.find("h1")
    article_sections = main.select(".competition-article-section")
    if not heading or not article_sections:
        raise ValueError("Competition heading or article sections missing")
    sections = {}
    faqs = []
    for section in article_sections:
        label = section.select_one("h2,h3")
        content = section.select_one(".competition-rich-content")
        if label and content:
            sections[label.get_text(" ", strip=True)] = content.get_text(" ", strip=True)
            if "常见问题" in label.get_text():
                for question in content.select("h3,h4"):
                    answer = []
                    for sibling in question.next_siblings:
                        if getattr(sibling, "name", None) in ("h3", "h4"):
                            break
                        if getattr(sibling, "get_text", None):
                            answer.append(sibling.get_text(" ", strip=True))
                    faqs.append({"question": question.get_text(" ", strip=True), "answer": "\n".join(answer).strip()})
    for section_id, label in [("universities", "院校公开清单"), ("notices", "最新通知")]:
        section = main.select_one(f"#{section_id}")
        if section:
            for promotion in section.select(".competition-contact"):
                promotion.decompose()
            sections[label] = section.get_text(" ", strip=True)
    title = heading.get_text(" ", strip=True)
    edition = main.select_one("#competition-progress-title")
    phases = []
    for phase in main.select(".competition-progress-track > li"):
        name = phase.select_one("strong")
        note = phase.select_one("small")
        if name:
            phases.append({"name": name.get_text(" ", strip=True), "note": note.get_text(" ", strip=True) if note else ""})
    official_link = main.select_one('.detail-actions a[href^="https://"], .detail-actions a[href^="http://"]')
    summary = main.select_one('.detail-hero-copy > p')
    organizer = main.select_one('#organization .competition-rich-content')
    eligibility = main.select_one('#eligibility .competition-rich-content')
    tags = [node.get_text(" ", strip=True) for node in main.select('a[href^="/categories/"]')]
    item_id = "jingsai-" + hashlib.sha256(url.encode()).hexdigest()[:16]
    (ROOT / f"{item_id}.html").write_bytes(raw)
    return {
        "id": item_id,
        "name": title,
        "summary": summary.get_text(" ", strip=True) if summary else "",
        "organizer": organizer.get_text(" ", strip=True) if organizer else "",
        "eligibility": eligibility.get_text(" ", strip=True) if eligibility else "",
        "url": url,
        "calendarLabel": entry.get("calendarLabel", ""),
        "calendarNodes": entry.get("calendarNodes", []),
        "faqs": faqs,
        "tags": list(dict.fromkeys(tags)),
        "sections": sections,
        "text": " ".join(sections.values()),
        "retrievedAt": RETRIEVED_AT,
        "sourceLastModified": None,
        "officialUrl": official_link["href"] if official_link else None,
        "edition": edition.get_text(" ", strip=True) if edition else None,
        "phases": phases,
        "academicYear": ACADEMIC_YEAR,
    }


def main():
    links, fetched_months, failed_months = load_academic_calendar()
    discovered = {e["href"]: e for e in links}
    pending = [f"{BASE_URL}/competitions"]
    visited = set()
    listing_failures = []
    while pending and len(visited) < 50:
        url = pending.pop(0)
        if url in visited:
            continue
        visited.add(url)
        try:
            raw = fetch(url)
            soup = BeautifulSoup(raw.decode("utf-8"), "html.parser")
            (ROOT / f"listing-{len(visited)}.html").write_bytes(raw)
            for a in soup.select('a[href]'):
                href = a.get("href", "").split("#")[0]
                if re.fullmatch(r"/competitions/[a-z0-9-]+", href):
                    discovered.setdefault(href, {"href": href, "calendarNodes": [], "calendarLabel": ""})
                elif re.fullmatch(r"/competitions\?page=\d+", href):
                    next_url = urljoin(BASE_URL, href)
                    if next_url not in visited and next_url not in pending:
                        pending.append(next_url)
        except Exception as error:
            listing_failures.append({"url": url, "error": type(error).__name__})
        time.sleep(0.15)
    links = list(discovered.values())
    print("deduplicated calendar links", len(links))
    previous_path = ROOT / "jingsai-catalog.json"
    previous = json.loads(previous_path.read_text(encoding="utf-8")) if previous_path.exists() else {}
    previous_items = {i["url"]: i for i in previous.get("items", [])}
    items = []
    failed_details = []
    for number, entry in enumerate(links, 1):
        try:
            item = read_detail(entry)
            items.append(item)
            print(number, item["name"])
        except Exception as error:
            failed_details.append(entry["href"])
            old = previous_items.get(urljoin(BASE_URL, entry["href"]))
            if old:
                items.append(dict(old, stale=True))
            print("ERROR", urljoin(BASE_URL, entry["href"]), type(error).__name__, error)
        time.sleep(0.08)
    if not items:
        raise RuntimeError("No competition details available; existing catalog retained")
    if failed_months or listing_failures:
        known = {i["url"] for i in items}
        items.extend(dict(i, stale=True) for i in previous_items.values() if i["url"] not in known)
    data = {
        "schemaVersion": 2,
        "academicYear": ACADEMIC_YEAR,
        "source": f"{BASE_URL}/competitions",
        "sources": [f"{BASE_URL}/calendar?year={y}&month={m}" for y, m in ACADEMIC_MONTHS],
        "sourceSite": "竞赛通知网",
        "retrievedAt": RETRIEVED_AT,
        "degraded": bool(failed_months or failed_details or listing_failures),
        "listingPagesFetched": len(visited) - len(listing_failures),
        "failures": {"months": failed_months, "details": failed_details, "listing": listing_failures},
        "robots": "allowed: /; disallow: /api/",
        "calendarRange": {
            "start": f"{START_YEAR}-09",
            "end": f"{START_YEAR + 1}-08",
            "monthsRequested": [{"year": y, "month": m} for y, m in ACADEMIC_MONTHS],
            "monthsFetched": fetched_months,
            "monthsFailed": failed_months,
        },
        "items": items,
    }
    output = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    temporary = previous_path.with_suffix(".tmp")
    temporary.write_text(output, encoding="utf-8")
    temporary.replace(previous_path)
    if os.environ.get("QSS_DATA"):
        published = Path(os.environ["QSS_DATA"]) / "jingsai-catalog.json"
        temporary = published.with_suffix(".tmp")
        temporary.write_text(output, encoding="utf-8")
        temporary.replace(published)
    else:
        (Path(__file__).resolve().parents[2] / "web/academic-preview/jingsai-catalog.js").write_text("window.JingsaiCatalog=" + output + ";\n", encoding="utf-8")
    print("saved", len(items), "items from", len(fetched_months), "months")
    if failed_months:
        print("failed months", failed_months)


if __name__ == "__main__":
    main()
