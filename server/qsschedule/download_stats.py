"""Read-only dashboard over existing anonymous aggregate counters."""
import sqlite3
from datetime import datetime, timedelta, timezone
from pathlib import Path


def category(name):
    if name.endswith('.apk'):
        return 'app'
    if name.startswith('content-'):
        return 'resource'
    return 'plugin'


def report(root, days=30, now=None):
    days = int(days)
    if days not in (7, 30, 90):
        raise ValueError('请选择 7、30 或 90 天')
    today = (now or datetime.now(timezone.utc)).astimezone(timezone.utc).date()
    start = today - timedelta(days=days - 1)
    path = Path(root) / 'stats.sqlite'
    requests, completions, unique = [], [], []
    if path.exists():
        db = sqlite3.connect(path.resolve().as_uri() + '?mode=ro', uri=True, timeout=10)
        try:
            tables = {r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
            if 'requests' in tables:
                requests = db.execute('SELECT file,day,count FROM requests').fetchall()
            if 'completions' in tables:
                completions = db.execute('SELECT kind,version,COUNT(*) FROM completions GROUP BY kind,version ORDER BY kind,version').fetchall()
                unique = db.execute('SELECT kind,COUNT(DISTINCT client_hash) FROM completions GROUP BY kind').fetchall()
        finally:
            db.close()
    totals = {k: dict(total=0, today=0, last7Days=0, selectedPeriod=0) for k in ('app', 'plugin', 'resource')}
    daily = {(start + timedelta(days=i)).isoformat(): dict(app=0, plugin=0, resource=0) for i in range(days)}
    files = {}
    for name, day, count in requests:
        kind = category(name)
        row = files.setdefault(name, dict(file=name, kind=kind, total=0, today=0, last7Days=0, selectedPeriod=0))
        metrics = dict(total=count, today=count if day == today.isoformat() else 0,
                       last7Days=count if (today - timedelta(days=6)).isoformat() <= day <= today.isoformat() else 0,
                       selectedPeriod=count if start.isoformat() <= day <= today.isoformat() else 0)
        for key, value in metrics.items():
            row[key] += value
            totals[kind][key] += value
        if day in daily:
            daily[day][kind] += count
    return dict(timezone='UTC', days=days, generatedAt=datetime.now(timezone.utc).isoformat(), totals=totals,
                daily=[dict(day=day, **counts) for day, counts in daily.items()],
                files=sorted(files.values(), key=lambda row: (-row['total'], row['file'])),
                uniqueInstallations=dict(unique),
                completedDownloads=[dict(kind=k, version=v, count=c) for k, v, c in completions])
