"""HTTPS portal gateway. Never expose the trusted local admin port directly."""
import hashlib,hmac,json,secrets,time,threading,os
from http.cookies import SimpleCookie,CookieError
from pathlib import Path
from urllib.parse import urlsplit
from http.server import ThreadingHTTPServer
from plugin_admin import AdminHandler
from download_stats import report
import plugin_store

ASSETS=Path(__file__).with_name('portal')
class PortalHandler(AdminHandler):
    def respond(self,value,status=200,mime='application/json; charset=utf-8'):
        if getattr(self,'_internal_admin',False) and isinstance(value,bytes) and ('text/html' in mime or 'javascript' in mime):
            value=value.replace(b'"/admin.',b'"/qsschedule/manage/admin.').replace(b'"/stats',b'"/qsschedule/manage/stats').replace(b'href="/"',b'href="/qsschedule/manage/"')
            value=value.replace(b"'/api/",b"'/qsschedule/manage/api/")
            if 'text/html' in mime:
                value=value.replace(b'</head>',b'<link rel="stylesheet" href="/qsschedule/site/portal.css"><script defer src="/qsschedule/site/manage.js"></script></head>')
        super().respond(value,status,mime)

    def local(self):
        if getattr(self,'_internal_admin',False):
            return True
        # This check replaces localhost trust with authenticated HTTPS sessions.
        if self.headers.get('Host')!=self.server.host or self.headers.get('X-Forwarded-Proto')!='https':
            self.respond({'error':'仅通过指定 HTTPS 网站访问'},403);return False
        origin=self.headers.get('Origin')
        if (origin and origin!='https://'+self.server.host) or self.headers.get('Sec-Fetch-Site')=='cross-site':
            self.respond({'error':'不允许跨站访问'},403);return False
        return True

    def authorized(self):
        try:
            c=SimpleCookie();c.load(self.headers.get('Cookie',''));token=c['qss_admin'].value
        except (KeyError,ValueError,CookieError):return False
        with self.server.lock:
            expiry=self.server.sessions.get(token,0)
            if expiry<time.time():self.server.sessions.pop(token,None);return False
        return True

    def cookie_response(self,value,token='',status=200):
        raw=json.dumps(value).encode();self.send_response(status)
        self.send_header('Content-Type','application/json');self.send_header('Cache-Control','no-store')
        self.send_header('Set-Cookie',f'qss_admin={token}; Path=/qsschedule/manage/; HttpOnly; Secure; SameSite=Strict; Max-Age={28800 if token else 0}')
        self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)

    def do_GET(self):
        if not self.local():return
        path=urlsplit(self.path).path
        if path=='/qsschedule/site/api/overview':
            stats=report(self.server.root)
            plugins=[]
            for p in plugin_store.catalog(self.server.root) or []:
                total=sum(r['count'] for r in stats['completedDownloads'] if r['kind']==p['id'])
                plugins.append({k:p[k] for k in ('id','name','summary','version')}|dict(completed=total,installations=stats['uniqueInstallations'].get(p['id'],0)))
            update=json.loads((self.server.root/'update.json').read_text())
            return self.respond(dict(stats=stats,plugins=plugins,app={k:update[k] for k in ('latestVersionName','apkUrl','sizeBytes')}))
        files={'/qsschedule/site/':('index.html','text/html; charset=utf-8'),'/qsschedule/site/portal.css':('portal.css','text/css'),'/qsschedule/site/portal.js':('portal.js','text/javascript'),'/qsschedule/site/manage.js':('manage.js','text/javascript'),'/qsschedule/manage/login':('login.html','text/html; charset=utf-8'),'/qsschedule/manage/login.js':('login.js','text/javascript')}
        if path in files:
            name,mime=files[path];return self.respond((ASSETS/name).read_bytes(),mime=mime)
        if path.startswith('/qsschedule/manage/'):
            if not self.authorized():
                if '/api/' in path:return self.respond({'error':'登录已过期，请重新登录'},401)
                self.send_response(302);self.send_header('Location','/qsschedule/manage/login');self.end_headers();return
            if path=='/qsschedule/manage/api/state':
                stats=report(self.server.root)
                return self.respond(dict(plugins=plugin_store.records(self.server.root),audit=plugin_store.audit(self.server.root),csrf=self.server.csrf,publicBase=self.server.public_base,dataDirectory=str(self.server.root),downloads={r['kind']:sum(x['count'] for x in stats['completedDownloads'] if x['kind']==r['kind']) for r in stats['completedDownloads']}))
            self.path=self.path.removeprefix('/qsschedule/manage')
            self._internal_admin=True
            try:return super().do_GET()
            finally:self._internal_admin=False
        self.respond({'error':'页面不存在'},404)

    def do_POST(self):
        if not self.local():return
        path=urlsplit(self.path).path
        if path=='/qsschedule/manage/api/login':
            try:
                length=int(self.headers.get('Content-Length','0'))
                if not 0<length<=1024 or self.headers.get_content_type()!='application/json':raise ValueError()
                body=json.loads(self.rfile.read(length))
                if not isinstance(body,dict):raise ValueError()
                password=body.get('password','')
                if not isinstance(password,str) or len(password)>200:raise ValueError()
            except (ValueError,TypeError):return self.respond({'error':'登录请求无效'},400)
            ip=self.headers.get('X-Real-IP','unknown');now=time.time()
            with self.server.lock:
                self.server.attempts={k:v for k,v in self.server.attempts.items() if v[0]>now-900}
                start,count=self.server.attempts.get(ip,(now,0))
                if count>=8:return self.respond({'error':'尝试过多，请 15 分钟后再试'},429)
                self.server.attempts[ip]=(start,count+1)
            config=self.server.credentials
            digest=hashlib.pbkdf2_hmac('sha256',password.encode(),bytes.fromhex(config['salt']),config['iterations']).hex()
            if not hmac.compare_digest(digest,config['hash']):return self.respond({'error':'密码不正确'},401)
            token=secrets.token_urlsafe(32)
            with self.server.lock:
                self.server.sessions={k:v for k,v in self.server.sessions.items() if v>now}
                self.server.sessions[token]=now+28800;self.server.attempts.pop(ip,None)
            return self.cookie_response({'ok':True},token)
        if not self.authorized():return self.respond({'error':'请先登录'},401)
        if path=='/qsschedule/manage/api/logout':
            c=SimpleCookie();c.load(self.headers.get('Cookie',''))
            with self.server.lock:self.server.sessions.pop(c['qss_admin'].value,None)
            return self.cookie_response({'ok':True})
        if not path.startswith('/qsschedule/manage/api/'):return self.respond({'error':'接口不存在'},404)
        self.path=self.path.removeprefix('/qsschedule/manage')
        self._internal_admin=True
        try:return super().do_POST()
        finally:self._internal_admin=False

def create_server(root,credentials,port=18475,host='huanmen.com.cn'):
    server=ThreadingHTTPServer(('127.0.0.1',port),PortalHandler)
    server.root=Path(root);server.host=host;server.public_base='https://'+host+'/qsschedule'
    server.csrf=secrets.token_urlsafe(32);server.credentials=credentials
    server.sessions={};server.attempts={};server.lock=threading.Lock()
    return server

if __name__=='__main__':
    create_server(os.environ.get('QSS_DATA','/var/lib/qsschedule'),json.loads(Path('/etc/qsschedule/portal-auth.json').read_text())).serve_forever()
