"""Replace only the bar surface/frame renderer; preserve the accepted portraits byte for byte."""
import argparse,copy,hashlib,json,zipfile
from pathlib import Path

EXPECTED='6F5DE85B06322A7D09B7EB601DBD4DFB90432DAC0CFC34D675ED8B5AC05EBCE6'
REPLACED='com/tnc/tnc/bosshud/client/BossBarEngraving.class'
ENTRIES={REPLACED,'com/tnc/tnc/bosshud/client/BossBarMaterials.class',
         'assets/tnc/textures/gui/boss_hud/materials_v1.png','assets/tnc/textures/gui/boss_hud/materials_v1.png.mcmeta'}
PORTRAITS={'com/tnc/tnc/bosshud/client/BossHudPortraits.class','assets/tnc/textures/gui/boss_hud/portraits_v1.png','assets/tnc/textures/gui/boss_hud/portraits_v1.png.mcmeta'}
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest().upper()
def prepare(live,built,out,hud_baseline=None):
    base=live/'mods/tnc-1.0.0.jar';before=sha(base)
    if before!=EXPECTED:
        assert hud_baseline and sha(hud_baseline)==EXPECTED,'Formal release changed; inspect before rebasing'
        with zipfile.ZipFile(hud_baseline) as prior,zipfile.ZipFile(base) as current:
            hud_names={n for n in prior.namelist() if '/bosshud/' in n or '/boss_hud/' in n}
            assert hud_names=={n for n in current.namelist() if '/bosshud/' in n or '/boss_hud/' in n}
            assert all(prior.read(n)==current.read(n) for n in hud_names),'Concurrent HUD changes require a manual merge'
    out.mkdir(parents=True,exist_ok=False);target=out/'tnc-1.0.0.jar'
    with zipfile.ZipFile(base) as old,zipfile.ZipFile(built) as new:
        payload={name:new.read(name) for name in ENTRIES};assert ENTRIES&set(old.namelist())=={REPLACED}
        with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as merged:
            for info in old.infolist():merged.writestr(copy.copy(info),payload.get(info.filename,old.read(info.filename)))
            for name in sorted(ENTRIES-set(old.namelist())):merged.writestr(name,payload[name])
        with zipfile.ZipFile(target) as merged:
            assert merged.testzip() is None
            assert [name for name in old.namelist() if old.read(name)!=merged.read(name)]==[REPLACED]
            assert set(merged.namelist())-set(old.namelist())==ENTRIES-{REPLACED}
            assert all(merged.read(name)==data for name,data in payload.items())
            assert all(merged.read(name)==old.read(name) for name in PORTRAITS)
            unchanged=len(old.namelist())-1
    assert sha(base)==before
    manifest={'baseInstalledHash':before,'files':[{'source':str(target),'relative':'mods/tnc-1.0.0.jar','hash':sha(target),'expectedLiveHash':before}],
              'patchEntries':sorted(ENTRIES),'unrelatedEntriesByteIdentical':unchanged,'acceptedPortraitEntriesByteIdentical':sorted(PORTRAITS),
              'volcanicTortoiseDrawingUntouched':True,'combatNetworkSavesAndSettingsUntouched':True,
              'concurrentNonHudUpdatesPreserved':before!=EXPECTED}
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(manifest,ensure_ascii=False,indent=2))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--live',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--hud-baseline',type=Path)
    a=p.parse_args();prepare(a.live.resolve(),a.built.resolve(),a.output.resolve(),a.hud_baseline.resolve() if a.hud_baseline else None)
