import os
import tempfile
import unittest
os.environ['YOMU_ACCESS_TOKEN'] = 'test-only-' + 'x'*40
temporary = tempfile.TemporaryDirectory()
os.environ['YOMU_DATA_DIR'] = temporary.name
from fastapi.testclient import TestClient
from app import app

class SyncTests(unittest.TestCase):
    def test_reading_cursor_is_atomic_and_separate_from_listening(self):
        import json, hashlib
        def push(kind, ident, data, base=None):
            return self.client.post('/api/sync',json={'client':'resume-test','changes':[{'kind':kind,'id':ident,'data':data,'base':base}]})
        item_id=self.client.post('/api/articles',json={'url':'https://example.com/resume-test'}).json()['id']
        anchor=json.dumps({'version':1,'textHash':hashlib.sha256(b'hello world').hexdigest(),'offset':0,'quote':'hello world','prefix':''})
        cursor={'itemId':item_id,'anchor':anchor,'updatedAt':1,'deviceLabel':'Phone'}
        self.assertEqual(push('reading_cursors',item_id,cursor).status_code,200)
        remote={**cursor,'anchor':anchor.replace('"offset": 0','"offset": 6'),'updatedAt':2,'deviceLabel':'Website'}
        push('reading_cursors',item_id,remote,cursor)
        stale={**cursor,'anchor':anchor.replace('"offset": 0','"offset": 3'),'updatedAt':3}
        result=push('reading_cursors',item_id,stale,cursor).json()
        self.assertTrue(result['conflicts'])
        self.assertEqual(next(r['data'] for r in result['records'] if r['kind']=='reading_cursors' and r['id']==item_id)['anchor'],remote['anchor'])
        self.assertFalse(any(r['kind']=='reading_progress' and r['id']==item_id for r in result['records']))
        resolved=push('reading_cursors',item_id,stale,next(r['data'] for r in result['records'] if r['kind']=='reading_cursors' and r['id']==item_id)).json()
        self.assertFalse(resolved['conflicts'])
        replay=push('reading_cursors',item_id,stale,cursor).json()
        self.assertEqual(replay['revision'],resolved['revision'])
        # An old client writing only its known listening kind leaves new cursors intact.
        listening={'itemId':item_id,'sentenceIndex':2,'characterOffset':6,'playbackSpeed':1,'completed':False,'lastListenedAt':4}
        old_client=push('reading_progress',item_id,listening).json()
        self.assertEqual(next(r['data'] for r in old_client['records'] if r['kind']=='reading_cursors' and r['id']==item_id)['anchor'],stale['anchor'])
        parent=next(r['data'] for r in old_client['records'] if r['kind']=='reading_items' and r['id']==item_id)
        deleted=push('reading_items',item_id,None,parent).json()
        self.assertIsNone(next(r['data'] for r in deleted['records'] if r['kind']=='reading_cursors' and r['id']==item_id))
        replay=push('reading_cursors',item_id,stale).json()
        self.assertIsNone(next(r['data'] for r in replay['records'] if r['kind']=='reading_cursors' and r['id']==item_id))

    def test_reader_defaults_version_and_anchor_validation(self):
        defaults={'id':'global','font':'serif','size':20,'spacing':1.7,'schemaVersion':1}
        for data,code in [(defaults,200),({**defaults,'schemaVersion':2},400),({**defaults,'size':999},400)]:
            response=self.client.post('/api/sync',json={'changes':[{'kind':'reader_defaults','id':'global','data':data,'base':None}]})
            self.assertEqual(response.status_code,code)
        response=self.client.post('/api/sync',json={'changes':[{'kind':'reading_cursors','id':'bad','data':{'itemId':'bad','anchor':'{}','updatedAt':0,'deviceLabel':'Phone'},'base':None}]})
        self.assertEqual(response.status_code,400)

    def setUp(self):
        self.client = TestClient(app)
        self.client.headers['Authorization'] = 'Bearer ' + os.environ['YOMU_ACCESS_TOKEN']
    def sync(self, ident, data, base=None):
        response = self.client.post('/api/sync', json={'client':'test','changes':[{'kind':'playlists','id':ident,'data':data,'base':base}]})
        self.assertEqual(response.status_code,200)
        return response.json()
    def test_auth_required_for_library_and_files(self):
        for path in ('/api/state','/api/files/anything'):
            self.assertEqual(TestClient(app).get(path).status_code,401)
    def test_duplicate_capture_returns_existing_identity(self):
        first=self.client.post('/api/articles',json={'url':'https://example.com/duplicate-contract'}).json()
        duplicate=self.client.post('/api/articles',json={'url':'https://example.com/duplicate-contract'}).json()
        self.assertEqual(duplicate,{'status':'already_saved','id':first['id']})
    def test_field_merge_and_same_field_conflict(self):
        base={'id':'merge','name':'Original','sortOrder':0,'createdAt':1}
        self.sync('merge',base)
        self.sync('merge',{**base,'name':'Browser'},base)
        result=self.sync('merge',{**base,'sortOrder':3},base)
        record=next(r['data'] for r in result['records'] if r['id']=='merge')
        self.assertEqual(record['name'],'Browser');self.assertEqual(record['sortOrder'],3)
        conflict=self.sync('merge',{**base,'name':'Offline phone'},base)
        self.assertTrue(conflict['conflicts'])
    def test_replay_and_delete_do_not_resurrect_offline_item(self):
        base={'id':'delete','name':'Original','createdAt':1,'sortOrder':0}
        self.sync('delete',base)
        self.sync('delete',None,base)
        result=self.sync('delete',{**base,'name':'Stale'},base)
        self.assertIsNone(next(r['data'] for r in result['records'] if r['id']=='delete'))
        replay=self.sync('delete',base,None)
        self.assertIsNone(next(r['data'] for r in replay['records'] if r['id']=='delete'))
        revision=replay['revision']
        self.assertEqual(self.sync('delete',None,base)['revision'],revision)
    def test_server_replacement_and_invalid_fields(self):
        self.assertEqual(self.client.post('/api/sync',json={'instance':'wrong','changes':[]}).status_code,409)
        self.assertEqual(self.client.post('/api/sync',json={'changes':[{'kind':'playlists','id':'bad','data':{'id':'bad','secret':'no'}}]}).status_code,400)
    def test_file_upload_and_download(self):
        ident='a68f4a1b-357c-4df3-a164-f567b1a3225a'
        self.assertEqual(self.client.put('/api/files/'+ident,content=b'hello',headers={'X-File-Name':'notes.txt'}).status_code,200)
        self.assertEqual(self.client.get('/api/files/'+ident).content,b'hello')
        self.assertEqual(self.client.put('/api/files/'+ident,content=b'x',headers={'X-File-Name':'bad.exe'}).status_code,400)

    def test_queue_can_be_readded_and_parent_deletion_cascades(self):
        def exchange(changes):
            response=self.client.post('/api/sync',json={'changes':changes})
            self.assertEqual(response.status_code,200)
            return response.json()
        item={'id':'parent','title':'Article','sourceType':'url'}
        queue={'itemId':'parent','position':0,'addedAt':1}
        def change(kind,data,base=None): return {'kind':kind,'id':'parent','data':data,'base':base}
        exchange([change('reading_items',item),change('up_next',queue)])
        exchange([change('up_next',None,queue)])
        readded=exchange([change('up_next',queue)])
        self.assertEqual(next(r['data'] for r in readded['records'] if r['kind']=='up_next' and r['id']=='parent'),queue)
        deleted=exchange([change('reading_items',None,item)])
        self.assertIsNone(next(r['data'] for r in deleted['records'] if r['kind']=='up_next' and r['id']=='parent'))

if __name__ == '__main__': unittest.main()
