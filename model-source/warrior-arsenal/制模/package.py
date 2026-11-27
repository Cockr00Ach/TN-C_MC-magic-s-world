"""Build the independent resource overlay. An explicit --install path selects an OpenLoader resource directory."""
import argparse,hashlib,os,shutil,tempfile
from zipfile import ZIP_DEFLATED,ZipFile,ZipInfo
from roster import ROOT,PACK

def digest(path):
    with path.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()

def build():
    files=sorted(p for p in PACK.rglob('*') if p.is_file())
    assert all(p.suffix in ('.json','.png','.mcmeta') for p in files)
    result=ROOT/'TN-C-战士兵器精修.zip'
    tmp=result.with_suffix('.tmp')
    with ZipFile(tmp,'w') as z:
        for p in files:
            n=p.relative_to(PACK).as_posix();info=ZipInfo(n,(2026,11,26,0,0,0));info.compress_type=ZIP_DEFLATED;z.writestr(info,p.read_bytes())
    with ZipFile(tmp) as z:
        assert z.testzip() is None
        assert all(z.read(p.relative_to(PACK).as_posix())==p.read_bytes() for p in files)
    tmp.replace(result)
    return result,len(files)

def install(pack,target):
    dest=target.resolve(strict=True)
    if dest.name!='resources' or dest.parent.name!='openloader':raise ValueError('Expected config/openloader/resources')
    final=dest/'tnc-warrior-arsenal.zip'
    if final.exists():
        if digest(final)==digest(pack):return final
        raise FileExistsError('A different installed archive already exists: '+str(final))
    with tempfile.NamedTemporaryFile(dir=dest,suffix='.pending',delete=False) as stage:
        tmp=final.with_name(os.path.basename(stage.name))
    try:
        shutil.copyfile(pack,tmp);assert digest(tmp)==digest(pack)
        os.rename(tmp,final)
    finally:tmp.unlink(missing_ok=True)
    assert digest(final)==digest(pack)
    return final

if __name__=='__main__':
    from pathlib import Path
    parser=argparse.ArgumentParser();parser.add_argument('--install',type=Path)
    args=parser.parse_args();pack,n=build();print(pack,n,digest(pack))
    if args.install:print('Installed',install(pack,args.install))
