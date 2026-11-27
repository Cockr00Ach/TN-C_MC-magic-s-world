"""Audit against the five active Forge tags and the jar the instance is loading."""
from collections import Counter
import math,re,sys
from zipfile import ZipFile
import numpy as np
from roster import *

def validate():
    game=Path(sys.argv[1]) if len(sys.argv)>1 else Path('D:/垃圾桶/PCL 正式版 2.9.3/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915')
    counts=Counter();cost={};texture_set=set()
    with ZipFile(game/'mods/tnc-1.0.0.jar') as z:
        def exists(ref):
            f=loc(ref,'textures','.png')
            return (PACK/f).is_file() or f.as_posix() in z.namelist() or ref.startswith('minecraft:')
        for tag in FAMILIES:
            path='data/tnc/tags/items/'+tag+'.json'
            assert json.loads(z.read(path))==json.loads((RES/path).read_bytes()),tag
        for tag,k in all_ids():
            ref='tnc:item/'+k;r=loc(ref,'models','.json');assert z.read(r.as_posix())==(RES/r).read_bytes(),k
            counts[tag]+=1;assert (PACK/r).is_file(),k
            m=model(ref);old=model(ref,False)
            expected=old.get('display',{})
            if old.get('parent')=='minecraft:item/handheld':
                with ZipFile(game/(game.name+'.jar')) as vanilla:
                    expected={**json.loads(vanilla.read('assets/minecraft/models/item/generated.json'))['display'],
                              **json.loads(vanilla.read('assets/minecraft/models/item/handheld.json'))['display'],**expected}
            current=m.get('display',{})
            def normalize(slots):
                return {s:{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1],**v} for s,v in slots.items()}
            assert normalize(current)==normalize(expected),(k,expected,current)
            if old.get('tnc_grip') is not None:assert np.allclose(old['tnc_grip'],m['tnc_grip']),k
            if old.get('overrides'):assert old['overrides']==m.get('overrides'),k
            assert len(m.get('elements',[]))>0,k
            if tag=='longbows':
                o=m['overrides'];assert len(o)==3,k
                selections=[]
                for e in o:
                    sub=model(e['model']);assert sub.get('elements') and sub.get('display')==model(e['model'],False).get('display'),e
                    selections.append(json.dumps(sub['elements']))
                assert len(set(selections))==3,k
        for path in sorted((PACK/'assets/tnc/models/item').rglob('*.json')):
            src=json.loads(path.read_bytes());es=src.get('elements',[])
            item=path.stem;cost[path.relative_to(PACK).as_posix()]=[len(es),sum(len(e['faces']) for e in es)]
            if item in ('bronze_halberd','bronze_twin_off','bronze_twin_main'):
                assert all(e.get('rotation',{}).get('angle',0)==0 and len(e['faces'])==6 for e in es),item
            if not es:continue
            used=set()
            for e in es:
                lo,hi=e['from'],e['to'];assert all(-16<=a<b<=32 for a,b in zip(lo,hi)),(item,lo,hi)
                rot=e.get('rotation')
                if rot:assert rot['axis'] in ('x','y','z') and rot['angle'] in (-45,-22.5,0,22.5,45),item
                assert 0<len(e['faces'])<=6,(item,e)
                for f in e['faces'].values():
                    assert all(math.isfinite(c) and -.0001<=c<=16.0001 for c in f['uv']),(item,f)
                    used.add(tex_ref(f['texture'],src['textures']))
            for tex in used:
                if tex not in texture_set:assert exists(tex),(path,tex)
                texture_set.add(tex)
        keys=re.search(r'IDS\s*=\s*Set\.of\((.*?)\);',(REPO/'src/main/java/com/tnc/tnc/warrior/client/WarriorGhostModels.java').read_text(encoding='utf8'),re.S)
        ghosts=set(re.findall(r'"([a-z0-9_]+)"',keys[1]))
        actual={p.stem for p in (PACK/'assets/tnc/models/item/warrior_ghost').glob('*.json')}
        assert ghosts==actual,(ghosts-actual,actual-ghosts)
    print('Family counts:',dict(counts),'bow pull variants: 15; ghost models:',len(ghosts),'valid cuboid assets:',len(cost))
    print('All authored cuboids/faces:',sum(v[0] for v in cost.values()),sum(v[1] for v in cost.values()),'referenced textures:',len(texture_set))
    assert sum(counts.values())==45
    return cost
if __name__=='__main__':validate()
