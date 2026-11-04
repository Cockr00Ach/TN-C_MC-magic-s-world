"""Layer verified warrior outputs over a known installed TN-C build.

Preserves installed local music and previously shipped teammate classes. The
ownership list is explicit; neither the working tree nor an entire build folder
is blindly packaged. No installed file is written by this tool.
"""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def main():
    parser=argparse.ArgumentParser()
    for name in ('base','base-sha256','built','files','out'):parser.add_argument('--'+name,required=True)
    args=parser.parse_args();base=Path(args.base);built=Path(args.built);out=Path(args.out)
    assert out.resolve() not in (base.resolve(),built.resolve()),'Never overwrite an input artifact'
    assert hashlib.sha256(base.read_bytes()).hexdigest().lower()==args.base_sha256.lower(),'Installed base changed'
    root=Path(__file__).resolve().parents[2]
    files=json.loads(Path(args.files).read_text('utf8'))
    classes=[name.removeprefix('src/main/java/').removesuffix('.java') for name in files
             if name.startswith('src/main/java/') and name.endswith('.java')]
    resources={name.removeprefix('src/main/resources/'):root/name for name in files if name.startswith('src/main/resources/')}
    def owned_class(name):return any(name==c+'.class' or name.startswith(c+'$') and name.endswith('.class') for c in classes)
    replaced=[]
    with zipfile.ZipFile(base) as old,zipfile.ZipFile(built) as new:
        for c in classes:assert c+'.class' in new.namelist(),c+' is missing from verified build'
        for name in old.namelist():
            if name.endswith(('.mixins.json','.refmap.json')) and name in new.namelist():
                assert old.read(name)==new.read(name),name+' requires an explicit mapping update'
        out.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as target:
            for info in old.infolist():
                if info.filename in resources or owned_class(info.filename):continue
                target.writestr(info,old.read(info.filename))
            for name in new.namelist():
                if owned_class(name):target.writestr(name,new.read(name));replaced.append(name)
            for name,path in resources.items():target.writestr(name,path.read_bytes());replaced.append(name)
    with zipfile.ZipFile(base) as old,zipfile.ZipFile(out) as final:
        assert len(final.namelist())==len(set(final.namelist())),'Duplicate ZIP entries'
        assert final.testzip() is None,'Corrupt output'
        for name in old.namelist():
            if name not in replaced and not owned_class(name):assert old.read(name)==final.read(name),name
    manifest={'base':str(base),'base_sha256':args.base_sha256,'built':str(built),'output':str(out),
              'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'replaced_entries':replaced,
              'preserved_other_entries':True}
    out.with_suffix('.manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),'utf8')
    print('Prepared',out,'with',len(replaced),'owned entries;',manifest['sha256'])


if __name__=='__main__':main()
