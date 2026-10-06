"""Read-only publication check. Reports categories and paths, never matched values."""
import argparse
import collections
import json
import pathlib
import re
import subprocess

PATTERNS = {
    'access-token': r'(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}|sk-[A-Za-z0-9_-]{24,}|AKIA[0-9A-Z]{16})',
    'private-key': r'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----',
    'credential-literal': r'(?i)(?:password|passwd|api[_-]?key|secret|access[_-]?token)\s*[=:]\s*["\'][A-Za-z0-9_!@#$%^&*+-]{8,}["\']',
    'session-value': r'(?i)(?:JSESSIONID|CASTGC|Authorization)\s*[=:]\s*["\']?(?:Bearer )?[A-Za-z0-9_.-]{20,}',
    'student-id': r'(?<!\d)(?:20[012]\d)\d{7,10}(?!\d)',
    'phone-number': r'(?<!\d)1[3-9]\d{9}(?!\d)',
    'email': r'[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}',
    'local-user-path': r'(?i)(?:C:[/\\]Users[/\\][^/\\\s]+|/home/[^/\s]+)',
}
FORBIDDEN = re.compile(r'(?i)(?:^|/)(?:local\.properties|.*\.(?:apk|aab|db|sqlite3?|jks|keystore|p12|pem|mhtml?|tar)|(?:phone|window)-[^/]+|users\.json)$')

def scan(data, label):
    if b'\0' in data: return []
    text = data.decode('utf-8', errors='replace')
    findings = []
    for category, pattern in PATTERNS.items():
        hits = []
        for n, line in enumerate(text.splitlines(), 1):
            if re.search(pattern, line): hits.append(n)
        if hits: findings.append({'path': label, 'category': category, 'lines': hits[:35], 'count': len(hits)})
    return findings

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('root', type=pathlib.Path)
    parser.add_argument('--history', action='store_true')
    parser.add_argument('--report', type=pathlib.Path, required=True)
    args = parser.parse_args()
    findings = []
    forbidden = []
    objects = 0
    if args.history:
        raw = subprocess.check_output(['git', 'rev-list', '--objects', '--all'], cwd=args.root).decode('utf-8')
        entries = {}
        for line in raw.splitlines():
            oid, _, label = line.partition(' ')
            entries.setdefault(oid, label)
        p = subprocess.Popen(['git', 'cat-file', '--batch'], cwd=args.root, stdin=subprocess.PIPE, stdout=subprocess.PIPE)
        for oid, label in entries.items():
            p.stdin.write((oid + '\n').encode()); p.stdin.flush()
            info = p.stdout.readline().split()
            data = p.stdout.read(int(info[2])); p.stdout.read(1)
            if info[1] not in [b'blob', b'commit', b'tag']: continue
            objects += 1
            if FORBIDDEN.search(label): forbidden.append(label)
            findings += [{**f, 'object': oid} for f in scan(data, label or info[1].decode())]
        p.stdin.close(); p.wait()
    else:
        for f in args.root.rglob('*'):
            if not f.is_file() or f.is_symlink() or any(s in f.relative_to(args.root).parts for s in ['.git', '.gradle', 'build', '.kotlin']): continue
            label = f.relative_to(args.root).as_posix()
            objects += 1
            if FORBIDDEN.search(label): forbidden.append(label)
            findings += scan(f.read_bytes(), label)
    report = {'history': args.history, 'objects': objects, 'forbiddenPaths': sorted(set(forbidden)), 'findings': findings}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'objects': objects, 'forbidden': len(set(forbidden)), 'categories': dict(collections.Counter(f['category'] for f in findings)), 'report': str(args.report)}, ensure_ascii=True))

if __name__ == '__main__': main()
