import test from 'node:test';
import assert from 'node:assert/strict';
import worker, { validate } from './worker.mjs';

const RULES = '26.3-hard-air-bedrock-v1';
const row = { attemptId:'bf381b30-e5ed-4770-982b-c2e28e3b5e8b', nickname:'ChunkTester', activeMillis:1000, totalMillis:2000, rules:RULES };
const env = { GITHUB_TOKEN:'unit-test-not-a-real-token' };
const request = (value=row, headers={}) => new Request('https://test.invalid/results',{ method:'POST',headers:{'Content-Type':'application/json',...headers},body:JSON.stringify(value) });

test('accepts only result fields and never a client confirmation',()=>{
  assert.deepEqual(validate({...row,status:'verified',repository:'attacker/repo',labels:['verified']}),row);
  for(const patch of [{activeMillis:true},{activeMillis:0},{totalMillis:999},{nickname:'a\nb'},{attemptId:'../bad'},{rules:'other'}])assert.throws(()=>validate({...row,...patch}));
});
test('health, missing configuration, method and size limits',async()=>{
  const health = await worker.fetch(new Request('https://test.invalid/health'),{});
  assert.equal((await health.json()).status,'needs-configuration');
  assert.equal((await worker.fetch(request(),{})).status,503);
  assert.equal((await worker.fetch(new Request('https://test.invalid/results'),env)).status,405);
  assert.equal((await worker.fetch(new Request('https://test.invalid/other'),env)).status,404);
  assert.equal((await worker.fetch(request({...row,extra:'x'.repeat(5000)}),env)).status,413);
});
test('anonymous issue creation, duplicate, conflict and GitHub failure',async()=>{
  const original=globalThis.fetch; const calls=[];let mode='create';
  globalThis.fetch=async(url,options={})=>{
    assert.equal(options.redirect,'manual'); // Workers rejects redirect: error before sending.
    calls.push({url,options});
    if(url.includes('raw.githubusercontent.com'))return Response.json({});
    if(mode==='failure')return Response.json({message:'credential detail must stay private'},{status:401});
    if(options.method==='POST')return Response.json({html_url:'https://github.com/Xvobuk/chunk-speedrun/issues/999'},{status:201});
    if(mode==='create')return Response.json([]);
    const prior=mode==='conflict'?{...row,activeMillis:500}:row;
    return Response.json([{title:'ChunkSpeedrun result '+row.attemptId,body:'```json\r\n'+JSON.stringify(prior)+'\r\n```\r\n',html_url:'https://github.com/Xvobuk/chunk-speedrun/issues/999'}]);
  };
  try{
    const result=await worker.fetch(request({...row,status:'verified'}),env);
    assert.equal(result.status,201);assert.equal((await result.json()).status,'unverified');
    const sent=JSON.parse(calls.find(c=>c.options.method==='POST').options.body);
    assert.equal(sent.title,'ChunkSpeedrun result '+row.attemptId);assert.ok(!sent.body.includes('verified'));assert.equal(sent.labels,undefined);
    mode='duplicate';assert.equal((await worker.fetch(request(),env)).status,200);
    mode='conflict';assert.equal((await worker.fetch(request(),env)).status,409);
    mode='failure';const failed=await worker.fetch(request(),env);assert.equal(failed.status,503);assert.ok(!(await failed.text()).includes('credential detail'));
    assert.ok(calls.filter(c=>c.url.includes('api.github.com')).every(c=>c.options.headers.Authorization==='Bearer '+env.GITHUB_TOKEN));
  } finally {globalThis.fetch=original;}
});
test('old ledger result is idempotent without GitHub writes',async()=>{
  const original=globalThis.fetch;let calls=0;
  globalThis.fetch=async()=>{calls++;return Response.json({[row.attemptId]:{...row,issue:10}});};
  try{assert.equal((await worker.fetch(request(),env)).status,200);assert.equal(calls,1);}
  finally{globalThis.fetch=original;}
});
