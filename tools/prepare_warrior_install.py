"""Prepare and hash a narrow warrior update over the actual installed mod jar."""
import argparse,copy,hashlib,json,zipfile
from pathlib import Path

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest().upper()
def prepare(live,built,forge_class,output):
    base=live/'mods/tnc-1.0.0.jar';before=sha(base)
    output.mkdir(parents=True,exist_ok=False);target=output/'tnc-1.0.0.jar'
    prefixes=('com/tnc/tnc/warrior/','com/tnc/tnc/client/MagicStoneHud',
              'com/tnc/tnc/network/MagicStoneNetwork','com/tnc/tnc/production/ManaForgeRecipe')
    resources={'data/tnc/recipes/wind_weapon_infusion.json','data/tnc/tags/items/infusible_weapons.json'}
    forge='com/tnc/tnc/production/MagicForgeBlockEntity.class'
    with zipfile.ZipFile(base) as old,zipfile.ZipFile(built) as new:
        payload={n:new.read(n) for n in new.namelist() if n in resources or
                 n.endswith('.class') and n.startswith(prefixes) and 'GameTests' not in n}
        payload[forge]=forge_class.read_bytes()
        assert resources<=payload.keys()
        assert 'com/tnc/tnc/warrior/WarriorTalentRules.class' in payload
        assert not any(n.startswith('net/') for n in payload),'Dependency fixtures cannot ship'
        with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as merged:
            for info in old.infolist():merged.writestr(copy.copy(info),payload.get(info.filename,old.read(info.filename)))
            for n in sorted(payload.keys()-set(old.namelist())):merged.writestr(n,payload[n])
        with zipfile.ZipFile(target) as merged:
            assert merged.testzip() is None
            changed=[n for n in old.namelist() if old.read(n)!=merged.read(n)]
            assert set(changed)<=payload.keys()
            assert all(merged.read(n)==data for n,data in payload.items())
            unchanged=len(old.namelist())-len(changed)
    assert sha(base)==before,'Installed jar changed during preparation'
    manifest={'baseInstalledHash':before,'files':[{'source':str(target.resolve()),'relative':'mods/tnc-1.0.0.jar',
        'hash':sha(target),'expectedLiveHash':before}], 'patchEntries':sorted(payload),
        'changedExistingEntries':changed,'unrelatedEntriesByteIdentical':unchanged,
        'forgeAdapter':'Only tickForge preview-to-assembly call changed; original installed dependencies retained',
        'savesModified':False}
    (output/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
    print(json.dumps({'jar':str(target),'hash':sha(target),'updatedEntries':len(payload),
                      'unrelatedEntriesByteIdentical':unchanged},ensure_ascii=False))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--live',type=Path,required=True);p.add_argument('--built',type=Path,required=True)
    p.add_argument('--forge-class',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    prepare(a.live.resolve(),a.built.resolve(),a.forge_class.resolve(),a.output.resolve())
