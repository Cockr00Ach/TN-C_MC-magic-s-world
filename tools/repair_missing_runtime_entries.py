"""Restore missing production entries without replacing installed content."""
import argparse,copy,hashlib,json,zipfile
from pathlib import Path
from audit_mod_runtime import audit

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def repair(base,built,output,report):
    if output.exists() or output.resolve() in (base.resolve(),built.resolve()):
        raise ValueError('Output must be a new, separate jar')
    before=digest(base)
    with zipfile.ZipFile(base) as old,zipfile.ZipFile(built) as source:
        names=set(old.namelist())
        # Test fixtures and test subscribers must not be introduced into a player's installation.
        additions=[n for n in source.namelist() if n not in names and not n.endswith('/')
                   and 'GameTests' not in n and not n.startswith(('data/tncleviathan/','data/tncroute/'))]
        entries={n:old.read(n) for n in names if n.endswith('.class')}
        entries.update({n:source.read(n) for n in additions if n.endswith('.class')})
        missing,bad=audit(entries)
        if missing or bad:
            raise ValueError(f'Incomplete runtime dependencies: {missing}; {bad}')
        with zipfile.ZipFile(output,'w') as out:
            for info in old.infolist():out.writestr(copy.copy(info),old.read(info.filename))
            for name in additions:out.writestr(copy.copy(source.getinfo(name)),source.read(name))
        with zipfile.ZipFile(output) as out:
            if out.testzip() is not None:raise ValueError('Candidate CRC failure')
            for name in names:
                if old.read(name)!=out.read(name):raise ValueError(f'Installed content changed: {name}')
    if digest(base)!=before:raise ValueError('Installed package changed during repair')
    evidence={'base':str(base),'base_sha256':before,'built':str(built),'built_sha256':digest(built),
              'output':str(output),'output_sha256':digest(output),'added_entries':additions,
              'preserved_entries':len(names),'all_existing_entries_identical':True,
              'missing_classes':missing,'missing_members':bad}
    report.write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in evidence.items() if k!='added_entries'},ensure_ascii=False,indent=2))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    for key in ('base','built','output','report'):parser.add_argument('--'+key,type=Path,required=True)
    args=parser.parse_args();repair(args.base,args.built,args.output,args.report)
