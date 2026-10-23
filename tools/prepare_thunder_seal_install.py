"""Scope the new thunder chain onto the current installed mod, preserving every other entry."""
from pathlib import Path
import argparse,hashlib,json,subprocess,zipfile
from prepare_dungeon_mob_install import members
ROOT=Path(__file__).resolve().parents[1]
NAMES=['雷楔烙印','双极鸣雷','三才雷锁','六极霆狱','九霄天刑']
DESCS=['雷楔从地面显现，蓄势后向雷印落点导通并引下落雷。','双雷楔交叉导通，连续两次放电后集中引爆。','三楔构成雷锁，放电减速阵内敌人，再以落雷收束。','六楔展开霆狱与高空雷环，四轮脉冲后落雷引爆。','九楔五次导通后升空收拢，牵引九道天雷汇成中央天刑；不破坏地形。']
TRANSLATIONS={lang:{} for lang in ['zh_cn','en_us']}
for i,n in enumerate(NAMES,1):
 TRANSLATIONS['zh_cn'][f'spell.tnc.thunder_seal_{i}.name']=n
 TRANSLATIONS['zh_cn'][f'spell.tnc.thunder_seal_{i}.description']=DESCS[i-1]
 TRANSLATIONS['en_us'][f'spell.tnc.thunder_seal_{i}.name']=['Thunder Brand','Bipolar Discharge','Triad Lightning Lock','Sixfold Thunder Prison','Ninefold Sky Judgment'][i-1]
 TRANSLATIONS['en_us'][f'spell.tnc.thunder_seal_{i}.description']=f'{[1,2,3,6,9][i-1]} physical conductors pulse {i} times, then converge into a final lightning strike. Aimed location stays fixed; allies and walls are respected.'
TRANSLATIONS['zh_cn']['entity.tnc.thunder_seal']='霆狱雷阵';TRANSLATIONS['en_us']['entity.tnc.thunder_seal']='Thunder Seal'
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
def prepare(live,built,out):
 out.mkdir(parents=True,exist_ok=True);base=live/'mods/tnc-1.0.0.jar';before=digest(base)
 asm=next((ROOT/'.gradle-user/wrapper/dists').rglob('asm-9.7.jar'));java=ROOT/'.jdk17/bin'
 subprocess.run([str(java/'javac.exe'),'-cp',str(asm),'-d',str(out),str(ROOT/'tools/WireBossMusicMethod.java')],check=True)
 with zipfile.ZipFile(base) as src,zipfile.ZipFile(built) as donor:
  names=[n for n in donor.namelist() if n.startswith('com/tnc/tnc/magic/thunderseal/') and n.endswith('.class') and 'GameTests' not in n and 'ClientAudit' not in n]
  names += [n for n in donor.namelist() if n.startswith(('data/tnc/spells/thunder_seal_','assets/tnc/textures/spell/thunder_seal_','assets/tnc/models/spell/thunder_seal_','assets/tnc/models/item/thunder_seal_'))]
  assert all(n not in src.namelist() for n in names),'New chain already exists; merge instead'
  assert b'light_mirror_t1' not in donor.read('com/tnc/tnc/magic/SpellCatalog.class'),'Unpublished mirror entries leaked into build'
  payload={n:donor.read(n) for n in names};chain='com/tnc/tnc/magic/SpellCatalog$Chain.class'
  # Old enum constants remain a prefix; new constants are appended only.
  oldfields,_=members(src.read(chain));newfields,_=members(donor.read(chain));assert set(oldfields)<=set(newfields)
  descriptor="Lcom/tnc/tnc/magic/SpellCatalog$Chain;";oldconstants=[k for k in oldfields if k[1]==descriptor];newconstants=[k for k in newfields if k[1]==descriptor];assert newconstants[:len(oldconstants)]==oldconstants,"Saved chain ordinal changed"
  payload[chain]=donor.read(chain)
  n='com/tnc/tnc/magic/SpellCatalog.class';a=out/'catalog-original.class';b=out/'catalog-donor.class';c=out/'catalog-patched.class';a.write_bytes(src.read(n));b.write_bytes(donor.read(n))
  subprocess.run([str(java/'java.exe'),'-cp',str(asm)+';'+str(out),'WireBossMusicMethod',str(a),str(b),str(c),'<clinit>'],check=True)
  f,m=members(src.read(n));nf,nm=members(c.read_bytes());assert f==nf and m.keys()==nm.keys();assert all(raw==nm[k] for k,raw in m.items() if k[0]!='<clinit>');payload[n]=c.read_bytes()
  n='data/tnc/spell_pools/tnc_lightning.json';pool=json.loads(src.read(n));pool['spell_ids']=list(dict.fromkeys(pool['spell_ids']+[f'tnc:thunder_seal_{i}' for i in range(1,6)]));payload[n]=json.dumps(pool,indent=2).encode()
  for lang,changes in TRANSLATIONS.items():
   n='assets/tnc/lang/'+lang+'.json';d=json.loads(src.read(n));d.update(changes);payload[n]=json.dumps(d,ensure_ascii=False,indent=2).encode()
  jar=out/'tnc-1.0.0.jar'
  with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as dst:
   for entry in src.infolist():dst.writestr(entry,payload.get(entry.filename,src.read(entry)))
   for n,data in payload.items():
    if n not in src.namelist():dst.writestr(n,data)
 with zipfile.ZipFile(base) as src,zipfile.ZipFile(jar) as dst:
  assert dst.testzip() is None;assert all(src.read(n)==dst.read(n) for n in src.namelist() if n not in payload)
 assert digest(base)==before
 files=[{'source':str(jar.resolve()),'relative':'mods\\tnc-1.0.0.jar','hash':digest(jar)}]
 # Some packs put a higher-priority pool in KubeJS. Merge it only when it exists.
 rel='kubejs/data/tnc/spell_pools/tnc_lightning.json';existing=live/rel
 if existing.exists():
  d=json.loads(existing.read_text(encoding='utf-8-sig'));d['spell_ids']=list(dict.fromkeys(d['spell_ids']+[f'tnc:thunder_seal_{i}' for i in range(1,6)]));p=out/'kubejs-lightning-pool.json';p.write_text(json.dumps(d,indent=2),encoding='utf-8');files.append({'source':str(p.resolve()),'relative':rel.replace('/','\\'),'hash':digest(p),'expectedLiveHash':digest(existing)})
 (out/'manifest.json').write_text(json.dumps({'jar':str(jar.resolve()),'baseInstalledHash':before,'files':files},indent=2),encoding='utf-8')
 (out/'provenance.json').write_text(json.dumps({'entries':list(payload),'baseHash':before,'releaseHash':digest(jar)},indent=2),encoding='utf-8');print(digest(jar))
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--live',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();prepare(a.live,a.built,a.output)
