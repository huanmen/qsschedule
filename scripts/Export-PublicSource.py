"""Export reproducible source inputs, excluding local data and verification artifacts."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

SKIP_DIRS = {'.git', '.gradle', '.kotlin', 'build', 'node_modules', '__pycache__', '.idea', '.vscode', 'downloads', 'plugin-packages', 'captures', '.cxx'}
SKIP_SUFFIXES = {'.apk', '.aab', '.db', '.sqlite', '.sqlite3', '.jks', '.keystore', '.key', '.pem', '.p12', '.log', '.tmp', '.bak', '.mht', '.mhtml', '.pyc'}
ROOT_FILES = {'.gitignore', 'README.md', 'PRIVACY.md', 'LICENSE', 'THIRD_PARTY_NOTICES.md', 'Start-Plugin-Admin.ps1', '打开插件管理.cmd'}
DOCS = {'SOURCE-PROVENANCE.md', 'MIGRATION-20261006.md', 'PLUGIN-SDK.md', 'PLUGIN-ADMIN.md', 'RELEASE-1.5.0.md'}

def included(relative):
    if any(p in SKIP_DIRS for p in relative.parts): return False
    if relative.suffix.lower() in SKIP_SUFFIXES or relative.name in {'local.properties', 'users.json'}: return False
    if relative.name.startswith('.env'): return relative.name == '.env.example'
    if len(relative.parts) == 1: return relative.name in ROOT_FILES
    root = relative.parts[0]
    if root == 'android': return True
    if root == 'web': return relative.parts[1] == 'academic-preview'
    if root == 'plugins': return relative.suffix == '.json'
    if root == 'scripts': return relative.name in {'Audit-PublicSource.py', 'Export-PublicSource.py', 'Publish-GitHubSource.py'}
    if root == 'docs': return len(relative.parts) == 2 and relative.name in DOCS
    if root == 'server':
        if relative.name.startswith('deploy-'): return False
        return relative.suffix in {'.py', '.conf', '.service', '.timer', '.md', '.txt', '.json', '.html', '.css', '.js'}
    return False

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('root', type=Path)
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    root, destination = args.root.resolve(), args.destination.resolve()
    if destination == root or destination.exists(): raise SystemExit('Destination must be a new directory')
    destination.mkdir(parents=True)
    entries = []
    # Walk only source roots; never recurse through output or device artifacts.
    candidates = [root / p for p in ROOT_FILES if (root / p).is_file()]
    import os
    for base in ('android', 'web', 'plugins', 'scripts', 'docs', 'server'):
        for folder, directories, names in os.walk(root / base):
            directories[:] = [d for d in directories if d not in SKIP_DIRS]
            candidates.extend(Path(folder) / n for n in names)
    for source in sorted(candidates):
        relative = source.relative_to(root)
        if not included(relative) or source.is_symlink(): continue
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        entries.append({'path': relative.as_posix(), 'sha256': hashlib.sha256(target.read_bytes()).hexdigest()})
    print(json.dumps({'destination': str(destination), 'files': len(entries), 'bytes': sum((destination / e['path']).stat().st_size for e in entries)}, ensure_ascii=True))

if __name__ == '__main__': main()
