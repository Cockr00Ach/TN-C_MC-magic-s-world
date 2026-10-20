"""Add the collaborator's Canglan boss to a prepared dungeon patch, preserving other entries."""
from pathlib import Path
import json,zipfile,hashlib,argparse

def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
def prepare(folder,built,root):
    manifest=json.loads((folder/'manifest.json').read_text())
    provenance=json.loads((folder/'provenance.json').read_text())
    target=folder/'tnc-1.0.0.jar'
    payload={}
    with zipfile.ZipFile(built) as z:
        for n in z.namelist():
            if n.startswith('com/tnc/tnc/world/wind/') and '/CanglanWarlord' in n and n.endswith('.class') and 'GameTests' not in n:
                payload[n]=z.read(n)
    assert len(payload)>=7, list(payload)
    for n in ['geo/entity/canglan_warlord.geo.json','animations/entity/canglan_warlord.animation.json','textures/entity/canglan_warlord.png','models/item/canglan_warlord_spawn_egg.json']:
        p=root/'src/main/resources/assets/tnc'/n
        if n.startswith('models/item/') and not p.exists():
            p.write_text('{"parent":"minecraft:item/template_spawn_egg"}\n')
        payload['assets/tnc/'+n]=p.read_bytes()
    with zipfile.ZipFile(target) as before:
        for lang in ['en_us','zh_cn']:
            n=f'assets/tnc/lang/{lang}.json';data=json.loads(before.read(n));source=json.loads((root/'src/main/resources'/n).read_text(encoding='utf-8-sig'))
            for k in ['entity.tnc.canglan_warlord','item.tnc.canglan_warlord_spawn_egg']:data[k]=source[k]
            payload[n]=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode()
        candidate=folder/'combined.jar'
        with zipfile.ZipFile(candidate,'w') as dst:
            for info in before.infolist():dst.writestr(info,payload.get(info.filename,before.read(info)))
            for n in payload.keys()-set(before.namelist()):dst.writestr(n,payload[n],compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(target) as original, zipfile.ZipFile(candidate) as after:
            assert after.testzip() is None
            assert all(original.read(n)==after.read(n) for n in original.namelist() if n not in payload)
    candidate.replace(target)
    provenance['entries']=sorted(set(provenance['entries'])|set(payload));provenance['releaseHash']=digest(target);provenance['canglanBossClasses']=len([n for n in payload if n.endswith('.class')])
    manifest['files'][0]['hash']=digest(target)
    (folder/'manifest.json').write_text(json.dumps(manifest,indent=2));(folder/'provenance.json').write_text(json.dumps(provenance,indent=2))
    print('Combined dungeon + Canglan:',digest(target),'boss classes',provenance['canglanBossClasses'])
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('folder',type=Path);p.add_argument('built',type=Path);a=p.parse_args();prepare(a.folder,a.built,Path(__file__).resolve().parents[1])
