"""Check actual GeckoLib rotation conventions, joint chains, attack direction and UV bounds."""
import json, math
from pathlib import Path
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/tnc'
geo=json.loads((ASSETS/'geo/entity/dark_giant.geo.json').read_text(encoding='utf-8'))['minecraft:geometry'][0]
bones={b['name']:b for b in geo['bones']}
assert len(bones)==len(geo['bones'])
clips=json.loads((ASSETS/'animations/entity/dark_giant.animation.json').read_text(encoding='utf-8'))['animations']

def sample(channel,t):
    if isinstance(channel,list):return np.array(channel,dtype=float)
    keys=sorted((float(k),v.get('vector',v) if isinstance(v,dict) else v) for k,v in channel.items())
    for (a,av),(b,bv) in zip(keys,keys[1:]):
        if a<=t<=b:return np.array(av)+(np.array(bv)-av)*(t-a)/(b-a)
    return np.array(keys[0 if t<keys[0][0] else -1][1],dtype=float)

def matrix(angles):
    x,y,z=np.radians(angles);cx,sx=math.cos(x),math.sin(x);cy,sy=math.cos(y),math.sin(y);cz,sz=math.cos(z),math.sin(z)
    return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])

def transform(name,clip,t,memo=None):
    memo={} if memo is None else memo
    if name in memo:return memo[name]
    b=bones[name];p=np.array(b['pivot'],dtype=float)*[-1,1,1]
    channel=clips[clip]['bones'].get(name,{});rot=sample(channel.get('rotation',[0,0,0]),t)+b.get('rotation',[0,0,0])
    r=matrix(rot*[-1,-1,1]);m=np.eye(4);m[:3,:3]=r;m[:3,3]=p-r@p
    if b.get('parent'):m=transform(b['parent'],clip,t,memo)@m
    memo[name]=m;return m

def palm(side,clip,t):
    p=np.array([(-24 if side=='right' else 24)*-1,25.5,-1,1])
    return (transform('armhand'+side,clip,t)@p)[:3]

def main():
    for b in bones.values():
        if b.get('parent'):assert b['parent'] in bones
        seen=set();current=b
        while current.get('parent'):
            assert current['name'] not in seen;seen.add(current['name']);current=bones[current['parent']]
        for cube in b.get('cubes',[]):
            assert all(s>0 for s in cube['size'])
            for face in cube['uv'].values():
                assert all(0<=v<=1024 for uv,size in zip(face['uv'],face['uv_size']) for v in (uv,uv+size))
    for clip,data in clips.items():
        assert set(data['bones'])<=set(bones),(clip,set(data['bones'])-set(bones))
        for name,channel in data['bones'].items():
            for key,track in channel.items():
                if isinstance(track,dict):assert max(float(t) for t in track)<=data['animation_length']+.0001,(clip,name)
    result={}
    for side in ['right','left']:
        hand=palm(side,'cast_hands',2.2);sky=palm(side,'cast_sky_hands',2.6)
        before=palm(side,'cast_siphon',1.6);after=palm(side,'cast_siphon',2.2)
        if side=='right':assert hand[2]<-30,('Palm must release forward, not behind the back',hand)
        assert sky[1]>90,('Overhead reach remains connected above the head',sky)
        assert after[2]>before[2]+7,('Siphon visibly retracts through the elbow',before,after)
        for clip,data in clips.items():
            for t in np.linspace(0,data['animation_length'],40):
                assert np.isfinite(transform('armhand'+side,clip,t)).all()
        result[side]={'forwardRelease':hand.tolist(),'skyReach':sky.tolist(),'siphonRetraction':float(after[2]-before[2])}
    texture=Image.open(ASSETS/'textures/entity/dark_giant_bedrock.png');glow=Image.open(ASSETS/'textures/entity/dark_giant_emissive.png')
    assert texture.size==glow.size==(2048,2048)
    out={'bones':len(bones),'clips':len(clips),'palms':result,'atlas':texture.size,'checks':'acyclic hierarchy, positive cubes, UV bounds, clip bounds, native rotation signs, forward release, overhead reach, elbow retraction'}
    (ROOT/'work/dark-giant-upperbody-kinematics.json').write_text(json.dumps(out,indent=2),encoding='utf-8')
    print(json.dumps(out,indent=2))

if __name__=='__main__':main()
