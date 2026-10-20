"""Preserve the author's attack examples and adapt their timing to server motion."""
from pathlib import Path
import copy
import json

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/tnc'
AUTHOR=ROOT/'model-source/ink-crane/author-attacks-v4'

def refine():
    path=ASSETS/'animations/entity/ink_crane_boss.animation.json'
    data=json.loads(path.read_text(encoding='utf-8'))
    examples=json.loads((AUTHOR/'attack-examples.animation.json').read_text(encoding='utf-8'))['animations']
    clips=data['animations']
    for name,clip in examples.items():clips['author_'+name]=copy.deepcopy(clip)
    sweep=copy.deepcopy(examples['attack2'])
    sweep['animation_length']=6.0
    # Whole-entity displacement is server controlled; preserve authored torso,
    # wing, tail and yaw rhythm, then recover to the folded stance.
    sweep['bones']['root'].pop('position',None)
    for channels in sweep['bones'].values():
        for channel,frames in list(channels.items()):
            if isinstance(frames,list):frames={'0.0':frames}
            frames['2.65']=[0,0,0]
            channels[channel]=frames
    for side in ('l','r'):
        for i in range(10):
            sweep['bones'][f'wing_{side}_finger_{i+1}']={'rotation':{'0.0':[0,0,0],
                '1.0':[0,(i-4)*2,(-1 if side=='l' else 1)*i],
                '1.6':[8,(4-i)*3,0],'2.65':[0,0,0]}}
    # Three alternating, eased arcs instead of a single abrupt full-body spin.
    # Keep the author's example above as an immutable reference.
    sweep['bones']['root']={'rotation':{}}
    poses={'root':[0,54,0], 'body':[-6,12,7], 'neck_low':[8,-15,-5],
           'neck_mid':[-8,-12,0], 'head':[0,-18,0],
           'wing_l':[-22,-35,72], 'wing_r':[-18,24,-62],
           'wing_l_fore':[0,-40,12], 'wing_r_fore':[0,35,-10], 'tail':[8,-18,0]}
    for name,pose in poses.items():
        frames={'0.0':[0,0,0]}
        for strike in range(3):
            start=.55+strike*1.3
            sign=1 if strike%2==0 else -1
            frames[str(round(start,3))]=[pose[0]*.6,-pose[1]*sign,pose[2]*sign*.7]
            frames[str(round(start+.85,3))]=[pose[0],pose[1]*sign,pose[2]*sign]
            frames[str(round(start+1.25,3))]=[pose[0]*.5,pose[1]*sign*.8,pose[2]*sign*.5]
        frames['5.85']=[0,0,0]
        sweep['bones'][name]={'rotation':frames}
    clips['spectral_sweep']=sweep
    avatar=copy.deepcopy(sweep);avatar['animation_length']=6.6
    for channels in avatar['bones'].values():
        for channel,frames in channels.items():
            channels[channel]={'0.0':frames.get('0.0',[0,0,0]),
                               **{str(round(float(t)+.6,4)):v for t,v in frames.items()}}
    clips['avatar_sweep']=avatar
    ascent=copy.deepcopy(examples['attack3']);ascent['animation_length']=3.4
    ascent['bones'].pop('root',None)
    for channels in ascent['bones'].values():
        for channel,frames in list(channels.items()):
            if isinstance(frames,list):frames={'0.0':frames}
            frames['3.3']=[0,0,0];channels[channel]=frames
    clips['sky_ascent']=ascent
    diveposes={'body':[65,0,0],'neck_low':[-20,0,0],'neck_mid':[-15,0,0],
               'head':[-20,0,0],'wing_l':[-25,-20,38],'wing_r':[-25,20,-38],
               'tail':[-20,0,0]}
    clips['sky_dive']={'animation_length':1.6,'bones':{
        name:{'rotation':{'0.0':[0,0,0],'.35':pose,'1.6':pose}}
        for name,pose in diveposes.items()}}
    clips['sky_land']={'animation_length':.8,'bones':{
        name:{'rotation':{'0.0':pose,'.3':[-value*.15 for value in pose],'.8':[0,0,0]}}
        for name,pose in diveposes.items()}}
    roar={'animation_length':2.1,'bones':{}}
    poses={'neck_low':[-14,0,0],'neck_mid':[-18,0,0],'head':[-42,0,0],
           'body':[-5,0,0],'wing_l':[-45,-30,75],'wing_r':[-45,30,-75],
           'wing_l_fore':[0,-20,-15],'wing_r_fore':[0,20,15],'tail':[15,0,0]}
    for name,pose in poses.items():
        roar['bones'][name]={'rotation':{'0.0':[0,0,0],'.65':pose,'1.65':pose,'2.1':pose}}
    for i in range(9):
        roar['bones'][f'crown_plume_{i+1}']={'rotation':{'0.0':[0,0,0],'.7':[-12,(i-4)*3,0],'2.1':[0,0,0]}}
    clips['ink_roar']=roar
    path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    model=json.loads((AUTHOR/'author-crane.geo.json').read_text(encoding='utf-8'))
    (ASSETS/'geo/entity/ink_crane_boss.geo.json').write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8')
    ghost=copy.deepcopy(model);geo=ghost['minecraft:geometry'][0]
    geo['description']['identifier']='geometry.tnc.ink_crane_phantom'
    for b in geo['bones']:
        if b['name']=='root':b['pivot']=[0,123,0]
    (ASSETS/'geo/entity/ink_crane_phantom.geo.json').write_text(json.dumps(ghost,indent=2)+'\n',encoding='utf-8')
    print('Author attack2/attack3 preserved; roar, synchronized avatar sweep and ascent adapted.')

if __name__=='__main__':refine()
