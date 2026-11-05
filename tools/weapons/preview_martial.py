"""Offline block-rig review using the shipped keyframes and matching ghost geometry."""
import copy
import json
import sys
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
from traditional_weapons import ROOT
sys.path.insert(0,str(ROOT/'tools'))
import preview_geo_anim as render


def main():
    assets=ROOT/'src/main/resources/assets/tnc'
    source=json.loads((assets/'animations/entity/traditional_martial_arts.json').read_text('utf8'))['animations']
    cases=[('willow_leaf_dao',3,'柳叶刀 · 缠头裹脑'),('red_tassel_spear',0,'红缨枪 · 拦拿扎'),
           ('qimei_staff',3,'齐眉棍 · 立舞花'),('monk_spade',3,'月牙铲 · 勾挂回带')]
    sheet=Image.new('RGB',(1440,len(cases)*330),(24,25,31));d=ImageDraw.Draw(sheet)
    font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',25)
    for row,(id,slot,title) in enumerate(cases):
        bones,order=render.load_geo(ROOT/'src/main/resources/assets/tnc/geo/entity/self.geo.json')
        model=json.loads((assets/'models/item/warrior_ghost'/f'{id}.json').read_text('utf8'))
        gx,gy,gz=model['tnc_grip'];cubes=[]
        for e in model['elements']:
            a,b=e['from'],e['to']
            cubes.append({'origin':[-6+(a[0]-gx)*.75,12-(b[2]-gz)*.75,(a[1]-gy)*.75],
                          'size':[(b[0]-a[0])*.75,(b[2]-a[2])*.75,(b[1]-a[1])*.75]})
        bones['weapon']={'name':'weapon','parent':'armRight','pivot':[-6,12,0],'cubes':cubes};order.append('weapon')
        render._GEO=bones;render._CACHE.clear();render._TEX=None;render.FRONT='-Z'
        render.TINT.update(armRight=(215,85,73),armLeft=(76,143,207),weapon=(185,199,207))
        anim=copy.deepcopy(source[id+'_'+str(slot)]);root=anim['bones']['bone']['rotation']
        anim['bones']['body']={'rotation':{t:[-v[0],0,-v[2]] for t,v in root.items()}}
        for bone,tracks in anim['bones'].items():
            if bone not in ('bone','body'):tracks['rotation']={t:[-v[0],v[1],-v[2]] for t,v in tracks['rotation'].items()}
        anim['bones']['bone']['rotation']={t:[0,v[1],0] for t,v in root.items()}
        d.text((15,row*330+6),title+' / 离线骨架预览',font=font,fill='white')
        frames=[]
        for i in range(30):
            frames.append(render.render(bones,order,anim,min(i/25,1),300,30,12,4,'',center=(0,17,0)))
        for col,n in enumerate([0,6,11,16,20,25]):sheet.paste(frames[n].resize((240,280)),(col*240,row*330+45))
        out=ROOT/'docs/previews'/f'martial-{id}.gif'
        frames[0].save(out,save_all=True,append_images=frames[1:],duration=60,loop=0,optimize=False)
    sheet.save(ROOT/'docs/previews/traditional-martial-arts-20261105.png')
    print('Wrote four rig GIFs and keyframe contact sheet')


if __name__=='__main__':main()
