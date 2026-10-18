"""Prepare a water-only update over the latest installed jar, preserving other topics."""
import argparse, copy, hashlib, json, zipfile
from pathlib import Path

def water(name):
    return (name.startswith('com/tnc/tnc/magic/water/') or
            name.startswith('com/tnc/tnc/client/Water') or
            name.startswith('com/tnc/tnc/client/TNWater') or
            name.startswith('com/tnc/tnc/client/SeaGodSwordVisuals') or
            name.startswith('assets/tnc/models/water/'))
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def prepare(base,built,out,report):
    if out.resolve() in (base.resolve(),built.resolve()) or out.exists():
        raise ValueError('Output must be a new, separate jar')
    before_hash=digest(base);changed=[];removed=[];preserved=0
    with zipfile.ZipFile(base) as original,zipfile.ZipFile(built) as update:
        if len(original.namelist())!=len(set(original.namelist())):raise ValueError('Duplicate base entries')
        if any(n.upper().endswith(('.SF','.RSA','.DSA')) and n.startswith('META-INF/') for n in original.namelist()):
            raise ValueError('Signed base jar requires a different distribution process')
        files={i.filename:i for i in update.infolist() if water(i.filename) and not i.is_dir()}
        required={'com/tnc/tnc/client/WaterSolidModels.class',
                  'com/tnc/tnc/magic/water/WaterRainModelLayout.class',
                  'com/tnc/tnc/client/TNWaterFieldRenderer.class',
                  'assets/tnc/models/water/seal.json','assets/tnc/models/water/raindrop.json',
                  'assets/tnc/models/water/rain_crown.json'}
        if not required<=files.keys():raise ValueError('Incomplete water build')
        out.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(out,'w') as result:
            for info in original.infolist():
                name=info.filename
                if water(name) and not info.is_dir():
                    if name not in files:removed.append(name);continue
                    entry=files.pop(name);data=update.read(name)
                    if data!=original.read(name):changed.append(name)
                    result.writestr(copy.copy(entry),data)
                else:result.writestr(copy.copy(info),original.read(name));preserved+=1
            for name,info in files.items():result.writestr(copy.copy(info),update.read(name));changed.append(name)
        with zipfile.ZipFile(out) as result:
            if result.testzip() is not None:raise ValueError('Output CRC verification failed')
            for name in original.namelist():
                if not water(name) and result.read(name)!=original.read(name):
                    raise ValueError('Unrelated entry changed: '+name)
        if digest(base)!=before_hash:raise ValueError('Installed jar changed during preparation')
    evidence={'base':str(base),'base_sha256':before_hash,'built':str(built),'built_sha256':digest(built),
              'output':str(out),'output_sha256':digest(out),'preserved_entries':preserved,
              'changed_water_entries':changed,'removed_water_entries':removed,'unrelated_entries_identical':True}
    report.write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf8')
    print(json.dumps({'preserved_entries':preserved,'changed_water_entries':len(changed),
                      'removed_water_entries':len(removed),'sha256':evidence['output_sha256']},indent=2))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--base',type=Path,required=True);p.add_argument('--built',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True);p.add_argument('--report',type=Path,required=True)
    a=p.parse_args();prepare(a.base,a.built,a.output,a.report)
