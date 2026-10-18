"""Local compatibility build: retain Conquest blocks, remove vanilla appearance overrides.

Uses the unmodified Minecraft 1.20.1 asset inventory. Custom Conquest assets and
non-vanilla texture dependencies are preserved byte for byte. Does not modify input.
"""
from pathlib import Path
import argparse, hashlib, json, zipfile

def prepare(source, vanilla, output):
    if source.resolve()==output.resolve():raise ValueError('Output must differ from input')
    with zipfile.ZipFile(vanilla) as base:
        original={p for p in base.namelist() if p.startswith('assets/minecraft/')}
    removed=[];retained=0
    output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(source) as src, zipfile.ZipFile(output,'w') as dst:
        for info in src.infolist():
            name=info.filename;index=name.find('assets/minecraft/')
            asset=name[index:] if index>=0 else ''
            # Includes opt-in bundled resource packs; keep custom dependency assets.
            if asset and not info.is_dir() and (asset in original or asset.startswith('assets/minecraft/optifine/')):
                removed.append(name);continue
            dst.writestr(info,src.read(name));retained+=1
    with zipfile.ZipFile(source) as src,zipfile.ZipFile(output) as dst:
        if dst.testzip() is not None:raise ValueError('Corrupt output')
        for name in dst.namelist():
            if dst.read(name)!=src.read(name):raise ValueError('Unexpected content change: '+name)
        for leaf in ('oak','spruce','birch','jungle','acacia','dark_oak','mangrove','cherry','azalea','flowering_azalea'):
            path='assets/minecraft/blockstates/'+leaf+'_leaves.json'
            if path in dst.namelist():raise ValueError('Leaf override remains: '+path)
        assert 'assets/conquest/blockstates/round_barrel.json' in dst.namelist()
    report={'source':str(source),'output':str(output),'removed_count':len(removed),'retained_count':retained,
            'retained_entries_byte_identical':True,'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
            'output_sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'removed':removed}
    output.with_suffix('.report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
    print(json.dumps({k:v for k,v in report.items() if k!='removed'},ensure_ascii=False))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('vanilla',type=Path);p.add_argument('output',type=Path)
    a=p.parse_args();prepare(a.source,a.vanilla,a.output)
