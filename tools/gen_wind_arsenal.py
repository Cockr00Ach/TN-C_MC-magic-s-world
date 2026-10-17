"""Native baked wind constructs; vanilla atlas materials, no runtime mesh generation."""
import json, math
from pathlib import Path
OUT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/tnc/models/wind/arsenal'
OUT.mkdir(parents=True,exist_ok=True)
TEXTURES={'particle':'minecraft:block/light_blue_stained_glass','air':'minecraft:block/light_blue_stained_glass','edge':'minecraft:block/white_stained_glass','metal':'minecraft:block/white_concrete','core':'minecraft:block/sea_lantern'}
def box(x,y,z,dx,dy,dz,tex='air',rotation=None):
    e={'from':[round(x,3),round(y,3),round(z,3)],'to':[round(x+dx,3),round(y+dy,3),round(z+dz,3)],'shade':False,'faces':{f:{'uv':[0,0,16,16],'texture':'#'+tex} for f in ['north','south','east','west','up','down']}}
    if rotation:e['rotation']={'origin':[8,8,8],'axis':rotation[0],'angle':rotation[1]}
    return e
def ring(radius,count,start,end,thick=.45,tex='air',taper=False):
    result=[]
    for i in range(count):
        a=start+(end-start)*(i+.5)/count
        x,y=8+radius*math.cos(a),8+radius*math.sin(a)
        # Small overlapping facets make a thin curved ribbon without giant opaque panels.
        w=radius*abs(end-start)/count*1.08
        tangent=round((math.degrees(a)+90)/22.5)*22.5
        vertical=round(tangent/90)*90
        angle=tangent-vertical
        thickness=thick*(.15+.85*math.sin(math.pi*(i+.5)/count)) if taper else thick
        dx,dy=(thickness,w) if round(vertical/90)%2 else (w,thickness)
        e=box(x-dx/2,y-dy/2,7.78,dx,dy,.44,tex)
        if angle:e['rotation']={'origin':[round(x,3),round(y,3),8],'axis':'z','angle':angle}
        result.append(e)
    return result
def save(name,elements):
    (OUT/(name+'.json')).write_text(json.dumps({'ambientocclusion':False,'textures':TEXTURES,'elements':elements},indent=2)+'\n',encoding='utf-8')
save('core',[box(5,5,5,6,6,6,'air'),box(4,6,6,8,4,4,'air'),box(6,4,6,4,8,4,'air'),box(6,6,4,4,4,8,'air'),box(6.6,5.5,6.6,2.8,5,2.8,'core'),box(6,6,6,4,4,4,'air',('z',45)),box(5.7,6.8,6.8,4.6,2.4,2.4,'edge',('y',45))])
save('cage',ring(6.8,24,.2,math.pi*1.65,.65)+ring(5.8,14,math.pi,math.pi*2.05,.35,'edge'))
save('crescent',ring(7,24,-1.5,1.5,1.2,taper=True)+ring(6.2,20,-1.4,1.4,.35,'edge',True))
save('sword',[
    box(7.55,7.55,-7,.9,.9,13,'edge'),box(5.3,7.5,4,5.4,1,1.3,'metal'),
    box(6.5,7.7,6,3,.6,11,'air'),box(7.35,7.45,6,1.3,1.1,15,'core'),
    box(6.1,7.82,8,3.8,.36,7,'edge'),box(7.65,7.65,20,.7,.7,4,'edge'),
    box(6.9,7.6,17,2.2,.8,3,'air'),box(7.2,7.2,-8,1.6,1.6,1,'core')])
save('spear',[
    box(7.65,7.65,-13,.7,.7,28,'edge'),box(7.4,7.4,-12,1.2,1.2,4,'core'),
    box(5.2,7.7,10,5.6,.6,3,'air'),box(5.8,7.7,13,4.4,.6,3,'air'),box(6.4,7.7,16,3.2,.6,3,'air'),box(7.1,7.7,19,1.8,.6,3,'edge'),box(7.6,6.8,10,.8,2.4,10,'air'),
    box(7.5,7.5,13,1,1,9,'core'),box(7.75,7.75,22,.5,.5,2,'edge'),
    box(5.8,7.75,10,4.4,.5,2,'edge'),box(7.75,5.8,10,.5,4.4,2,'edge')])
save('chakram',ring(6.2,36,0,math.tau,.8)+ring(5.4,28,0,math.tau,.25,'edge')+[
    box(7.65,13.8,7.65,.7,2,.7,'core'),box(7.65,.2,7.65,.7,2,.7,'core'),
    box(.2,7.65,7.65,2,.7,.7,'core'),box(13.8,7.65,7.65,2,.7,.7,'core')])
print('Generated six native wind models')
