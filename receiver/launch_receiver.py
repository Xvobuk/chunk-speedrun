import argparse
import base64
import json
import re
import subprocess
import threading
import time
import urllib.request
from pathlib import Path
from receiver import gh,create_server

def publish(repository,url):
    path=f'repos/{repository}/contents/submission-endpoint.json'
    current=json.loads(gh('api',path))
    value=json.dumps(dict(url=url,updatedAt=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())),indent=2)+'\n'
    payload=dict(message='Update anonymous submission receiver',content=base64.b64encode(value.encode()).decode(),branch='main',sha=current['sha'])
    gh('api','--method','PUT',path,'--input','-',input_data=json.dumps(payload))

def main():
    p=argparse.ArgumentParser();p.add_argument('--repo',default='Xvobuk/chunk-speedrun');p.add_argument('--runtime',required=True);p.add_argument('--cloudflared',required=True);p.add_argument('--port',type=int,default=8765);a=p.parse_args()
    try:
        with urllib.request.urlopen(f'http://127.0.0.1:{a.port}/health',timeout=2) as response:
            if json.load(response).get('repository')==a.repo:
                print('Receiver is already running',flush=True);return
    except Exception:pass
    server=create_server(a.repo,a.runtime,a.port)
    threading.Thread(target=server.serve_forever,daemon=True).start()
    runtime=Path(a.runtime);runtime.mkdir(parents=True,exist_ok=True)
    try:
        while True:
            command=[a.cloudflared,'tunnel','--url',f'http://127.0.0.1:{a.port}','--no-autoupdate']
            process=subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
            published=False
            try:
                for line in process.stdout:
                    print(line.rstrip(),flush=True)
                    match=re.search(r'https://[a-z0-9-]+\.trycloudflare\.com',line)
                    if match and not published:
                        url=match[0]
                        for attempt in range(4):
                            try:publish(a.repo,url);published=True;break
                            except Exception:time.sleep(3)
                        if published:
                            (runtime/'endpoint.json').write_text(json.dumps(dict(url=url,pid=process.pid)),encoding='utf-8');print('Receiver URL published to GitHub: '+url,flush=True)
                process.wait()
            finally:
                if process.poll() is None:process.terminate()
            print('Tunnel stopped; retrying in 10 seconds',flush=True);time.sleep(10)
    finally:server.shutdown();server.server_close()

if __name__=='__main__':main()
