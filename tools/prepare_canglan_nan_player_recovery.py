"""Prepare byte-local NBT float repairs without changing a live save or any inventory data."""
import argparse,gzip,hashlib,json,math,struct,uuid
from pathlib import Path
from check_tavern_palette import Reader

class IndexedReader(Reader):
    def __init__(self,data):super().__init__(data);self.path=();self.spans={}
    def payload(self,t):
        if t==10:
            out={}
            while True:
                child=self.u1()
                if child==0:return out
                name=self.s();old=self.path;self.path=old+(name,);out[name]=self.payload(child);self.path=old
        if t==9:
            child=self.u1();size=self.i4();out=[]
            for i in range(size):
                old=self.path;self.path=old+(i,);out.append(self.payload(child));self.path=old
            return out
        if t in (5,6):self.spans[self.path]=(self.i,4 if t==5 else 8,t)
        return super().payload(t)

def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest().upper()
def load(path):
    original=path.read_bytes();raw=gzip.decompress(original) if original[:2]==b'\x1f\x8b' else original
    reader=IndexedReader(raw);kind=reader.u1();reader.s();data=reader.payload(kind);return original,raw,reader,data
def maximum(player):
    entry=next((a for a in player.get('Attributes',[]) if a.get('Name')=='minecraft:generic.max_health'),None)
    if entry is None:return 20.0
    base=float(entry['Base']);mods=entry.get('Modifiers',[])
    result=base+sum(float(m['Amount']) for m in mods if m.get('Operation')==0)
    result*=1+sum(float(m['Amount']) for m in mods if m.get('Operation')==1)
    for m in mods:
        if m.get('Operation')==2:result*=1+float(m['Amount'])
    assert math.isfinite(result) and 0<result<=1_000_000,'Maximum-health attribute itself needs review'
    return result
def prepare(live,world,out):
    live=live.resolve();world=world.resolve();out=out.resolve()
    assert world.parent==live/'saves' and world.name=='新的世界 (15)','Unexpected recovery world'
    _,_,_,level=load(world/'level.dat');player=level['Data']['Player'];pid=str(uuid.UUID(bytes=struct.pack('>4i',*player['UUID'])))
    targets=[(world/'level.dat',('Data','Player'))]
    separate=world/'playerdata'/(pid+'.dat')
    if separate.exists():targets.append((separate,()))
    out.mkdir(parents=True,exist_ok=False);files=[];evidence=[]
    for index,(path,prefix) in enumerate(targets):
        old,raw,reader,data=load(path);p=data
        for key in prefix:p=p[key]
        repaired=bytearray(raw);changes=[];spans=[]
        for key in ('Health','AbsorptionAmount'):
            before=float(p.get(key,0))
            if math.isfinite(before):continue
            downed=p.get('ForgeData',{}).get('PlayerPersisted',{}).get('tncCombat',{}).get('downed',0)
            after=(1.0 if downed else maximum(p)) if key=='Health' else 0.0
            offset,size,kind=reader.spans[prefix+(key,)];repaired[offset:offset+size]=struct.pack('>f' if kind==5 else '>d',after)
            changes.append({'field':key,'before':str(before),'after':after});spans.append((offset,size))
        if not changes:continue
        allowed={n for start,size in spans for n in range(start,start+size)}
        assert len(raw)==len(repaired) and all(a==b or i in allowed for i,(a,b) in enumerate(zip(raw,repaired))),'Unrelated NBT bytes changed'
        target=out/('player-repair-'+str(index)+'.dat');target.write_bytes(gzip.compress(bytes(repaired),mtime=0) if old[:2]==b'\x1f\x8b' else bytes(repaired))
        _,newraw,_,newdata=load(target);np=newdata
        for key in prefix:np=np[key]
        assert all(math.isfinite(float(np[k])) for k in ('Health','AbsorptionAmount'))
        assert newraw==bytes(repaired)
        files.append({'source':str(target),'relative':str(path.relative_to(live)),'hash':digest(target),'expectedLiveHash':digest(path)})
        evidence.append({'relative':str(path.relative_to(live)),'changes':changes,'onlyFloatPayloadBytesChanged':True})
    manifest={'world':str(world),'files':files,'evidence':evidence,'inventoryEquipmentQuestsPositionUnchanged':True}
    (out/'player-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({'files':len(files),'evidence':evidence},ensure_ascii=True,indent=2))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--live',type=Path,required=True);p.add_argument('--world',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();prepare(a.live,a.world,a.output)
