"""Development fixture ONLY: MCA's universal jar has SRG injector strings without a refmap.
ForgeGradle remaps bytecode, but cannot remap those strings. Translate only UTF8
member names in MCA mixin classes with the generated SRG-to-Mojmap table. The
installed modpack jar is never modified. This changes names, not MCA behavior.
"""
from pathlib import Path
import struct,zipfile,re
root=Path(__file__).resolve().parents[1]
names={}
for line in (root/'build/createSrgToMcp/output.srg').read_text().splitlines():
 v=line.split()
 if v[0]=='MD:': names[v[1].rsplit('/',1)[-1]]=v[3].rsplit('/',1)[-1]
 elif v[0]=='FD:': names[v[1].rsplit('/',1)[-1]]=v[2].rsplit('/',1)[-1]
def patch(b):
 out=bytearray(b[:10]);i=10;count=int.from_bytes(b[8:10],'big');n=1
 while n<count:
  tag=b[i];out.append(tag);i+=1
  if tag==1:
   size=int.from_bytes(b[i:i+2],'big');i+=2;raw=b[i:i+size];i+=size
   # Names are ASCII; preserve non-ASCII modified UTF-8 verbatim.
   new=re.sub(rb'(?<![A-Za-z0-9_])(?:m|f)_\d+_(?![A-Za-z0-9_])',lambda m:names.get(m[0].decode(),m[0].decode()).encode(),raw)
   out+=len(new).to_bytes(2,'big')+new
  else:
   sizes={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}
   size=sizes[tag];out+=b[i:i+size];i+=size
   if tag in (5,6):n+=1
  n+=1
 return bytes(out)+b[i:]
source=root/'libs/mca-7.6.26.jar';target=root/'libs/mca-dev-7.6.26.jar';patched=0
with zipfile.ZipFile(source) as src,zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as dst:
 for item in src.infolist():
  data=src.read(item.filename)
  if item.filename.startswith('forge/net/mca/mixin/') and item.filename.endswith('.class'):
   new=patch(data);patched+=new!=data;data=new
  dst.writestr(item,data)
print('Dev fixture prepared:',patched,'mixin classes. Game jar unchanged.')
