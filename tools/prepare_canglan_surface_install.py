"""Two-resource overlay; never installs combat classes, animations or unrelated assets."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'model-source/canglan_warlord/surface-refinement-20261022'
def digest(data):return hashlib.sha256(data).hexdigest().upper()

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--base',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=True)
    before=digest(args.base.read_bytes())
    geo='assets/tnc/geo/entity/canglan_warlord.geo.json'
    texture='assets/tnc/textures/entity/canglan_warlord.png'
    animation='assets/tnc/animations/entity/canglan_warlord.animation.json'
    patches={name:(ROOT/'src/main/resources'/name).read_bytes() for name in (geo,texture)}
    target=args.output/'tnc-1.0.0.jar'
    with ZipFile(args.base) as base:
        assert base.read(geo)==(SOURCE/'before.geo.json').read_bytes(),'Installed geometry changed: reconcile before applying'
        assert base.read(animation)==(SOURCE/'unchanged.animation.json').read_bytes(),'Installed animation differs from refinement baseline'
        with ZipFile(target,'w',ZIP_DEFLATED) as output:
            for entry in base.infolist():output.writestr(copy.copy(entry),patches.get(entry.filename,base.read(entry.filename)))
        with ZipFile(target) as output:
            assert output.testzip() is None
            assert set(output.namelist())==set(base.namelist())
            assert all(output.read(name)==base.read(name) for name in base.namelist() if name not in patches)
            assert all(output.read(name)==data for name,data in patches.items())
        untouched=len(base.namelist())-len(patches)
    assert digest(args.base.read_bytes())==before,'Base jar changed during merge'
    report=dict(base=str(args.base),baseSHA256=before,output=str(target.resolve()),sha256=digest(target.read_bytes()),
                patches=list(patches),unrelatedEntriesByteIdentical=untouched,animationPreservedByteForByte=True)
    (args.output/'verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
