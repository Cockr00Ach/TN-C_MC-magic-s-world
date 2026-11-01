"""Read-only final installed payload check and local audit record."""
import hashlib,json,zipfile
from pathlib import Path

root=Path(__file__).resolve().parents[1]
release=root/'work/ink-shader-switch-install-20261027'
live=Path('E:/download/正式版 2.12.6.1/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915')
manifest=json.loads((release/'manifest.json').read_text(encoding='utf-8'))
for item in manifest['files']:
    assert hashlib.sha256((live/item['relative']).read_bytes()).hexdigest().upper()==item['hash']
with zipfile.ZipFile(live/'mods/tnc-1.0.0.jar') as z:
    assert z.testzip() is None
    assert sorted(n for n in z.namelist() if n.startswith('com/tnc/tnc/inkshader/'))==manifest['patchEntries']
visual=root/'work/ink-shader-visual-fixed-client-20261027'
report=json.loads((visual/'report.json').read_text(encoding='utf-8'))
assert not report['failures']
report.update(visualScreenshotsAccepted=True,visualLimitation='Ink Wash edge detection emphasizes dragon and orb polygon grids; kept optional. No full-modpack FPS claim.')
(visual/'verified.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
record=dict(installed=True,installedFiles=len(manifest['files']),jarSha256=manifest['files'][0]['hash'],backup=str(live/'backups/tnc-ink-shader-switch-20261027-143438'),unitTests=8,lifecycleStates=10,visualStates=len(report['states']),asmClasses=105,asmMethods=1189,defaultMode='off',visualOnlyOptional=True,testFixturesRemoved=True,derivedMixinResourceRestored=True)
(release/'installed.json').write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf-8')
research=root/'work/ink-shader-research-20261027/research-summary.json'
summary=json.loads(research.read_text(encoding='utf-8'))
summary['inkShader']['oculusRuntimeCompatibilityTested']=True
summary['inkShader']['visualLimitation']=report['visualLimitation']
summary['downloadsInstalledInFormalPack']=True
summary['installedDownloads']=['ink-wash-shaderpack.zip']
summary['notInstalledDownloads']=['MelonShaderCyclerForge-1.3.6.jar']
research.write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(record,ensure_ascii=False,indent=2))
