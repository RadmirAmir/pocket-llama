#!/usr/bin/env python3
"""Exercise the actual pinned GGUF on synthetic RU/EN retrieval fixtures. Host CPU, not a phone benchmark."""
import argparse,hashlib,json,math,pathlib,resource,socket,statistics,subprocess,time,urllib.request,uuid
ROOT=pathlib.Path(__file__).resolve().parents[1]
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--server',required=True);p.add_argument('--model',required=True);p.add_argument('--report',default=str(ROOT/'build/rag-live.json'));a=p.parse_args()
    pin=json.loads((ROOT/'rag-model.lock.json').read_text());model=pathlib.Path(a.model)
    with model.open('rb') as f:assert hashlib.file_digest(f,'sha256').hexdigest()==pin['sha256'],'Model checksum'
    fixture=json.loads((ROOT/'tests/fixtures/rag-retrieval.json').read_text());key=uuid.uuid4().hex
    with socket.socket() as s:s.bind(('127.0.0.1',0));port=s.getsockname()[1]
    report=pathlib.Path(a.report);report.parent.mkdir(parents=True,exist_ok=True);log=report.with_suffix('.log').open('w');started=time.monotonic()
    child=subprocess.Popen([a.server,'--model',str(model),'--host','127.0.0.1','--port',str(port),'--api-key-file','/proc/self/fd/0','--embeddings','--pooling','mean','--embd-normalize','2','--ctx-size','2048','--batch-size','2048','--ubatch-size','2048','--parallel','1','--threads','3','--device','none','--offline','--no-webui','--log-verbosity','0'],stdin=subprocess.PIPE,stdout=log,stderr=log)
    child.stdin.write((key+'\n').encode());child.stdin.close();http=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    def call(path,data=None):
        req=urllib.request.Request('http://127.0.0.1:'+str(port)+path,data=None if data is None else json.dumps(data).encode(),headers={'Content-Type':'application/json','Authorization':'Bearer '+key})
        return json.load(http.open(req,timeout=90))
    def embed(text):
        ids=call('/tokenize',{'content':text,'add_special':True,'parse_special':False})['tokens'];assert len(ids)<=2048
        vector=call('/v1/embeddings',{'input':ids,'encoding_format':'float'})['data'][0]['embedding'];assert len(vector)==768 and all(math.isfinite(v) for v in vector);assert abs(sum(v*v for v in vector)-1)<1e-4
        return vector
    timings=[];results=[]
    try:
        for _ in range(450):
            if child.poll() is not None:raise RuntimeError('Embedding runtime exited')
            try:call('/props');break
            except Exception:time.sleep(.2)
        else:raise RuntimeError('Embedding runtime not ready')
        load=time.monotonic()-started;vectors=[]
        for doc in fixture['documents']:vectors.append(embed('title: '+doc['title']+' | text: '+doc['text']))
        for item in fixture['queries']:
            t=time.monotonic();v=embed('task: search result | query: '+item['text']);timings.append((time.monotonic()-t)*1000)
            scores=[sum(x*y for x,y in zip(v,d)) for d in vectors];rank=sorted(range(len(scores)),key=lambda i:scores[i],reverse=True);position=rank.index(item['expected'])+1
            results.append({'query':item['text'],'expected':item['expected'],'rank':position,'cosine':round(scores[item['expected']],4)})
        top1=sum(r['rank']==1 for r in results);top3=sum(r['rank']<=3 for r in results)
        assert top1>=8 and top3==len(results),results
    finally:child.terminate();child.wait(timeout=15);log.close()
    value={'modelSha256':pin['sha256'],'runtimeCommit':(ROOT/'llama.lock').read_text().strip(),'platform':'Linux x86_64 host CPU, 3 threads; not Android performance','documents':len(vectors),'queries':len(results),'top1':top1,'top3':top3,'loadSeconds':round(load,3),'medianQueryMs':round(statistics.median(timings),2),'peakChildRssKiB':resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss,'results':results}
    report.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n');print(json.dumps({k:v for k,v in value.items() if k!='results'},ensure_ascii=False))
if __name__=='__main__':main()
