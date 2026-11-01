"""Patch only the icon renderer and its static atlas onto the current formal release."""
import argparse,copy,hashlib,json,zipfile
from pathlib import Path

EXPECTED='239411A9FE2413BBF91AF66BD3B5E5DC9D34D9524C1C556D63E970C9E477C096'
REPLACED='com/tnc/tnc/bosshud/client/BossBarEngraving.class'
ENTRIES={REPLACED,'com/tnc/tnc/bosshud/client/BossHudPortraits.class',
         'assets/tnc/textures/gui/boss_hud/portraits_v1.png',
         'assets/tnc/textures/gui/boss_hud/portraits_v1.png.mcmeta'}
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest().upper()
def prepare(live,built,out):
    source=live/'mods/tnc-1.0.0.jar';before=sha(source);assert before==EXPECTED,'Formal release changed; inspect before rebasing'
    out.mkdir(parents=True,exist_ok=False);target=out/'tnc-1.0.0.jar'
    with zipfile.ZipFile(source) as old,zipfile.ZipFile(built) as fresh:
        payload={name:fresh.read(name) for name in ENTRIES}
        assert ENTRIES&set(old.namelist())=={REPLACED}
        with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as merged:
            for info in old.infolist():merged.writestr(copy.copy(info),payload.get(info.filename,old.read(info.filename)))
            for name in sorted(ENTRIES-set(old.namelist())):merged.writestr(name,payload[name])
        with zipfile.ZipFile(target) as merged:
            assert merged.testzip() is None
            changed=[name for name in old.namelist() if old.read(name)!=merged.read(name)]
            assert changed==[REPLACED],changed
            assert all(merged.read(name)==content for name,content in payload.items())
            assert set(merged.namelist())-set(old.namelist())==ENTRIES-{REPLACED}
            unchanged=len(old.namelist())-1
    assert sha(source)==before
    manifest={'baseInstalledHash':before,'files':[{'source':str(target),'relative':'mods/tnc-1.0.0.jar','hash':sha(target),'expectedLiveHash':before}],
              'patchEntries':sorted(ENTRIES),'unrelatedEntriesByteIdentical':unchanged,'volcanicTortoiseDrawingUntouched':True,
              'staticAtlasOnlyNoRuntimeModelsOrEntitySpawning':True,'combatNetworkSavesAndSettingsUntouched':True}
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(manifest,ensure_ascii=False,indent=2))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--live',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();prepare(a.live.resolve(),a.built.resolve(),a.output.resolve())
