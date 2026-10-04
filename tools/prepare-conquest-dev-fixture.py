"""Dev-only Mojmap names for Conquest's nested Fabric API classes and mixin strings.

The installed Conquest jar is never edited. Outer classes already come from
ForgeGradle's mapped cache; nested jars are not processed by ForgeGradle.
"""
from pathlib import Path
import zipfile,io,re
ROOT=Path(__file__).resolve().parents[1]
names={}
for line in (ROOT/'build/createSrgToMcp/output.srg').read_text().splitlines():
    v=line.split()
    if v[0]=='MD:':names[v[1].rsplit('/',1)[-1]]=v[3].rsplit('/',1)[-1]
    elif v[0]=='FD:':names[v[1].rsplit('/',1)[-1]]=v[2].rsplit('/',1)[-1]

def replace(raw):
    return re.sub(rb'(?<![A-Za-z0-9_])(?:m|f)_\d+_(?![A-Za-z0-9_])',lambda m:names.get(m[0].decode(),m[0].decode()).encode(),raw)

def cls(b):
    out=bytearray(b[:10]);i=10;n=1;count=int.from_bytes(b[8:10],'big')
    while n<count:
        tag=b[i];out.append(tag);i+=1
        if tag==1:
            size=int.from_bytes(b[i:i+2],'big');i+=2;raw=b[i:i+size];i+=size;raw=replace(raw);out+=len(raw).to_bytes(2,'big')+raw
        else:
            size={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}[tag];out+=b[i:i+size];i+=size
            if tag in (5,6):n+=1
        n+=1
    return bytes(out)+b[i:]

changed=0
def jar(data):
    global changed
    result=io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as source,zipfile.ZipFile(result,'w',zipfile.ZIP_DEFLATED) as target:
        for entry in source.infolist():
            raw=source.read(entry.filename);new=raw
            if entry.filename.endswith('.jar'):new=jar(raw)
            elif entry.filename.endswith('.class'):new=cls(raw)
            elif entry.filename.endswith('refmap.json'):new=replace(raw)
            changed+=new!=raw;target.writestr(entry,new)
    return result.getvalue()

if __name__=='__main__':
    source=next((Path.home()/'.gradle/caches/forge_gradle/deobf_dependencies/conquest').rglob('ConquestReforged-1.6.0_mapped_official_1.20.1.jar'))
    target=ROOT/'work/tavern-fixtures/conquest-dev.jar';target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(jar(source.read_bytes()))
    print('Dev fixture prepared:',changed,'entries; installed mod remains unchanged')
