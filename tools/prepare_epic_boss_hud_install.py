"""Install only the new HUD module onto the latest formal jar; never ship a whole dirty-workspace build."""
import argparse,copy,hashlib,json,zipfile
from pathlib import Path

EXPECTED='0EE4D348DFF5039904187607456863718EC33A77AD309EE0F6A57BAE9F7E38E9'
ROOTS=['com/tnc/tnc/bosshud/'+p for p in ('BossHudTheme','BossHudRules','BossHudNetwork','ServerBossHud','client/EpicBossHud','client/EpicBossBarArt')]
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
def selected(name):return any(name==p+'.class' or name.startswith(p+'$') and name.endswith('.class') for p in ROOTS)
def prepare(live,built,out):
    base=live/'mods/tnc-1.0.0.jar';before=digest(base);assert before==EXPECTED,'Formal mod changed: inspect and rebase the scoped HUD patch'
    out.mkdir(parents=True,exist_ok=False)
    with zipfile.ZipFile(base) as a,zipfile.ZipFile(built) as b:
        payload={n:b.read(n) for n in b.namelist() if selected(n)}
        assert len(payload)==13,list(payload)  # Includes javac's enum-switch table used by the thirteen motifs.
        for n in payload:assert n not in a.namelist(),'HUD module already installed: do not overwrite blindly'
        target=out/'tnc-1.0.0.jar'
        with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
            for info in a.infolist():z.writestr(copy.copy(info),a.read(info.filename))
            for name,data in sorted(payload.items()):z.writestr(name,data)
        with zipfile.ZipFile(target) as z:
            assert z.testzip() is None
            assert all(a.read(n)==z.read(n) for n in a.namelist())
            assert all(z.read(n)==data for n,data in payload.items())
            assert not any('Audit' in n or 'GameTests' in n for n in payload)
            unchanged=len(a.namelist())
    assert digest(base)==before
    manifest={'baseInstalledHash':before,'files':[{'source':str(target),'relative':'mods/tnc-1.0.0.jar','hash':digest(target),'expectedLiveHash':before}],
              'patchEntries':sorted(payload),'unrelatedEntriesByteIdentical':unchanged,'allPreviouslyInstalledEntriesByteIdentical':True,'bossCombatClassesAndHealthUntouched':True,'savesUntouched':True}
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'entriesAdded':len(payload),'unchanged':unchanged,'hash':digest(target)},indent=2))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--live',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();prepare(a.live.resolve(),a.built.resolve(),a.output.resolve())
