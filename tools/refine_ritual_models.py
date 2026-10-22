"""Code-native Blockbench-compatible models; uses the pack's ceramic/copper materials."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'src/main/resources/assets/tnc'
def box(lo,hi,tex,rotation=None):
    e={'from':lo,'to':hi,'faces':{f:{'texture':'#'+tex,'uv':[0,0,16,16]} for f in ['north','south','east','west','up','down']}}
    if rotation:e['rotation']=rotation
    return e
def write(name,data):
    (A/'models'/name).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n','utf-8')
def run():
    textures={k:'tnc:block/alchemy_'+v for k,v in [('ceramic','ceramic'),('bronze','bronze'),('glow','glow'),('wood','wood')]}
    textures['particle']=textures['ceramic']
    # Continuous, opaque bottom. No cullface on any interior surface.
    elements=[box([3.2,5,10.6],[28.8,7,21.4],'ceramic'),box([10.6,5,3.2],[21.4,7,28.8],'ceramic'),box([5,4,5],[27,5,27],'bronze')]
    for a in [-45,45]:
        elements.append(box([10.6,5,3.2],[21.4,7,28.8],'ceramic',{'origin':[16,0,16],'axis':'y','angle':a}))
    for x,z in [(5,5),(24,5),(5,24),(24,24)]:
        elements += [box([x,0,z],[x+3,5,z+3],'ceramic'),box([x-.5,0,z-.5],[x+3.5,1,z+3.5],'bronze')]
    for angle in [0,45,-45,90,135,180,225,270]:
        # Minecraft JSON rotations allow only -45..45: rotate the coordinates for quarter turns.
        import math
        a=math.radians(angle);cx=16+math.sin(a)*12.8;cz=16+math.cos(a)*12.8
        diagonal=angle%90!=0
        if diagonal:
            for y0,y1,depth,tex in [(6,14.3,2.8,'ceramic'),(13.9,15.5,3.4,'bronze'),(7,8,3.1,'bronze')]:
                elements.append(box([cx-6.3,y0,cz-depth/2],[cx+6.3,y1,cz+depth/2],tex,{'origin':[cx,0,cz],'axis':'y','angle':45 if angle in [45,225] else -45}))
        elif angle in [0,180]:
            elements += [box([10,6,cz-1.5],[22,14.4,cz+1.5],'ceramic'),box([9.5,14.1,cz-1.8],[22.5,15.5,cz+1.8],'bronze')]
        else:
            elements += [box([cx-1.5,6,10],[cx+1.5,14.4,22],'ceramic'),box([cx-1.8,14.1,9.5],[cx+1.8,15.5,22.5],'bronze')]
    for x in [0,29]:
        elements += [box([x,10,11],[x+3,12,21],'bronze'),box([x,12,11],[x+3,14,13],'bronze'),box([x,12,19],[x+3,14,21],'bronze')]
    display={'gui':{'rotation':[25,225,0],'translation':[-4,-3,0],'scale':[.48,.48,.48]}}
    write('block/spellflame_cauldron.json',{'textures':textures,'elements':elements,'display':display})
    lid=[]
    for radius,half,y0,y1,tex in [(14,6,15.5,16.1,'bronze'),(13.3,5.6,16.1,16.7,'ceramic'),(11.5,4.9,16.7,17.3,'ceramic')]:
        lid.extend([box([16-radius,y0,16-half],[16+radius,y1,16+half],tex),box([16-half,y0,16-radius],[16+half,y1,16+radius],tex)])
        for a in [-45,45]:lid.append(box([16-half,y0,16-radius],[16+half,y1,16+radius],tex,{'origin':[16,0,16],'axis':'y','angle':a}))
    lid.extend([box([13,17.3,14],[15,19,18],'bronze'),box([17,17.3,14],[19,19,18],'bronze'),box([13,19,14],[19,20,18],'wood')])
    write('block/spellflame_cauldron_lid.json',{'textures':textures,'elements':lid})
    # The three special recipes have distinct silhouettes, not a renamed shared plate.
    source=json.loads((A/'models/item/sunpepper_roast.json').read_text('utf8'))
    plate=source['elements'][:5]
    import copy, math
    for name in ['concord_banquet','ninefold_sun_feast','discord_mash']:
        dish=copy.deepcopy(source);dish['elements']=copy.deepcopy(plate)
        if name=='concord_banquet':
            for x,z,tex,h in [(4,4,'pepper',5),(9,4,'berry',4),(4,9,'root',4.5),(9,9,'leaf',3.5)]:
                dish['elements'].append(box([x,2.7,z],[x+3,h,z+3],tex))
            dish['elements'].append(box([6.8,4,6.8],[9.2,5.6,9.2],'cream'))
        elif name=='ninefold_sun_feast':
            dish['elements'] += [box([5.5,2.7,5.5],[10.5,5.4,10.5],'cream'),box([6.8,5.4,6.8],[9.2,6.4,9.2],'pepper')]
            for i in range(8):
                a=i*math.pi/4;x=8+math.cos(a)*4.3;z=8+math.sin(a)*4.3
                dish['elements'].append(box([x-1,2.7,z-1],[x+1,4,z+1],['pepper','leaf','berry','root'][i%4]))
        else:
            dish['textures']['spoiled']='minecraft:block/purple_terracotta'
            for i,(x,z) in enumerate([(4,4),(7,3),(10,5),(4,8),(8,8),(10,10),(6,11)]):
                dish['elements'].append(box([x,2.7,z],[x+2.7,3.6+(i%3)*.7,z+2.7],'spoiled' if i%2 else 'root'))
        write('item/'+name+'.json',dish)
if __name__=='__main__':run()
