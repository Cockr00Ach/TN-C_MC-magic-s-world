"""Solve wrist paths in GeckoLib's actual mirrored animation frame, preserving the forward guard."""
import sys,json
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import solve_wrist as sw
import numpy as np
geo,bones=sw.load_geometry();clips=json.loads(sw.audit.ANIM.read_text(encoding='utf-8'))['animations'];table={}
for name,data in clips.items():
 keys=data['bones'].get('weapon_1',{}).get('rotation',{})
 table[name]=[(float(t),v[0],v[2]) for t,v in sorted(keys.items(),key=lambda x:float(x[0]))]
for name,time,form in [('cleave_left',.5,1),('spear_throw',.6,2)]:
 frame=sw.frame_for(geo,bones,clips,name,time);x0,z0=sw.wrist_at(clips,name,time)
 limits=sw.audit.aabb(sw.audit.bone_points(geo,frame.matrices['pauldron_1'],'pauldron_1'))
 options=sorted(((x0+dx,z0+dz) for dx in range(-60,61,3) for dz in range(-18,19,3)),key=lambda p:abs(p[0]-x0)+abs(p[1]-z0))
 for x,z in options:
  hits,phi,_,reach=frame.measure(bones,form,x,z)
  if hits or reach<24 or not sw.aim_ok(name,time,phi):continue
  blade=np.concatenate([poly for poly,_,_ in sw.wind_blade.place_at(form,sw.wind_blade.anchor_for(frame.matrices,bones,x,z))])
  if ((blade>limits[0])&(blade<limits[1])).all(axis=1).any():continue
  table[name]=[(t,x if abs(t-time)<.001 else a,z if abs(t-time)<.001 else b) for t,a,b in table[name]]
  sw.set_track(clips,name,table[name]);print('Aim corrected',name,x,z,flush=True);break
 else:raise RuntimeError('No clear aimed wrist: '+name)
for name in ['cleave_left','cleave_right','sweep','cyclone','spear_throw']:
 if not sw.clip_report(geo,bones,clips,name):continue
 print('Solving native grip:',name,flush=True)
 sw.solve_clip(geo,bones,clips,name,table)
 remaining=sw.clip_report(geo,bones,clips,name)
 print('Remaining:',len(remaining),flush=True)
 if remaining:raise RuntimeError('Unresolved path: '+name)
folder=Path(__file__).resolve().parents[1]/'model-source/canglan_warlord';folder.mkdir(parents=True,exist_ok=True)
out={n:{f'{t:.3f}':[x,0,z] for t,x,z in table[n]} for n in table}
(folder/'palm_grip_keys.json').write_text(json.dumps(out,indent=2)+'\n',encoding='utf-8')
