"""Anonymous result receiver; GitHub Issues is the only persistent result store."""
import json
import os
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
sys.path.insert(0,str(Path(__file__).resolve().parent.parent/'scripts'))
from update_leaderboard import validate

def gh(*args, input_data=None):
    binary = Path(os.environ.get('ProgramFiles','C:/Program Files'))/'GitHub CLI/gh.exe'
    executable = str(binary) if binary.is_file() else 'gh'
    env = {**os.environ,'GH_PROMPT_DISABLED':'1'}
    result = subprocess.run([executable,*args],input=input_data,text=True,encoding='utf-8',capture_output=True,timeout=40,env=env,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
    if result.returncode:
        raise RuntimeError('GitHub request failed')
    return result.stdout

def create_server(repository,runtime,port=24873):
    lock=threading.Lock()
    accepted={}
    windows={}
    runtime=Path(runtime)
    runtime.mkdir(parents=True,exist_ok=True)
    class Handler(BaseHTTPRequestHandler):
        server_version='ChunkSpeedrunReceiver/1'
        def setup(self):
            super().setup(); self.connection.settimeout(10)
        def log_message(self,*args): pass
        def reply(self,code,value):
            body=json.dumps(value,ensure_ascii=False).encode('utf-8')
            self.send_response(code);self.send_header('Content-Type','application/json; charset=utf-8');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
        def do_GET(self):
            self.reply(200,dict(status='ok',repository=repository)) if self.path=='/health' else self.reply(404,dict(error='Not found'))
        def do_POST(self):
            if self.path!='/results':return self.reply(404,dict(error='Not found'))
            try:
                size=int(self.headers.get('Content-Length','0'))
                if not 0<size<=4096:return self.reply(413,dict(error='Invalid body size'))
                row=validate(json.loads(self.rfile.read(size)))
            except (ValueError,KeyError,TypeError,UnicodeError):return self.reply(400,dict(error='Invalid result'))
            aid=row['attemptId']
            with lock:
                if aid in accepted:
                    prior,url=accepted[aid]
                    return self.reply(200,dict(issue=url,status='unverified')) if prior==row else self.reply(409,dict(error='Attempt already exists'))
                # IP addresses are used only in memory for abuse limits, never saved.
                peer=self.headers.get('CF-Connecting-IP',self.client_address[0])
                now=time.monotonic();hour=int(now//3600)
                for key in (peer,'*'):
                    previous,count=windows.get(key,(hour,0))
                    if previous!=hour:count=0
                    if count>=(60 if key==peer else 240):return self.reply(429,dict(error='Try later'))
                for key in (peer,'*'):
                    previous,count=windows.get(key,(hour,0));windows[key]=(hour,count+1 if previous==hour else 1)
                title='ChunkSpeedrun result '+aid
                try:
                    recent=json.loads(gh('issue','list','--repo',repository,'--state','all','--limit','1000','--json','title,url,body'))
                    existing=next((i for i in recent if i['title']==title),None)
                    if existing:
                        body=existing['body'].strip()
                        old=validate(json.loads(body[8:-4])) if body.startswith('```json\n') and body.endswith('\n```') else None
                        if old!=row:return self.reply(409,dict(error='Attempt already exists'))
                        url=existing['url'];code=200
                    else:
                        with tempfile.TemporaryDirectory(dir=runtime) as directory:
                            bodyfile=Path(directory)/'result.md';bodyfile.write_text('```json\n'+json.dumps(row,ensure_ascii=False,indent=2)+'\n```\n',encoding='utf-8')
                            url=gh('issue','create','--repo',repository,'--title',title,'--body-file',str(bodyfile)).strip();code=201
                    accepted[aid]=(row,url)
                    self.reply(code,dict(issue=url,status='unverified'))
                except Exception:
                    self.reply(503,dict(error='GitHub unavailable; keep local result and retry later'))
    server=ThreadingHTTPServer(('127.0.0.1',port),Handler)
    server.daemon_threads=True
    return server
