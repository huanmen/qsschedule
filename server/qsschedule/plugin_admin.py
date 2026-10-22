"""Local-only web administration. On a server, access through an SSH tunnel."""
import argparse
import json
import secrets
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit, parse_qs

import plugin_store as store
from download_stats import report

ASSETS = Path(__file__).with_name('plugin-admin')


class AdminHandler(BaseHTTPRequestHandler):
    server_version = 'QSScheduleAdmin'

    def log_message(self, *_):
        pass

    def respond(self, value, status=200, mime='application/json; charset=utf-8'):
        raw = value if isinstance(value, bytes) else json.dumps(value, ensure_ascii=False).encode()
        self.send_response(status)
        for key, val in {'Content-Type': mime, 'Content-Length': str(len(raw)), 'Cache-Control': 'no-store',
                         'X-Content-Type-Options': 'nosniff', 'X-Frame-Options': 'DENY',
                         'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"}.items():
            self.send_header(key, val)
        self.end_headers()
        if self.command != 'HEAD':
            self.wfile.write(raw)

    def local(self):
        port = self.server.server_port
        host = self.headers.get('Host', '')
        if host not in (f'127.0.0.1:{port}', f'localhost:{port}'):
            self.respond({'error': '仅允许本机或同端口 SSH 隧道访问'}, 403)
            return False
        origin = self.headers.get('Origin')
        if origin and origin != 'http://' + host:
            self.respond({'error': '不允许跨站操作'}, 403)
            return False
        if self.headers.get('Sec-Fetch-Site') == 'cross-site':
            self.respond({'error': '不允许跨站访问'}, 403)
            return False
        return True

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        if not self.local():
            return
        path = urlsplit(self.path).path
        query = parse_qs(urlsplit(self.path).query)
        if path == '/api/download-stats':
            try:
                return self.respond(report(self.server.root, query.get('days', ['30'])[0]))
            except ValueError as error:
                return self.respond({'error': str(error)}, 400)
        if path == '/api/package':
            import re
            digest = query.get('sha', [''])[0]
            if not re.fullmatch(r'[0-9a-f]{64}', digest):
                return self.respond({'error': '资料包不存在'}, 404)
            file = self.server.root / 'plugin-packages' / (digest + '.json')
            if not file.is_file():
                return self.respond({'error': '资料包不存在'}, 404)
            return self.respond(file.read_bytes())
        if path == '/api/snapshot':
            try:
                return self.respond(store.snapshot(self.server.root, int(query.get('seq', ['0'])[0])))
            except ValueError as error:
                return self.respond({'error': str(error)}, 404)
        if path == '/api/state':
            return self.respond(dict(plugins=store.records(self.server.root), audit=store.audit(self.server.root),
                                     csrf=self.server.csrf, publicBase=self.server.public_base, dataDirectory=str(self.server.root)))
        files = {'/': ('index.html', 'text/html; charset=utf-8'), '/admin.js': ('admin.js', 'text/javascript; charset=utf-8'), '/admin.css': ('admin.css', 'text/css; charset=utf-8')}
        files.update({'/stats': ('stats.html', 'text/html; charset=utf-8'),
                      '/stats.js': ('stats.js', 'text/javascript; charset=utf-8'),
                      '/stats.css': ('stats.css', 'text/css; charset=utf-8')})
        if path in files:
            filename, mime = files[path]
            return self.respond((ASSETS / filename).read_bytes(), mime=mime)
        self.respond({'error': '页面不存在'}, 404)

    def do_POST(self):
        if not self.local():
            return
        if not secrets.compare_digest(self.headers.get('X-QSS-CSRF', ''), self.server.csrf):
            return self.respond({'error': '页面已过期，请刷新'}, 403)
        try:
            path = urlsplit(self.path).path
            if self.headers.get('Transfer-Encoding'):
                return self.respond({'error': '不支持此上传方式'}, 400)
            length = int(self.headers.get('Content-Length', '0'))
            limit = store.MAX_PACKAGE if path == '/api/upload' else 16000
            if not 0 < length <= limit:
                return self.respond({'error': '文件或请求过大'}, 413)
            if self.headers.get_content_type() != 'application/json':
                return self.respond({'error': '请选择 JSON 资料包'}, 415)
            raw = self.rfile.read(length)
            if path == '/api/upload':
                return self.respond(store.upload(self.server.root, raw))
            if path not in ('/api/save', '/api/publish', '/api/unpublish'):
                return self.respond({'error': '接口不存在'}, 404)
            body = json.loads(raw)
            if not isinstance(body, dict) or set(body) != {'data', 'revision'} or not isinstance(body['data'], dict) or type(body['revision']) is not int:
                raise ValueError('请求格式无效')
            store.mutate(self.server.root, path.rsplit('/', 1)[-1], body['data'], body['revision'], self.server.public_base)
            self.respond({'ok': True})
        except store.Conflict as error:
            self.respond({'error': str(error)}, 409)
        except (ValueError, TypeError, KeyError, UnicodeError) as error:
            self.respond({'error': str(error) or '请求格式无效'}, 400)
        except OSError:
            self.respond({'error': '无法保存，请检查数据目录权限及磁盘空间'}, 500)


def create_server(root, port=18474, public_base='https://huanmen.com.cn/qsschedule'):
    store.https(public_base)
    store.initialize(root, public_base.rstrip('/'))
    server = ThreadingHTTPServer(('127.0.0.1', port), AdminHandler)
    server.root = Path(root).resolve()
    server.public_base = public_base.rstrip('/')
    server.csrf = secrets.token_urlsafe(32)
    return server


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data', default=os.environ.get('QSS_DATA', '/var/lib/qsschedule'))
    parser.add_argument('--port', type=int, default=18474)
    parser.add_argument('--public-base', default='https://huanmen.com.cn/qsschedule')
    args = parser.parse_args()
    with create_server(args.data, args.port, args.public_base) as server:
        print(f'Plugin admin: http://127.0.0.1:{server.server_port}', flush=True)
        server.serve_forever()
