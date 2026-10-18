"""Audit TN-C class references; Minecraft/Forge inherited members require runtime validation."""
import struct,zipfile,re
class Class:
 def __init__(self,b):
  self.b=b;self.i=8;self.cp=[None];n=self.u2()
  while len(self.cp)<n:
   t=self.u1()
   if t==1:v=self.take(self.u2()).decode('utf-8','replace')
   elif t in (3,4):v=self.take(4)
   elif t in (5,6):v=self.take(8)
   elif t in (7,8,16,19,20):v=self.u2()
   elif t in (9,10,11,12,17,18):v=(self.u2(),self.u2())
   elif t==15:v=(self.u1(),self.u2())
   else:raise ValueError(t)
   self.cp.append((t,v))
   if t in (5,6):self.cp.append(None)
  self.u2();self.name=self.cls(self.u2());self.parent=self.cls(self.u2());self.interfaces=[self.cls(self.u2()) for _ in range(self.u2())];self.fields=self.members();self.methods=self.members()
 def take(self,n):v=self.b[self.i:self.i+n];self.i+=n;return v
 def u1(self):return self.take(1)[0]
 def u2(self):return int.from_bytes(self.take(2),'big')
 def u4(self):return int.from_bytes(self.take(4),'big')
 def cls(self,i):return self.cp[self.cp[i][1]][1] if i else None
 def members(self):
  out=set()
  for _ in range(self.u2()):
   self.u2();out.add((self.cp[self.u2()][1],self.cp[self.u2()][1]))
   for _ in range(self.u2()):self.u2();self.take(self.u4())
  return out
 def refs(self):
  for e in self.cp:
   if e and e[0] in (9,10,11):
    c,nt=e[1];a,b=self.cp[nt][1];yield self.cls(c),self.cp[a][1],self.cp[b][1],e[0]==9

def audit(entries):
 classes={n[:-6]:Class(b) for n,b in entries.items() if n.startswith('com/tnc/') and n.endswith('.class')}
 def has(name,key,field,seen):
  if not name or name in seen:return False
  if name=='java/lang/Object':return not field and key[0] in {'getClass','hashCode','equals','clone','toString','notify','notifyAll','wait','finalize'}
  # Minecraft/Forge inheritance is external to this jar and is outside this audit.
  if not name.startswith('com/tnc/'):return True
  if name not in classes:return False
  seen.add(name);c=classes[name]
  if key in (c.fields if field else c.methods):return True
  if key[0]=='<init>':return False
  return any(has(p,key,field,seen) for p in [c.parent]+c.interfaces)
 missing=set();bad=[]
 for name,c in classes.items():
  for entry in c.cp:
   if not entry:continue
   if entry[0]==7:
    owner=c.cp[entry[1]][1]
    if owner.startswith('com/tnc/') and owner not in classes:missing.add(owner)
   elif entry[0]==1:
    for owner in re.findall(r'L(com/tnc/[^;<>]+);',entry[1]):
     if owner not in classes:missing.add(owner)
  for owner,member,desc,field in c.refs():
   if not owner.startswith('com/tnc/'):continue
   if owner not in classes:missing.add(owner)
   elif not has(owner,(member,desc),field,set()):bad.append((name,owner,member,desc))
 return sorted(missing),bad
if __name__=='__main__':
 import argparse,json
 parser=argparse.ArgumentParser(description="Check internal TN-C class/member references in a production jar.")
 parser.add_argument("jar",type=__import__('pathlib').Path);args=parser.parse_args()
 with zipfile.ZipFile(args.jar) as z:
  missing,bad=audit({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
 print(json.dumps({"missing_classes":missing,"missing_members":bad},indent=2))
 raise SystemExit(bool(missing or bad))
