"""Render actual Java ornament volumes exported by WarriorWeaponOrnamentsTest."""
import copy
import json
import os
import weapon_gen as gen


def main():
    source=os.path.join(gen.ROOT,'build','tnc-weapon-ornaments.json')
    frames=json.load(open(source,encoding='utf8'))
    originals={w['id']:w for w in gen.WEAPONS}
    palettes={'bow':'earth','moon_bow':'doom','storm_bow':'sky','ember_bow':'crimson'}
    labels={'bow':'战士长弓','moon_bow':'弦月长弓','storm_bow':'逐风战弓','ember_bow':'烬羽重弓'}
    rows=[]
    for id,poses in frames.items():
        for phase,boxes in enumerate(poses):
            if id in originals:
                weapon=copy.deepcopy(originals[id]);name=weapon['zh'];yaw,pitch=34,-52
            else:
                weapon={'id':id,'palette':palettes[id],'parts':[],
                        '_pixel_parts':gen.pixel_forge.bow_parts(palettes[id],1 if phase else 0)}
                name=labels[id];yaw,pitch=18,-8
            image,rects=gen.build_texture(weapon)
            parts=list(gen.expand_profile(weapon));pixels=image.load();swatches={}
            for index,b in enumerate(boxes):
                color=b['color']
                if color not in swatches:
                    n=len(swatches);u,v=n%128,64+n//128
                    pixels[u,v]=((color>>16)&255,(color>>8)&255,color&255,255);swatches[color]=(u,v,1,1)
                key='dynamic_'+str(index)
                parts.append((key,(b['x'],b['y'],b['z'],b['x']+b['w'],b['y']+b['h'],b['z']+b['d']),'gem','block'))
                rects[key]=swatches[color]
            weapon['_pixel_parts']=parts;weapon['_rects']=rects
            preview=gen.render_preview(weapon,image,size=440,yaw_deg=yaw,pitch_deg=pitch)
            rows.append((preview,name+(' · 激发' if phase else ' · 常驻'),str(len(boxes))+' animated blocks'))
    gen.contact_sheet(rows,os.path.join(gen.PREVIEW,'weapon-ornaments-20261105.png'),cols=4,cell=380,
                      title='武器动态结构 · Java 实际几何离线预览')


if __name__=='__main__':main()
