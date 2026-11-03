"""Offline motion review with a block rig and simple weapon proxies; not a Minecraft screenshot."""
import json
from pathlib import Path
import preview_geo_anim as render
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1]
def main():
    geo=ROOT/'src/main/resources/assets/tnc/geo/entity/self.geo.json'
    animations=ROOT/'src/main/resources/assets/tnc/animations/entity/warrior_performance.json'
    cases=[('greatsword','GS_RIFT'),('spear','SPEAR_FLOW'),('scythe','SCYTHE_HOOK'),('bow','BOW_VOLLEY')]
    for kind,name in cases:
        bones,order=render.load_geo(geo)
        # Proxy attachment at the hand; color-coded limbs expose handoff and support-hand release.
        parts={'greatsword':[(5,-.5,4,11,1,18),(3,-1,3,13,1.5,4)],
               'spear':[(7.5,0,0,8.5,1,22),(7,0,22,9,1,27)],
               'scythe':[(7.5,0,0,8.5,1,20),(-1,0,18,9,1.5,21),(-3,0,15,0,1.5,19)],
               'bow':[(6,0,0,7,1,5),(4,0,5,5,1,13),(6,0,13,7,1,18),(6.5,0,0,7,1,18)]}[kind]
        cubes=[]
        for x1,y1,z1,x2,y2,z2 in parts:
            cubes.append({'origin':[-6+(x1-8)*.75,12-(z2-2)*.75,(y1-.5)*.75],
                          'size':[(x2-x1)*.75,(z2-z1)*.75,(y2-y1)*.75]})
        bones['weapon']={'name':'weapon','parent':'armRight','pivot':[-6,12,0],'cubes':cubes};order.append('weapon')
        render._GEO=bones;render._CACHE.clear();render._TEX=None;render.FRONT='-Z'
        render.TINT.update(armRight=(215,85,73),armLeft=(76,143,207),weapon=(185,199,207))
        anim=render.load_anim(str(animations),name);length=anim['animation_length']
        # Runtime uses ModelPart (Y down); this geometry is Y up. Reflect X/Z
        # angles and apply root pitch/roll to the torso only, like WarriorPose.
        root=anim['bones']['bone']['rotation']
        anim['bones']['body']={'rotation':{t:[-v[0],0,-v[2]] for t,v in root.items()}}
        for bone,tracks in anim['bones'].items():
            if bone in ('bone','body'):continue
            tracks['rotation']={t:[-v[0],v[1],-v[2]] for t,v in tracks['rotation'].items()}
        anim['bones']['bone']['rotation']={t:[0,v[1],0] for t,v in root.items()}
        frames=[]
        for i in range(40):
            t=length*min(i/32,1)
            image=render.render(bones,order,anim,t,420,30,12,5,'RIG PREVIEW / '+kind.upper(),center=(0,17,0))
            frames.append(image)
        out=ROOT/'docs/previews'/('warrior-signature-'+kind+'-20261104.gif')
        frames[0].save(out,save_all=True,append_images=frames[1:],duration=55,loop=0,optimize=False)
        strip=Image.new('RGB',(1200,240),'#1a1a22')
        for i,n in enumerate([0,5,11,16,23,32]):strip.paste(frames[n].resize((200,200)),(i*200,20))
        strip.save(out.with_suffix('.png'))
        print(out)
if __name__=='__main__':main()
