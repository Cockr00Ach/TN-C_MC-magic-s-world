"""Overlay the verified ink package onto the latest installed mod, preserving all other entries."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest().upper()

def selected(name):
    return name.startswith(('com/tnc/tnc/world/ink/','assets/tnc/shaders/core/ink_dragon.')) or name in (
        'assets/tnc/textures/environment/ink_shanshui_atlas.png',
        'assets/tnc/textures/entity/ink_winged_tiger.png',
        'assets/tnc/meshes/ink_celestial_dragon.mesh',
        'assets/tnc/geo/entity/ink_winged_tiger.geo.json','assets/tnc/animations/entity/ink_winged_tiger.animation.json',
        'assets/tnc/lang/zh_cn.json','assets/tnc/lang/en_us.json')

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('base');parser.add_argument('update');parser.add_argument('output');parser.add_argument('report')
    args=parser.parse_args()
    base_hash=digest(args.base)
    with ZipFile(args.base) as base,ZipFile(args.update) as update:
        assert not any(n.startswith('META-INF/') and n.endswith(('.SF','.RSA','.DSA')) for n in base.namelist()),'Signed base needs a different release workflow'
        patches={n:update.read(n) for n in update.namelist() if selected(n) and not n.endswith('/')}
        for language in ('zh_cn','en_us'):
            name=f'assets/tnc/lang/{language}.json'
            if name in patches:
                original=json.loads(base.read(name))
                merged=dict(original)
                changed={'entity.tnc.ink_winged_tiger','entity.tnc.ink_celestial_dragon'}
                names=json.loads(patches[name])
                for key in changed:merged[key]=names[key]
                assert all(merged[k]==v for k,v in original.items() if k not in changed)
                patches[name]=(json.dumps(merged,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
        assert 'assets/tnc/textures/environment/ink_shanshui_atlas.png' in patches
        assert 'com/tnc/tnc/world/ink/client/InkPaintingRenderer.class' in patches
        written=set()
        with ZipFile(args.output,'w',compression=ZIP_DEFLATED) as out:
            for entry in base.infolist():
                if entry.filename in written:continue
                out.writestr(copy.copy(entry),patches.get(entry.filename,base.read(entry.filename)))
                written.add(entry.filename)
            for name,data in patches.items():
                if name not in written:out.writestr(name,data)
        unchanged=0
        with ZipFile(args.output) as merged:
            assert merged.testzip() is None
            for name in base.namelist():
                if not selected(name):
                    assert base.read(name)==merged.read(name),f'Unrelated entry changed: {name}'
                    unchanged+=1
            for name,data in patches.items():assert merged.read(name)==data
    assert digest(args.base)==base_hash,'Installed base changed during merge; retry against latest'
    report={'base':str(Path(args.base).resolve()),'baseSHA256':base_hash,'outputSHA256':digest(args.output),
            'inkEntriesUpdated':len(patches),'unrelatedEntriesByteIdentical':unchanged,
            'scope':'Ink entity package and art assets; language files only update tiger and celestial-dragon names'}
    Path(args.report).write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
