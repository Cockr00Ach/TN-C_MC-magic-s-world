"""Overlay only dragon renderer/budget classes, preserving every other installed entry."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest().upper()

def selected(name):
    root = 'com/tnc/tnc/world/ink/'
    return name in (root+'InkDragonRenderBudget.class', root+'client/InkCelestialDragonRenderer.class') or (
        name.startswith(root+'client/InkDragonBufferedMesh') and name.endswith('.class'))

def main():
    parser=argparse.ArgumentParser()
    for name in ('base','update','output','report'): parser.add_argument(name)
    args=parser.parse_args()
    before=digest(args.base)
    assert Path(args.output).resolve() not in (Path(args.base).resolve(),Path(args.update).resolve())
    with ZipFile(args.base) as base, ZipFile(args.update) as update:
        patches={name:update.read(name) for name in update.namelist() if selected(name)}
        assert len(patches)==4, list(patches)
        written=set()
        with ZipFile(args.output,'w') as output:
            for entry in base.infolist():
                assert entry.filename not in written, 'Duplicate base ZIP entry'
                output.writestr(copy.copy(entry),patches.get(entry.filename,base.read(entry.filename)))
                written.add(entry.filename)
            for name,data in patches.items():
                if name not in written: output.writestr(name,data)
        with ZipFile(args.output) as output:
            assert output.testzip() is None
            untouched=[name for name in base.namelist() if name not in patches]
            assert all(base.read(name)==output.read(name) for name in untouched)
            assert all(output.read(name)==data for name,data in patches.items())
    assert digest(args.base)==before, 'Installed jar changed during merge'
    report=dict(base=args.base,baseSHA256=before,output=args.output,sha256=digest(args.output),
                patched=sorted(patches),unrelatedEntriesByteIdentical=len(untouched))
    Path(args.report).write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2))

if __name__=='__main__': main()
