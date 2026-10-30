"""Overlay only lossless dragon rendering and screen-effect ownership fixes."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED

ROOTS=('com/tnc/tnc/world/ink/InkDragonTopology',
       'com/tnc/tnc/world/ink/InkDragonRenderBudget',
       'com/tnc/tnc/world/ink/InkRealmClient',
       'com/tnc/tnc/world/ink/client/InkDragonBufferedMesh',
       'com/tnc/tnc/world/ink/client/InkCelestialDragonRenderer',
       'com/tnc/tnc/world/ink/client/InkShaderPass',
       'com/tnc/tnc/world/ink/client/InkScreenEffects')

def selected(name):return any(name==root+'.class' or name.startswith(root+'$') and name.endswith('.class') for root in ROOTS)
def digest(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest().upper()

def main():
    p=argparse.ArgumentParser()
    for key in ('base','update','output','report'):p.add_argument(key,type=Path)
    args=p.parse_args();before=digest(args.base)
    assert args.output.resolve() not in (args.base.resolve(),args.update.resolve())
    with ZipFile(args.base) as base,ZipFile(args.update) as update:
        patches={name:update.read(name) for name in update.namelist() if selected(name)}
        assert len(patches)==11,list(patches)
        with ZipFile(args.output,'w',ZIP_DEFLATED) as output:
            for entry in base.infolist():output.writestr(copy.copy(entry),patches.get(entry.filename,base.read(entry.filename)))
            for name,data in patches.items():
                if name not in base.namelist():output.writestr(name,data)
        with ZipFile(args.output) as output:
            assert output.testzip() is None
            untouched=[n for n in base.namelist() if n not in patches]
            assert all(base.read(n)==output.read(n) for n in untouched)
            assert all(output.read(n)==data for n,data in patches.items())
    assert digest(args.base)==before,'Installed mod changed during merge'
    report=dict(base=str(args.base),baseSHA256=before,output=str(args.output.resolve()),sha256=digest(args.output),
                patches=sorted(patches),unrelatedEntriesByteIdentical=len(untouched),meshAssetByteIdentical=True)
    args.report.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
