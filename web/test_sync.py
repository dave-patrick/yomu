import os
import tempfile
import unittest
os.environ['YOMU_ACCESS_TOKEN'] = 'test-only-' + 'x'*40
temporary = tempfile.TemporaryDirectory()
os.environ['YOMU_DATA_DIR'] = temporary.name
from fastapi.testclient import TestClient
from app import app

class SyncTests(unittest.TestCase):
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
