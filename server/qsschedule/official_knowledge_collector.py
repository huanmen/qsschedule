"""Build one public, normalized knowledge feed from the official collectors.

The source collectors own fetching and cache retention.  This small, deterministic
step only joins their public snapshots, so a failed source never erases a working
source and no account, score, roster, or other personal data is copied.
"""
from __future__ import annotations

import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(os.environ.get("QSS_DATA", "/var/lib/qsschedule"))


def timestamp() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def _title_category(title: str, source_type: str) -> str:
    title = title or ""
    if source_type == "competition":
        if re.search(r"创新创业训练计划|训练计划", title):
            return "创新创业训练"
        if re.search(r"目录|认定|获奖统计|奖金|综测", title):
            return "认定与综测"
        return "赛事通知"
    if re.search(r"普通话|水平测试", title):
        return "普通话"
    if re.search(r"四.?六级|四、六级|大学英语|CET", title, re.I):
        return "四六级"
    if re.search(r"计算机等级|NCRE", title, re.I):
        return "计算机等级"
    if "教师资格" in title:
        return "教师资格"
    return "其他考试"


def _load(name: str) -> dict:
    try:
        value = json.loads((ROOT / name).read_text(encoding="utf-8"))
        return value if isinstance(value, dict) else {}
    except (OSError, ValueError, TypeError):
        return {}


def _asset(asset: dict) -> dict | None:
    if not isinstance(asset, dict) or not isinstance(asset.get("url"), str):
        return None
    # The source collectors already restrict hosts. Keep this guard at the
    # publishing boundary so future collectors cannot accidentally expose links.
    if not re.match(r"^https://(?:www\.)?qztc\.edu\.cn(?:/|$)", asset["url"]):
        return None
    return {"type": asset.get("type", "attachment"), "name": asset.get("name") or "原文附件", "url": asset["url"]}


def normalize(feed: dict, source_type: str) -> list[dict]:
    output = []
    for item in feed.get("items", []):
        if not isinstance(item, dict) or not item.get("url") or not item.get("title"):
            continue
        title = str(item["title"]).strip()
        category = item.get("category") or _title_category(title, source_type)
        entry = {
            "id": f"{source_type}:{item.get('id') or item['url']}",
            "kind": "competition_notice" if source_type == "competition" else "exam_notice",
            "sourceType": source_type,
            "category": category,
            "title": title,
            "region": item.get("region") or ("泉州" if source_type == "competition" else "全国"),
            "sourceId": item.get("sourceId"),
            "sourceName": item.get("sourceName"),
            "officialUrl": item["url"],
            "publishedAt": item.get("publishedAt"),
            "excerpt": item.get("excerpt", ""),
            "content": item.get("content", ""),
            "scheduleNotes": item.get("scheduleNotes", []),
            "examDate": item.get("examDate"),
            "dateStatus": item.get("dateStatus") or "review_required",
            "needsReview": bool(item.get("needsReview")),
            "contentState": item.get("contentState", "ok"),
            "updatedAt": item.get("updatedAt"),
            "assets": [],
        }
        seen_assets = set()
        for raw_asset in item.get("assets", []):
            asset = _asset(raw_asset)
            if asset and asset["url"] not in seen_assets:
                seen_assets.add(asset["url"])
                entry["assets"].append(asset)
        entry["needsReview"] = entry["needsReview"] or entry["contentState"] != "ok" or bool(entry["assets"])
        output.append(entry)
    return output


def build(competition: dict | None = None, exams: dict | None = None) -> dict:
    competition = competition if competition is not None else _load("competition-notices.json")
    exams = exams if exams is not None else _load("exam-notices.json")
    entries = normalize(competition, "competition") + normalize(exams, "exam")
    # Keep one record when the same official URL is exposed by two categories.
    unique = {}
    for entry in entries:
        old = unique.get(entry["officialUrl"])
        if old is None or (old["contentState"] != "ok" and entry["contentState"] == "ok"):
            unique[entry["officialUrl"]] = entry
    entries = sorted(unique.values(), key=lambda x: (x.get("publishedAt") or "", x["id"]), reverse=True)
    source_status = []
    for source_type, feed in (("competition", competition), ("exam", exams)):
        for source in feed.get("sources", []):
            source_status.append({
                "sourceType": source_type,
                "id": source.get("id"),
                "name": source.get("name"),
                "region": source.get("region"),
                "state": source.get("state", "error"),
                "checkedAt": source.get("checkedAt"),
                "lastSuccessAt": source.get("lastSuccessAt"),
                "pageCount": source.get("pageCount", 1),
                "noticeCount": source.get("noticeCount", 0),
                "articleErrors": source.get("articleErrors", 0),
                "error": source.get("error"),
            })
    degraded = not source_status or any(s["state"] != "ok" for s in source_status)
    return {
        "schemaVersion": 1,
        "generatedAt": timestamp(),
        "checkedAt": max((f.get("checkedAt") for f in (competition, exams) if f.get("checkedAt")), default=None),
        "refreshIntervalSeconds": 3600,
        "degraded": degraded,
        "sources": source_status,
        "items": entries[:700],
    }


def atomic_save(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(".tmp")
    temp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    temp.replace(path)


def main() -> int:
    result = build()
    atomic_save(ROOT / "official-knowledge.json", result)
    print(json.dumps({"items": len(result["items"]), "degraded": result["degraded"]}, ensure_ascii=False))
    # A partial source is still useful; a completely unavailable source remains
    # visible through the degraded flag while the last successful records stay.
    return 0 if result["items"] or result["sources"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
