"""Prepare a narrow execution overlay. Do not overwrite the installed boss entity wholesale."""
import argparse,copy,hashlib,json,subprocess,zipfile
from pathlib import Path
from prepare_dungeon_mob_install import members

ROOT=Path(__file__).resolve().parents[1]
EXPECTED='F269B1F4F9F1C87F0F309BB1CCBB91A531C7AC8FBC453148A52C739092AAAB90'
FAMILIES=['CanglanWarlordCombat','CanglanWarGodRules','CanglanExecutionPose','CanglanExecutionPins','CanglanCinematicNetwork','CanglanTestCommands','client/CanglanExecutionClient','client/CanglanWarlordModel','client/CanglanCinematicCamera']
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest().upper()
def selected(name):return any(name=='com/tnc/tnc/world/wind/'+n+'.class' or name.startswith('com/tnc/tnc/world/wind/'+n+'$') and name.endswith('.class') for n in FAMILIES)

def prepare(live,built,out):
    base=live/'mods/tnc-1.0.0.jar';before=digest(base);assert before==EXPECTED,'Formal jar changed; inspect before merging'
    out.mkdir(parents=True,exist_ok=False)
    asm=next((ROOT/'.gradle-user/wrapper/dists').rglob('asm-9.7.jar'));java=ROOT/'.jdk17/bin'
    subprocess.run([str(java/'javac.exe'),'-cp',str(asm),'-d',str(out),str(ROOT/'tools/WireCanglanExecutionInstall.java')],check=True)
    with zipfile.ZipFile(base) as a,zipfile.ZipFile(built) as b:
        payload={n:b.read(n) for n in b.namelist() if selected(n)}
        for family in FAMILIES:assert 'com/tnc/tnc/world/wind/'+family+'.class' in payload,family
        name='com/tnc/tnc/world/wind/CanglanWarlordEntity.class'
        src=out/'entity-base.class';donor=out/'entity-donor.class';patched=out/'entity-patched.class'
        src.write_bytes(a.read(name));donor.write_bytes(b.read(name))
        subprocess.run([str(java/'java.exe'),'-cp',str(asm)+';'+str(out),'WireCanglanExecutionInstall',str(src),str(donor),str(patched)],check=True)
        fields,methods=members(a.read(name));newfields,newmethods=members(patched.read_bytes())
        assert all(newfields[k]==v for k,v in fields.items()),'Installed entity fields changed'
        assert all(newmethods[k]==v for k,v in methods.items() if k[0]!='registerControllers'),'Unrelated installed entity methods changed'
        payload[name]=patched.read_bytes()
        geo='assets/tnc/geo/entity/canglan_warlord.geo.json'
        assert json.loads(a.read(geo))==json.loads(b.read(geo)),'Installed rig differs from authored authoritative pose'
        animation='assets/tnc/animations/entity/canglan_warlord.animation.json'
        old=json.loads(a.read(animation));fresh=json.loads(b.read(animation))
        assert 'execution_impale' not in old['animations'],'New clip already installed'
        old['animations']['execution_impale']=fresh['animations']['execution_impale']
        payload[animation]=(json.dumps(old,ensure_ascii=False,indent=2)+'\n').encode()
        kin='assets/tnc/poses/canglan_execution.json';payload[kin]=b.read(kin)
        target=out/'tnc-1.0.0.jar'
        with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
            for info in a.infolist():z.writestr(copy.copy(info),payload.get(info.filename,a.read(info.filename)))
            for n in sorted(payload.keys()-set(a.namelist())):z.writestr(n,payload[n])
        with zipfile.ZipFile(target) as z:
            assert z.testzip() is None
            unchanged=[n for n in a.namelist() if n not in payload]
            assert all(a.read(n)==z.read(n) for n in unchanged)
            assert all(z.read(n)==data for n,data in payload.items())
            assert not any(n.endswith('.class') and not n.startswith('com/tnc/tnc/') for n in z.namelist())
            assert all(v==json.loads(z.read(animation))['animations'][k] for k,v in json.loads(a.read(animation))['animations'].items())
    assert digest(base)==before
    manifest={'baseInstalledHash':before,'files':[{'source':str(target),'relative':'mods/tnc-1.0.0.jar','hash':digest(target),'expectedLiveHash':before}],
              'patchEntries':sorted(payload),'unrelatedEntriesByteIdentical':len(unchanged),'entityChange':'registerControllers only; all other installed fields/methods byte-identical','oldAnimationTracksPreserved':True}
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'entries':len(payload),'unchanged':len(unchanged),'hash':digest(target),'manifest':str(out/'manifest.json')},indent=2))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--live',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--output',type=Path,required=True);args=p.parse_args();prepare(args.live.resolve(),args.built.resolve(),args.output.resolve())
