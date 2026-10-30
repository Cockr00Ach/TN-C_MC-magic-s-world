"""Author a new impale/lift/fling clip; emit apply_patch input, preserving all old clips."""
import json
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/tnc'
geo=json.loads((ASSETS/'geo/entity/canglan_warlord.geo.json').read_text())['minecraft:geometry'][0]
bones={b['name']:b for b in geo['bones']}
chain=['root','pelvis','waist','torso','chest','arm_1','fore_1','hand_1','weapon_1']

def tr(v):
    m=np.eye(4);m[:3,3]=v;return m

def rot(v):
    x,y,z=np.radians(np.array(v)*[-1,-1,1]);cx,sx=np.cos(x),np.sin(x);cy,sy=np.cos(y),np.sin(y);cz,sz=np.cos(z),np.sin(z)
    m=np.eye(4);m[:3,:3]=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
    return m

def measure(q,lean=0,twist=0):
    poses={'arm_1':q[:3],'fore_1':q[3:6],'weapon_1':q[6:9],'torso':[lean,twist,0]}
    m=np.diag([-1,1,-1,1]);m[1,3]=.01
    for name in chain:
        b=bones[name];p=np.array(b['pivot'])*[-1,1,1]/16
        v=np.array(b.get('rotation',[0,0,0]))+poses.get(name,[0,0,0])
        m=m@tr(p)@rot(v)@tr(-p)
    p=np.array(bones['weapon_1']['pivot'])*[-1,1,1]/16
    palm=(m@np.r_[p,1])[:3]
    # DrawBlade: palm -> Rz(4) Rx(-80) -> Tz(1.2) -> scale(2.4) -> real item tip.
    a=np.radians(4);rz=np.array([[np.cos(a),-np.sin(a),0],[np.sin(a),np.cos(a),0],[0,0,1]])
    a=np.radians(-80);rx=np.array([[1,0,0],[0,np.cos(a),-np.sin(a)],[0,np.sin(a),np.cos(a)]])
    tip=palm+m[:3,:3]@rz@rx@np.array([0,0,1.2+2.4*1.069375])
    return np.r_[palm,tip]

def solve(palm,tip,seed,lean=0,twist=0):
    target=np.r_[palm,tip];q=np.array(seed,dtype=float)
    for _ in range(240):
        residual=target-measure(q,lean,twist)
        if np.linalg.norm(residual)<.015:break
        jac=np.column_stack([(measure(q+np.eye(9)[i]*.1,lean,twist)-measure(q,lean,twist))/.1 for i in range(9)])
        step=jac.T@np.linalg.solve(jac@jac.T+np.eye(6)*.0001,residual)
        q+=np.clip(step,-8,8)
    error=float(np.linalg.norm(target-measure(q,lean,twist)))
    if error>.12:raise RuntimeError(('Unreachable execution pose',error,palm,tip))
    return q.tolist()

def authored():
    # The tip-to-palm length is 3.7665 blocks: targets preserve the actual shared spear.
    definitions=[
        (0,[1.3,3.1,.6],[1.3,4.4,4.1348],0,0),
        (14,[1.6,2.7,-.1],[1.6,3.5,3.5805],-9,-12),
        (22,[1.0,2.7,2.0],[.3,1.4,5.4654],12,5),
        (32,[1.2,3.7,1.6],[.7,3.8,5.3318],5,0),
        (54,[1.5,5.5,.9],[1.0,9.2206,1.2],-12,-5),
        (64,[1.5,5.5,.9],[1.0,9.2206,1.2],-12,-5),
        (72,[1.2,4.5,1.7],[.2,6.0,5.0067],7,12),
        (80,[1.0,3.4,2.1],[-.3,2.8,5.5836],18,20),
        (96,[1.3,3.1,.6],[1.3,4.4,4.1348],0,0),
        (112,[1.3,3.1,.6],[1.3,4.4,4.1348],0,0),
    ]
    tracks={};q=[-50,0,-30,0,0,0,0,0,0]
    def put(name,t,value):tracks.setdefault(name,{})[str(t)]=[round(float(v),5) for v in value]
    for tick,palm,tip,lean,twist in definitions:
        q=solve(palm,tip,q,lean,twist)
        for name,v in [('arm_1',q[:3]),('fore_1',q[3:6]),('weapon_1',q[6:9]),('torso',[lean,twist,0]),('head',[-lean*.35,-twist*.7,0]),('arm_-1',[-30 if tick<40 else -95,-15,25]),('fore_-1',[-35,0,-12]),('thigh_1',[-8 if tick<80 else -20,0,-6]),('thigh_-1',[10,0,6])]:put(name,tick,v)
        for i,name in enumerate(['cape_a','cape_b','cape_c','cape_d','crest_a','crest_b','crest_c']):put(name,tick,[-lean*(.5+i*.1),-twist*.35,0])
    nodes=[]
    for b in geo['bones']:
        nodes.append({'name':b['name'],'parent':b.get('parent',''),'pivot':b['pivot'],'base':b.get('rotation',[0,0,0]),'keys':tracks.get(b['name'],{})})
    data={'duration':112,'contact':22,'release':72,'nodes':nodes}
    clip={'loop':False,'animation_length':5.6,'bones':{n:{'rotation':{f'{int(t)/20:.3f}':v for t,v in keys.items()}} for n,keys in tracks.items()}}
    return data,clip

if __name__=='__main__':
    data,clip=authored()
    animation='src/main/resources/assets/tnc/animations/entity/canglan_warlord.animation.json'
    kin='src/main/resources/assets/tnc/poses/canglan_execution.json'
    existing=json.loads((ROOT/animation).read_text())['animations']
    assert 'execution_impale' not in existing,'Execution clip already exists; do not overwrite blindly'
    # Insert exactly one member; all pre-existing authored tracks retain their text and values.
    addition=json.dumps({'execution_impale':clip},ensure_ascii=False,indent=2).splitlines()[1:-1]
    addition[-1]+=','
    print('*** Begin Patch\n*** Update File: '+str(ROOT/animation)+'\n@@\n   "animations": {')
    for line in addition:print('+'+'  '+line)
    print('*** Add File: '+str(ROOT/kin))
    for line in json.dumps(data,ensure_ascii=False,indent=2).splitlines():print('+'+line)
    print('*** End Patch')
