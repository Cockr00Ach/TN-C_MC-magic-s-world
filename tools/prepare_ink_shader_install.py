"""Add the isolated client switcher and the original verified shader. Never replace existing code or options."""
import argparse,copy,hashlib,json,zipfile
from pathlib import Path

ROOTS=['com/tnc/tnc/inkshader/'+name for name in ('InkShaderSession','InkShaderFiles','InkShaderOculus','InkShaderClient')]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest().upper()
def selected(name):return any(name==r+'.class' or name.startswith(r+'$') and name.endswith('.class') for r in ROOTS)
def prepare(live,built,shader,versions,out):
    base=live/'mods/tnc-1.0.0.jar';before=sha(base);out.mkdir(parents=True,exist_ok=False)
    metadata=json.loads(versions.read_text(encoding='utf-8'));download=next(f for f in metadata[0]['files'] if f['filename']==shader.name)
    assert hashlib.sha512(shader.read_bytes()).hexdigest()==download['hashes']['sha512']
    with zipfile.ZipFile(shader) as z:assert z.testzip() is None and 'shaders/final.fsh' in z.namelist()
    with zipfile.ZipFile(base) as old,zipfile.ZipFile(built) as new:
        payload={n:new.read(n) for n in new.namelist() if selected(n)}
        assert len(payload)==8 and not any('Audit' in n for n in payload),sorted(payload)
        assert not any(n.startswith('com/tnc/tnc/inkshader/') for n in old.namelist()),'Switcher already installed; inspect before updating'
        target=out/'tnc-1.0.0.jar'
        with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as merged:
            for info in old.infolist():merged.writestr(copy.copy(info),old.read(info.filename))
            for name,data in sorted(payload.items()):merged.writestr(name,data)
        with zipfile.ZipFile(target) as merged:
            assert merged.testzip() is None and all(merged.read(n)==old.read(n) for n in old.namelist())
            assert all(merged.read(n)==data for n,data in payload.items())
            unchanged=len(old.namelist())
    assert sha(base)==before
    shader_target=live/'shaderpacks'/shader.name
    files=[{'source':str(target),'relative':'mods/tnc-1.0.0.jar','hash':sha(target),'expectedLiveHash':before},
           {'source':str(shader),'relative':'shaderpacks/'+shader.name,'hash':sha(shader),'expectedLiveHash':sha(shader_target) if shader_target.exists() else None}]
    manifest={'baseInstalledHash':before,'files':files,'patchEntries':sorted(payload),'previousEntriesByteIdentical':unchanged,
              'defaultInkMode':'off','clientCommand':'/tncinkshader off|ink|status','source':'https://modrinth.com/shader/ink-wash-shaderpack',
              'shaderVersion':metadata[0]['version_number'],'shaderOriginalSha512Verified':True,'oculusAndComplementaryConfigurationsNotOverwritten':True,
              'existingShaderPacksAndSavesUntouched':True,'recoveryJournal':'config/tnc-ink-shader-session.json'}
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(manifest,ensure_ascii=False,indent=2))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--live',type=Path,required=True);p.add_argument('--built',type=Path,required=True);p.add_argument('--shader',type=Path,required=True);p.add_argument('--versions',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();prepare(a.live.resolve(),a.built.resolve(),a.shader.resolve(),a.versions.resolve(),a.output.resolve())
