import hashlib,json,tempfile,threading,unittest
from pathlib import Path
from http.client import HTTPConnection
import portal,plugin_store

class PortalTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name)
        plugin_store.initialize(self.root,'https://huanmen.com.cn/qsschedule')
        (self.root/'update.json').write_text(json.dumps(dict(latestVersionName='1.5.3',apkUrl='https://huanmen.com.cn/qsschedule/downloads/a.apk',sizeBytes=12)))
        auth=dict(salt='00'*16,iterations=1000,hash=hashlib.pbkdf2_hmac('sha256',b'test-password',bytes(16),1000).hex())
        self.server=portal.create_server(self.root,auth,0);self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
        self.client=HTTPConnection('127.0.0.1',self.server.server_port);self.cookie=''
    def tearDown(self):
        self.client.close();self.server.shutdown();self.server.server_close();self.thread.join();self.tmp.cleanup()
    def req(self,path,body=None,extra=None):
        h={'Host':'huanmen.com.cn','X-Forwarded-Proto':'https','Content-Type':'application/json','Cookie':self.cookie};h.update(extra or {})
        self.client.request('GET' if body is None else 'POST',path,None if body is None else json.dumps(body),h)
        r=self.client.getresponse();raw=r.read();return r.status,dict(r.getheaders()),raw
    def login(self):
        s,h,_=self.req('/qsschedule/manage/api/login',{'password':'test-password'})
        self.assertEqual(s,200);self.assertIn('HttpOnly',h['Set-Cookie']);self.assertIn('Secure',h['Set-Cookie']);self.cookie=h['Set-Cookie'].split(';')[0]
    def test_private_routes_and_login(self):
        self.assertEqual(self.req('/qsschedule/manage/api/state')[0],401)
        self.assertEqual(self.req('/qsschedule/manage/')[0],302)
        self.assertEqual(self.req('/qsschedule/manage/api/login',{'password':'wrong'})[0],401)
        self.login();s,_,raw=self.req('/qsschedule/manage/api/state');self.assertEqual(s,200);self.assertIn('csrf',json.loads(raw))
        s,_,raw=self.req('/qsschedule/manage/admin.js');self.assertEqual(s,200);self.assertIn(b"'/qsschedule/manage/api/state'",raw)
        self.req('/qsschedule/manage/api/logout',{});self.assertEqual(self.req('/qsschedule/manage/api/state')[0],401)
    def test_public_read_only_and_cross_site(self):
        s,_,raw=self.req('/qsschedule/site/api/overview');self.assertEqual(s,200);self.assertNotIn('client_hash',raw.decode());self.assertNotIn('audit',json.loads(raw))
        self.assertEqual(self.req('/qsschedule/site/',extra={'Origin':'https://evil.test'})[0],403)
        self.assertEqual(self.req('/qsschedule/site/',extra={'X-Forwarded-Proto':'http'})[0],403)
    def test_csrf_and_rate_limit(self):
        self.login();self.assertEqual(self.req('/qsschedule/manage/api/save',{})[0],403)
        for _ in range(8):self.req('/qsschedule/manage/api/login',{'password':'wrong'})
        self.assertEqual(self.req('/qsschedule/manage/api/login',{'password':'wrong'})[0],429)

if __name__=='__main__':unittest.main()
