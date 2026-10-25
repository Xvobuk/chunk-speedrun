"""Publish GitHub Issues submissions. Issue text is data, never executable code."""
import json
import os
import re
import urllib.request
import uuid
from pathlib import Path

RULES = '26.3-hard-air-bedrock-v1'
MAX_TIME = 10 * 365 * 24 * 3600 * 1000

def validate(value):
    if not isinstance(value, dict):
        raise ValueError('Expected object')
    result = {key: value[key] for key in ('attemptId','nickname','activeMillis','totalMillis','rules')}
    result['attemptId'] = str(uuid.UUID(result['attemptId']))
    if not isinstance(result['nickname'], str) or not re.fullmatch(r'[A-Za-z0-9_]{3,16}',result['nickname']):
        raise ValueError('Invalid nickname')
    a,t = result['activeMillis'],result['totalMillis']
    if type(a) is not int or type(t) is not int or not 0 < a <= t <= MAX_TIME:
        raise ValueError('Invalid times')
    if result['rules'] != RULES:
        raise ValueError('Unsupported rules')
    return result

def parse_issue(issue):
    match = re.fullmatch(r'ChunkSpeedrun result ([a-f0-9-]{36})',issue.get('title',''))
    if not match or 'pull_request' in issue:
        return None
    body = (issue.get('body') or '').replace('\r\n','\n').strip()
    if not body.startswith('```json\n') or not body.endswith('\n```') or len(body)>4096:
        return None
    try:
        result = validate(json.loads(body[8:-4]))
        if result['attemptId'] != match[1]:
            return None
        return {**result,'issue':issue['number']}
    except (ValueError, KeyError, TypeError):
        return None

def tables(records, moderation):
    # Confirmation can only be changed by committing moderation.json.
    # An issue label or a client-supplied status never confirms a result.
    confirmed = {r['attemptId']:validate(r) for r in moderation.get('verified',[])}
    excluded = set(moderation.get('rejected',[])) | set(moderation.get('testOnly',[]))
    verified = [{**r,'status':'verified'} for aid,r in confirmed.items() if aid not in excluded]
    unverified = [{**r,'status':'unverified'} for aid,r in records.items() if aid not in confirmed and aid not in excluded]
    order = lambda r:(r['activeMillis'],r['totalMillis'],r['attemptId'])
    return dict(rules=RULES,verified=sorted(verified,key=order)[:50],unverified=sorted(unverified,key=order)[:50])

def issues(repo, token):
    page = 1
    while True:
        req = urllib.request.Request(f'https://api.github.com/repos/{repo}/issues?state=all&per_page=100&page={page}',headers={'Authorization':f'Bearer {token}','Accept':'application/vnd.github+json','User-Agent':'ChunkSpeedrun-leaderboard'})
        with urllib.request.urlopen(req,timeout=30) as response:
            batch = json.load(response)
        yield from batch
        if len(batch)<100:
            break
        page += 1

def main():
    root = Path(__file__).resolve().parent.parent
    records_file = root/'records.json'
    records = json.loads(records_file.read_text('utf-8')) if records_file.exists() else {}
    for issue in issues(os.environ['GH_REPOSITORY'],os.environ['GH_TOKEN']):
        row = parse_issue(issue)
        if row is not None:
            # First accepted payload is immutable even if its issue is edited later.
            records.setdefault(row['attemptId'],row)
    moderation = json.loads((root/'moderation.json').read_text('utf-8'))
    for filename,data in (('records.json',records),('leaderboard.json',tables(records,moderation))):
        (root/filename).write_text(json.dumps(data,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')

if __name__=='__main__':
    main()
