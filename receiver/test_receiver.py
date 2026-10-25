import json
import tempfile
import threading
import unittest
import urllib.request
import urllib.error
from unittest.mock import patch
from pathlib import Path
from receiver import create_server
from update_leaderboard import RULES

class ReceiverTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.payloads=[]
        def fake_gh(*args,**kwargs):
            if args[:2]==('issue','list'):return '[]'
            self.assertEqual(args[:2],('issue','create'))
            self.assertIn('Xvobuk/chunk-speedrun',args)
            body=Path(args[args.index('--body-file')+1]).read_text('utf-8')
            self.payloads.append(json.loads(body.strip()[8:-4]))
            return 'https://github.com/Xvobuk/chunk-speedrun/issues/1\n'
        self.mock=patch('receiver.gh',side_effect=fake_gh);self.mock.start()
        self.http=create_server('Xvobuk/chunk-speedrun',self.temp.name,0)
        self.thread=threading.Thread(target=self.http.serve_forever,daemon=True);self.thread.start()
        self.url=f'http://127.0.0.1:{self.http.server_port}/results'
        self.row=dict(attemptId='37ee9c96-cbe4-4f61-b99e-c56c5b47cbad',nickname='ChunkTester',activeMillis=1000,totalMillis=3000,rules=RULES,status='verified')
    def tearDown(self):
        self.http.shutdown();self.http.server_close();self.thread.join();self.mock.stop();self.temp.cleanup()
    def post(self,row):
        try:
            with urllib.request.urlopen(urllib.request.Request(self.url,json.dumps(row).encode(),{'Content-Type':'application/json'})) as response:return response.status,json.load(response)
        except urllib.error.HTTPError as response:return response.code,json.load(response)
    def test_anonymous_and_idempotent(self):
        status,result=self.post(self.row)
        self.assertEqual(status,201);self.assertEqual(result['status'],'unverified')
        self.assertNotIn('status',self.payloads[0])
        self.assertEqual(self.post(self.row)[0],200);self.assertEqual(len(self.payloads),1)
        self.assertEqual(self.post({**self.row,'activeMillis':500})[0],409)
    def test_validation(self):
        for patch in ({'nickname':'name\ninjection'},{'activeMillis':True},{'totalMillis':0},{'attemptId':'../bad'}):self.assertEqual(self.post({**self.row,**patch})[0],400)
        self.assertEqual(self.payloads,[])

if __name__=='__main__':unittest.main()
