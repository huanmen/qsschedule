import hashlib
import importlib
import json
import os
from pathlib import Path
import tempfile
import threading
import unittest
from http.client import HTTPConnection

import plugin_admin
import plugin_store as store
from http.server import ThreadingHTTPServer


class PluginAdminTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.server = plugin_admin.create_server(self.root, 0, 'https://example.org/qsschedule')
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.client = HTTPConnection('127.0.0.1', self.server.server_port, timeout=5)

    def tearDown(self):
        self.client.close()
        self.server.shutdown(); self.server.server_close(); self.thread.join()
        self.temp.cleanup()

    def request(self, path, body=None, headers=None):
        method = 'GET' if body is None else 'POST'
        h = {'Content-Type':'application/json','X-QSS-CSRF':self.server.csrf}
        h.update(headers or {})
        self.client.request(method, path, json.dumps(body) if body is not None else None, h)
        response = self.client.getresponse()
        return response.status, json.loads(response.read()) if path != '/' else response.read()

    def draft(self, id='school.rules'):
        return dict(id=id,name='校园规则',summary='本地整理',kind='adskip-rule-pack',version=1,minAppVersion=13,minAdapterVersion=1,rulesUrl='https://example.org/rules.json',package=None,notes='初版')

    def row(self, id):
        return next(p for p in store.records(self.root) if p['id']==id)

    def test_add_save_publish_unpublish_and_republish(self):
        draft=self.draft()
        self.assertEqual(self.request('/api/save',dict(data=draft,revision=0))[0],200)
        self.assertNotIn(draft['id'],[p['id'] for p in store.catalog(self.root)])
        self.assertEqual(self.request('/api/publish',dict(data={'id':draft['id']},revision=1))[0],200)
        self.assertIn(draft['id'],[p['id'] for p in store.catalog(self.root)])
        draft['name']='草稿改名'
        store.mutate(self.root,'save',draft,2,'https://example.org/qsschedule')
        self.assertEqual(self.row(draft['id'])['published']['name'],'校园规则')
        store.mutate(self.root,'unpublish',{'id':draft['id']},3,'https://example.org/qsschedule')
        self.assertNotIn(draft['id'],[p['id'] for p in store.catalog(self.root)])
        store.mutate(self.root,'publish',{'id':draft['id']},4,'https://example.org/qsschedule')
        self.assertEqual(self.row(draft['id'])['published']['name'],'草稿改名')
        self.assertEqual(len([a for a in store.audit(self.root) if a['plugin']==draft['id']]),5)

    def test_stale_revision_cannot_overwrite(self):
        draft=self.draft();store.mutate(self.root,'save',draft,0,'https://example.org')
        status,data=self.request('/api/save',dict(data=draft,revision=0))
        self.assertEqual(status,409)
        self.assertIn('其他窗口',data['error'])

    def test_removed_vpn_stays_out_of_catalog_and_cannot_be_republished(self):
        base='https://example.org/qsschedule'
        draft=dict(self.draft('campus-vpn'),kind='builtin')
        with store.connection(self.root) as db:
            db.execute('INSERT INTO plugins VALUES (?,?,?,?,?,?)', ('campus-vpn',json.dumps(draft),json.dumps(dict(draft,publicBase=base)),'published',1,store.now()))
        self.assertNotIn('campus-vpn',[p['id'] for p in store.catalog(self.root)])
        store.initialize(self.root,base)
        with store.connection(self.root) as db:
            self.assertEqual(db.execute("SELECT status FROM plugins WHERE id='campus-vpn'").fetchone()[0],'unpublished')
        self.assertNotIn('campus-vpn',[p['id'] for p in store.records(self.root)])
        with self.assertRaisesRegex(ValueError,'已移除'):
            store.mutate(self.root,'publish',{'id':'campus-vpn'},2,base)

    def test_cross_site_and_unknown_host_cannot_read_or_write(self):
        body=dict(data=self.draft(),revision=0)
        for headers in [{'Origin':'https://evil.example'}, {'Host':'evil.example'}, {'X-QSS-CSRF':'wrong'},{'Sec-Fetch-Site':'cross-site'}]:
            self.assertEqual(self.request('/api/save',body,headers)[0],403)
        self.assertEqual(self.request('/api/state',headers={'Host':'evil.example'})[0],403)
        self.assertEqual(self.request('/api/state',headers={'Origin':'https://evil.example'})[0],403)
        self.assertNotIn('school.rules',[p['id'] for p in store.records(self.root)])

    def test_unsupported_native_and_invalid_sources_rejected(self):
        for draft in [dict(self.draft(),kind='builtin'),dict(self.draft(),rulesUrl='http://example.org/rules.json'),dict(self.draft(),version=True),dict(self.draft(),id='../file'),dict(self.draft(),minAdapterVersion=4)]:
            self.assertEqual(self.request('/api/save',dict(data=draft,revision=0))[0],400)

    def academic(self, version=1, title='赛事资料'):
        payload={'schemaVersion':1,'pluginId':'academic-records','pluginVersion':version,'data':{'records':[{'id':'event-1','name':title}], 'aliases':{'赛事':'event-1'}}}
        package=store.upload(self.root,json.dumps(payload,ensure_ascii=False).encode())
        draft=self.row('academic-records')['draft'];draft.update(version=version,package=package)
        return draft

    def test_immutable_releases_and_history_restore(self):
        base='https://example.org/qsschedule'
        draft=self.academic();store.mutate(self.root,'save',draft,1,base);store.mutate(self.root,'publish',{'id':'academic-records'},2,base)
        modified=self.academic(title='修改赛事')
        store.mutate(self.root,'save',modified,3,base)
        with self.assertRaisesRegex(ValueError,'增加版本号'):
            store.mutate(self.root,'publish',{'id':'academic-records'},4,base)
        old=next(a for a in store.audit(self.root) if a['plugin']=='academic-records' and a['action']=='发布')
        self.assertEqual(store.snapshot(self.root,old['seq'])['package'],draft['package'])
        modified=self.academic(version=2,title='修改赛事');store.mutate(self.root,'save',modified,4,base);store.mutate(self.root,'publish',{'id':'academic-records'},5,base)
        self.assertEqual(self.row('academic-records')['published']['version'],2)
        draft=self.academic(version=1);store.mutate(self.root,'save',draft,6,base)
        with self.assertRaisesRegex(ValueError,'最高版本'):
            store.mutate(self.root,'publish',{'id':'academic-records'},7,base)

    def test_public_download_bytes_and_unpublished_package_blocked(self):
        draft=self.academic();base='https://example.org/qsschedule';store.mutate(self.root,'save',draft,1,base);store.mutate(self.root,'publish',{'id':'academic-records'},2,base)
        os.environ['QSS_DATA']=str(self.root)
        import app
        app=importlib.reload(app)
        server=ThreadingHTTPServer(('127.0.0.1',0),app.Handler);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        client=HTTPConnection('127.0.0.1',server.server_port,timeout=5)
        def get(path, method='GET'):
            client.request(method,path);r=client.getresponse();return r.status,r.read(),r.getheader('Content-Length')
        try:
            status,raw,_=get('/downloads/qsschedule-academic-records-1.json')
            self.assertEqual(status,200);self.assertEqual(hashlib.sha256(raw).hexdigest(),draft['package']['sha256'])
            self.assertEqual(get('/downloads/qsschedule-academic-records-1.json','HEAD')[1],b'')
            self.assertEqual(get('/api/state')[0],404)
            self.assertEqual(get('/api/plugins/academic-records')[0],200)
            store.mutate(self.root,'unpublish',{'id':'academic-records'},3,base)
            self.assertEqual(get('/downloads/qsschedule-academic-records-1.json')[0],404)
            self.assertEqual(get('/api/plugins/academic-records')[0],404)
        finally:
            client.close();server.shutdown();server.server_close();thread.join()

    def test_rule_edit_validation_and_content_addressing(self):
        raw=json.dumps(dict(schemaVersion=2,version=1,name='规则',source='公开资料',rules=[dict(id='test-rule',name='跳过',packageName='com.example.app',selectors=[dict(text='跳过')])])).encode()
        package=store.upload(self.root,raw)
        self.assertEqual(package,store.upload(self.root,raw))
        draft=dict(self.draft(),package=package,rulesUrl='')
        store.validate(self.root,draft,True)
        invalid=json.loads(raw);invalid['rules'].append(invalid['rules'][0]);draft['package']=store.upload(self.root,json.dumps(invalid).encode())
        with self.assertRaisesRegex(ValueError,'重复'):
            store.validate(self.root,draft,True)

    def test_package_upload_and_read_and_schema_failure(self):
        status,package=self.request('/api/upload',{'anything':'可编辑'})
        self.assertEqual(status,200)
        self.assertEqual(self.request('/api/package?sha='+package['sha256'])[1],{'anything':'可编辑'})
        draft=self.row('academic-records')['draft'];draft['package']=package
        self.assertEqual(self.request('/api/save',dict(data=draft,revision=1))[0],400)
        self.assertEqual(self.request('/api/package?sha=../private')[0],404)


if __name__=='__main__':
    unittest.main()
