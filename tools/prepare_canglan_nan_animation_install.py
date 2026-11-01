"""Overlay health/resync changes on the installed spear execution, plus prepared offline float repairs."""
import argparse,copy,hashlib,json,subprocess,zipfile
from pathlib import Path
from prepare_dungeon_mob_install import members

ROOT=Path(__file__).resolve().parents[1]
EXPECTED='11EF40DE551320AE526BB0D5E8C115FAFCF1C220A9AE66377643271BF3520F38'
FAMILIES=['CanglanExecutionHealth','CanglanWarlordCombat','CanglanCinematicNetwork','CanglanExecutionPins','client/CanglanExecutionClient','client/CanglanWarlordModel']
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
def selected(n):return any(n=='com/tnc/tnc/world/wind/'+f+'.class' or n.startswith('com/tnc/tnc/world/wind/'+f+'$') and n.endswith('.class') for f in FAMILIES)
def prepare(live,built,recovery,out):
    base=live/'mods/tnc-1.0.0.jar';before=digest(base);assert before==EXPECTED,'Formal jar changed; inspect before merging'
    data=json.loads(recovery.read_text(encoding='utf-8'));out.mkdir(parents=True,exist_ok=False)
    asm=next((ROOT/'.gradle-user/wrapper/dists').rglob('asm-9.7.jar'));java=ROOT/'.jdk17/bin'
    subprocess.run([str(java/'javac.exe'),'-cp',str(asm),'-d',str(out),str(ROOT/'tools/WireCanglanRendererResync.java')],check=True)
    with zipfile.ZipFile(base) as a,zipfile.ZipFile(built) as b:
        payload={n:b.read(n) for n in b.namelist() if selected(n)}
        for family in FAMILIES:assert 'com/tnc/tnc/world/wind/'+family+'.class' in payload,family
        renderer='com/tnc/tnc/world/wind/client/CanglanWarlordRenderer.class'
        old=out/'renderer-base.class';donor=out/'renderer-donor.class';patched=out/'renderer-patched.class';old.write_bytes(a.read(renderer));donor.write_bytes(b.read(renderer))
        subprocess.run([str(java/'java.exe'),'-cp',str(asm)+';'+str(out),'WireCanglanRendererResync',str(old),str(donor),str(patched)],check=True)
        fields,methods=members(a.read(renderer));nf,nm=members(patched.read_bytes());assert all(nf[k]==v for k,v in fields.items())
        chosen=lambda k:k[0]=='renderRecursively' and 'Lcom/tnc/tnc/world/wind/CanglanWarlordEntity;' in k[1]
        assert all(nm[k]==v for k,v in methods.items() if not chosen(k)),'Unrelated installed renderer methods changed'
        payload[renderer]=patched.read_bytes();target=out/'tnc-1.0.0.jar'
        with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
            for info in a.infolist():z.writestr(copy.copy(info),payload.get(info.filename,a.read(info.filename)))
            for n in sorted(payload.keys()-set(a.namelist())):z.writestr(n,payload[n])
        with zipfile.ZipFile(target) as z:
            assert z.testzip() is None
            unchanged=[n for n in a.namelist() if n not in payload];assert all(a.read(n)==z.read(n) for n in unchanged)
            assert all(z.read(n)==v for n,v in payload.items())
            for n in ('assets/tnc/animations/entity/canglan_warlord.animation.json','assets/tnc/poses/canglan_execution.json','com/tnc/tnc/world/wind/CanglanWarlordEntity.class'):
                assert z.read(n)==a.read(n),'Existing animation, pose or entity changed'
    files=[{'source':str(target),'relative':'mods/tnc-1.0.0.jar','hash':digest(target),'expectedLiveHash':before}]+data['files']
    for f in data['files']:
        assert digest(Path(f['source']))==f['hash'] and digest(live/f['relative'])==f['expectedLiveHash'],'Player data changed; stop and re-prepare'
    assert digest(base)==before
    manifest={'baseInstalledHash':before,'files':files,'patchEntries':sorted(payload),'unrelatedEntriesByteIdentical':len(unchanged),
              'recoveryWorld':data['world'],'playerRecoveryEvidence':data['evidence'],'rendererScope':'typed renderRecursively only; all other fields and methods retained',
              'oldExecutionAssetsByteIdentical':True,'inventoryEquipmentQuestsPositionUnchanged':True}
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'entries':len(payload),'unchanged':len(unchanged),'files':len(files),'hash':digest(target)},indent=2))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--live',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--recovery',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();prepare(a.live.resolve(),a.built.resolve(),a.recovery.resolve(),a.output.resolve())
