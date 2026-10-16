"""Run locally over SSH; there is deliberately no public administration API."""
import argparse
import hashlib
import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(os.environ.get("QSS_DATA", "/var/lib/qsschedule"))


def validate_announcements(feed):
    if set(feed) != {"schemaVersion", "items"} or feed["schemaVersion"] != 1 or len(feed["items"]) > 100:
        raise ValueError("invalid announcement feed")
    ids = set()
    fields = {"id", "revision", "title", "body", "publishedAt", "expiresAt", "important", "minVersionCode", "maxVersionCode", "action"}
    for item in feed["items"]:
        if set(item) - fields or not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", item["id"]) or item["id"] in ids:
            raise ValueError("invalid or duplicate id")
        ids.add(item["id"])
        if not 0 < len(item["title"].strip()) <= 100 or not 0 < len(item["body"].strip()) <= 12000:
            raise ValueError("invalid title/body")
        if type(item.get("revision", 1)) is not int or item.get("revision", 1) < 1:
            raise ValueError("invalid revision")
        start = datetime.fromisoformat(item["publishedAt"].replace("Z", "+00:00"))
        if start.tzinfo is None:
            raise ValueError("timestamp needs timezone")
        end = item.get("expiresAt")
        if end and datetime.fromisoformat(end.replace("Z", "+00:00")) <= start:
            raise ValueError("expiry before publication")
        minimum, maximum = item.get("minVersionCode", 1), item.get("maxVersionCode")
        if type(minimum) is not int or minimum < 1 or (maximum is not None and (type(maximum) is not int or maximum < minimum)):
            raise ValueError("invalid version range")
        if type(item.get("important", False)) is not bool or item.get("action") not in (None, "check_update"):
            raise ValueError("invalid action or importance")
    if len(json.dumps(feed, ensure_ascii=False).encode()) > 128 * 1024:
        raise ValueError("feed too large")


def atomic_json(name, data):
    ROOT.mkdir(parents=True, exist_ok=True)
    path = ROOT / name
    if path.exists():
        backup = ROOT / "backups"
        backup.mkdir(exist_ok=True)
        (backup / (name + "." + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%f"))).write_bytes(path.read_bytes())
    tmp = ROOT / (name + ".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    tmp.replace(path)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["announcements", "update", "stats"])
    parser.add_argument("file", nargs="?")
    args = parser.parse_args()
    if args.command == "stats":
        from app import statistics
        print(json.dumps(statistics(), ensure_ascii=False, indent=2))
    else:
        data = json.loads(Path(args.file).read_text(encoding="utf-8-sig"))
        if args.command == "announcements":
            validate_announcements(data)
        else:
            name = unquote(data["apkUrl"].removeprefix("https://huanmen.com.cn/qsschedule/downloads/"))
            if not re.fullmatch(r"(?:泉师课程表|qsschedule)-[0-9A-Za-z._-]+\.apk", name):
                raise ValueError("APK must use this server download route")
            apk = ROOT / "downloads" / name
            digest = hashlib.sha256()
            with apk.open("rb") as source:
                for block in iter(lambda: source.read(65536), b""):
                    digest.update(block)
            if apk.stat().st_size != data["sizeBytes"] or digest.hexdigest() != data["sha256"]:
                raise ValueError("APK size/hash mismatch")
        atomic_json("announcements.json" if args.command == "announcements" else "update.json", data)
        print("Published successfully")
