"""Overlay the collaborator's dungeon mobs and data onto the current PCL pack."""
import argparse,hashlib,json,shutil,struct,subprocess,sys,zipfile
from pathlib import Path
from mob_ids import ids
import apply_dungeon_mob_lists as lists

def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest().upper()

def members(data):
    at=8
    def u2():
        nonlocal at
        value=struct.unpack_from('>H',data,at)[0];at+=2;return value
    def u4():
        nonlocal at
        value=struct.unpack_from('>I',data,at)[0];at+=4;return value
    cp=[None]*u2();i=1
    while i<len(cp):
        tag=data[at];at+=1
        if tag==1:
            size=u2();cp[i]=data[at:at+size].decode('utf-8',errors='replace');at+=size
        elif tag in (3,4,9,10,11,12,17,18):at+=4
        elif tag in (5,6):at+=8;i+=1
        elif tag in (7,8,16,19,20):at+=2
        elif tag==15:at+=3
        else:raise ValueError(tag)
        i+=1
    at+=6;interfaces=u2();at+=2*interfaces
    def section():
        nonlocal at
        result={}
        for _ in range(u2()):
            start=at;u2();name=cp[u2()];desc=cp[u2()]
            for _ in range(u2()):
                u2();size=u4();at+=size
            result[name,desc]=data[start:at]
        return result
    return section(),section()

def prepare(root,live,built,out):
    live=live.resolve();built=built.resolve();out=out.resolve()
    out.mkdir(parents=True,exist_ok=True);base=out/'base.jar';shutil.copy2(live/'mods/tnc-1.0.0.jar',base)
    species=ids();assert len(species)==17
    resources={f'assets/tnc/{kind}/{mob}{suffix}' for mob in species for kind,suffix in (
        ('geo/entity','.geo.json'),('animations/entity','.animation.json'),
        ('textures/entity','_bedrock.png'),('models/item','_spawn_egg.json'))}
    with zipfile.ZipFile(built) as z:
        families=['DungeonBoltEntity','DungeonGoals','DungeonMobRegistration','DungeonSpecies','TNDungeonMob',
                  'client/DungeonMobClient','client/DungeonMobGeoModel','client/DungeonMinionRenderer']
        classes={n for n in z.namelist() if any(n=='com/tnc/tnc/dungeon/'+f+'.class' or n.startswith('com/tnc/tnc/dungeon/'+f+'$') and n.endswith('.class') for f in families)}
        for f in families:assert 'com/tnc/tnc/dungeon/'+f+'.class' in classes,('Missing dungeon family',f)
        assert len(classes)>=9
        for n in resources:assert z.read(n)==(root/'src/main/resources'/n).read_bytes(),('Stale collaborator asset',n)
        payload={n:z.read(n) for n in resources|classes}
    java=root/'.jdk17/bin/java.exe';javac=root/'.jdk17/bin/javac.exe'
    asm=next((root/'.gradle-user/wrapper/dists').rglob('asm-9.7.jar'))
    helper=out/'helper';helper.mkdir(exist_ok=True)
    subprocess.run([str(javac),'-cp',str(asm),'-d',str(helper),str(root/'tools/WireDungeonMobs.java')],check=True)
    with zipfile.ZipFile(base) as z:
        before=z.read('com/tnc/tnc/TNMod.class');(out/'TNMod-original.class').write_bytes(before)
        subprocess.run([str(java),'-cp',str(asm)+';'+str(helper),'WireDungeonMobs',str(out/'TNMod-original.class'),str(out/'TNMod-wired.class')],check=True)
        after=(out/'TNMod-wired.class').read_bytes();fields,methods=members(before);newfields,newmethods=members(after)
        assert fields==newfields and methods.keys()==newmethods.keys()
        assert all(raw==newmethods[key] for key,raw in methods.items() if key[0]!='<init>'),'Live methods changed outside constructor'
        payload['com/tnc/tnc/TNMod.class']=after
        for lang in ['en_us','zh_cn']:
            n=f'assets/tnc/lang/{lang}.json';data=json.loads(z.read(n));source=json.loads((root/'src/main/resources'/n).read_text(encoding='utf-8-sig'))
            for mob in species:
                for key in [f'entity.tnc.{mob}',f'item.tnc.{mob}_spawn_egg']:
                    assert key in source,key
                    data[key]=source[key]
            payload[n]=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode()
        output=out/'tnc-1.0.0.jar'
        with zipfile.ZipFile(output,'w') as dst:
            dst.comment=z.comment
            for info in z.infolist():dst.writestr(info,payload.get(info.filename,z.read(info)))
            for n in sorted(payload.keys()-set(z.namelist())):dst.writestr(n,payload[n],compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(base) as a,zipfile.ZipFile(output) as b:
        assert b.testzip() is None
        assert set(a.namelist())<=set(b.namelist())
        assert all(a.read(n)==b.read(n) for n in a.namelist() if n not in payload)
        assert not [n for n in b.namelist() if n.endswith('.class') and not n.startswith('com/tnc/tnc/')]
        assert 'data/tnc/dimension/earth_arena.json' in b.namelist(),'Do not regress the earth realm recovery'
    manifest={'jar':str(output),'baseInstalledHash':digest(base),'files':[{'source':str(output),'relative':'mods\\tnc-1.0.0.jar','hash':digest(output)}]}
    data_root=Path('config/openloader/data/yuansujuexing/data/library_of_exile');seen=set();data_evidence=[]
    for theme in list(lists.REWRITE)+list(lists.APPEND):
        relative=data_root/'library_of_exile_mob_list'/(theme+'.json');current=live/relative
        data=json.loads(current.read_text(encoding='utf-8-sig'))
        if theme in lists.REWRITE:data['mobs']=[{'mob_id':m,'weight':w} for m,w in lists.REWRITE[theme]]
        else:
            have={m['mob_id'] for m in data['mobs']}
            data['mobs'] += [{'mob_id':m,'weight':w} for m,w in lists.APPEND[theme] if m not in have]
        for m in data['mobs']:
            assert m['weight']>0
            if m['mob_id'].startswith('tnc:'):
                assert m['mob_id'][4:] in species,m['mob_id'];seen.add(m['mob_id'][4:])
        target=out/'files'/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        manifest['files'].append({'source':str(target),'relative':str(relative),'hash':digest(target),'expectedLiveHash':digest(current)})
        data_evidence.append(str(relative))
    assert seen==set(species),('Missing dungeon roster species',set(species)-seen)
    for name in ['tnc_armored','tnc_vicious','tnc_fleet']:
        relative=data_root/'library_of_exile_mob_affix'/(name+'.json');source=next((root/'modpack').glob('*/'+relative.as_posix()));json.loads(source.read_text(encoding='utf-8-sig'))
        target=out/'files'/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
        manifest['files'].append({'source':str(target),'relative':str(relative),'hash':digest(target),'expectedLiveHash':digest(live/relative) if (live/relative).exists() else None})
        data_evidence.append(str(relative))
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2))
    (out/'provenance.json').write_text(json.dumps({'species':species,'baseHash':digest(base),'releaseHash':digest(output),'entries':sorted(payload),'configFiles':data_evidence,'unrelatedEntriesByteIdentical':True,'onlyConstructorRegistrationAdded':True},indent=2))
    print(f'{len(species)} mobs; {len(classes)} scoped classes; {len(resources)} assets; {len(data_evidence)} data files; SHA256 {digest(output)}')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--live',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    prepare(Path(__file__).resolve().parents[1],a.live,a.built,a.output)
