"""Original Minecraft-style wind general: cuboid-built form, flat color materials and cloth rig."""
import json
import math
import base64
import uuid
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'src/main/resources/assets/tnc'
COLORS={0:(188,204,213),1:(139,160,175),2:(219,232,234),3:(168,190,202),
        4:(230,235,226),5:(168,191,201),6:(65,127,135),7:(29,65,76),
        8:(187,162,103),9:(28,35,44),10:(42,46,51),11:(221,184,150),
        13:(122,185,184),14:(242,241,222),15:(203,225,232),16:(229,193,160),
        17:(202,164,132),18:(224,224,209),19:(40,52,60),20:(172,128,105),21:(38,41,47)}
SIZE=2048
FACES=('north','south','east','west','up','down')
bones=[]
surfaces=[]


def bone(name,pivot,parent='torso',rotation=None):
    b={'name':name,'pivot':list(pivot),'cubes':[]}
    if parent:b['parent']=parent
    if rotation:b['rotation']=list(rotation)
    bones.append(b)
    return b


def cube(b,center,size,mat,rot=None):
    c={'origin':[round(center[i]-size[i]/2,4) for i in range(3)],'size':list(size),'uv':{}}
    if rot:c.update(pivot=list(center),rotation=list(rot))
    b['cubes'].append(c)
    for face in FACES:
        x,y,z=size
        w,h=(x,y) if face in ('north','south') else (z,y) if face in ('east','west') else (x,z)
        surfaces.append((c,face,mat,w,h))


def build():
    bones.clear();surfaces.clear()
    from wind_general_voxel_shape import build as build_voxel
    build_voxel(bone,cube)


def pack():
    atlas=Image.new('RGB',(SIZE,SIZE),(24,35,39))
    islands=[]
    rects=[]
    for c,f,m,w,h in surfaces:
        uw,vh=w*12,h*12
        rects.append((math.ceil(uw)+4,math.ceil(vh)+4,c,f,m,uw,vh))
    rects.sort(key=lambda a:(-a[1],-a[0]))
    x=y=shelf=0
    for pw,ph,c,f,m,uw,vh in rects:
        if x+pw>SIZE:x=0;y+=shelf;shelf=0
        assert y+ph<=SIZE,'Texture atlas budget exceeded'
        # Pure block material colors. All facial and armor detail is real geometry.
        patch=Image.new('RGB',(pw-4,ph-4),COLORS[m])
        atlas.paste(patch.resize((pw,ph)),(x,y));atlas.paste(patch,(x+2,y+2))
        c['uv'][f]={'uv':[x+2,y+2],'uv_size':[round(uw,4),round(vh,4)]}
        islands.append([x+2,y+2,round(uw,4),round(vh,4),m])
        x+=pw;shelf=max(shelf,ph)
    target=OUT/'textures/entity/wind_general.png'
    atlas.save(target,optimize=True)
    return islands


def animations():
    clips={}
    for name,length,walk in [('idle',4,False),('walk',1.1,True)]:
        tracks={}
        def track(b,k,values):tracks.setdefault(b,{})[k]={str(t):v for t,v in values}
        track('torso','rotation',[(0,[0,0,-.5]),(length/2,[1,0,.5]),(length,[0,0,-.5])])
        if walk:
            for tag,s in [('left',1),('right',-1)]:
                track('leg_'+tag,'rotation',[(0,[s*20,0,0]),(length/2,[-s*20,0,0]),(length,[s*20,0,0])])
                track('shin_'+tag,'rotation',[(0,[0,0,0]),(length/4,[14,0,0]),(length,[0,0,0])])
                track('arm_'+tag,'rotation',[(0,[-s*5,0,s*-7]),(length/2,[s*5,0,s*-7]),(length,[-s*5,0,s*-7])])
        for s in (-1,1):
            for j in range(3):
                for prefix in ('cape','ribbon'):
                    track(f'{prefix}_{s}_{j}','rotation',[(0,[8+j*3,0,s*3]),(length/2,[16+j*4,s*3,s*7]),(length,[8+j*3,0,s*3])])
            track('tassel_'+str(s),'rotation',[(0,[-4,0,s*5]),(length/2,[-12,0,s*11]),(length,[-4,0,s*5])])
        for j,angle in enumerate((-18,-30,-36),1):track('plume_'+str(j),'rotation',[(0,[angle,0,-2]),(length/2,[angle-5,3,3]),(length,[angle,0,-2])])
        track('hair_tail','rotation',[(0,[5,0,-2]),(length/2,[12,0,3]),(length,[5,0,-2])])
        clips[name]={'animation_length':length,'loop':True,'bones':tracks}
    return {'format_version':'1.8.0','animations':clips}


def export_blockbench(clips):
    """Self-contained editable project: the same rig, exact face UVs and embedded PNG."""
    def uid(name):return str(uuid.uuid5(uuid.NAMESPACE_URL,'tnc:wind_general/'+name))
    groups={b['name']:{'name':b['name'],'origin':b['pivot'],'rotation':b.get('rotation',[0,0,0]),
            'uuid':uid(b['name']),'export':True,'isOpen':False,'children':[]} for b in bones}
    elements=[]
    for b in bones:
        for i,c in enumerate(b['cubes']):
            at=c['origin'];end=[round(at[j]+c['size'][j],4) for j in range(3)]
            faces={f:{'uv':[q['uv'][0],q['uv'][1],round(q['uv'][0]+q['uv_size'][0],4),round(q['uv'][1]+q['uv_size'][1],4)],'texture':0} for f,q in c['uv'].items()}
            identity=uid(b['name']+'/'+str(i))
            elements.append({'name':b['name']+'_'+str(i),'type':'cube','uuid':identity,'from':at,'to':end,
                'origin':c.get('pivot',b['pivot']),'rotation':c.get('rotation',[0,0,0]),'box_uv':False,'faces':faces})
            groups[b['name']]['children'].append(identity)
    roots=[]
    for b in bones:
        if b.get('parent'):groups[b['parent']]['children'].append(groups[b['name']])
        else:roots.append(groups[b['name']])
    animations=[]
    for name,clip in clips['animations'].items():
        animators={}
        for bone,tracks in clip['bones'].items():
            keys=[]
            for channel,track in tracks.items():
                for time,value in track.items():
                    keys.append({'channel':channel,'time':float(time),'data_points':[dict(zip(('x','y','z'),value))],
                                 'interpolation':'linear','uuid':uid(name+'/'+bone+'/'+channel+'/'+time)})
            animators[uid(bone)]={'name':bone,'type':'bone','keyframes':keys}
        animations.append({'name':name,'uuid':uid('animation/'+name),'loop':'loop','length':clip['animation_length'],'animators':animators})
    model={'meta':{'format_version':'4.10','model_format':'bedrock','box_uv':False},'name':'Wind General',
           'model_identifier':'geometry.tnc.wind_general','resolution':{'width':SIZE,'height':SIZE},
           'elements':elements,'outliner':roots,'animations':animations,
           'textures':[{'name':'wind_general.png','id':'0','uuid':uid('texture'),'mode':'bitmap',
                        'source':'data:image/png;base64,'+base64.b64encode((OUT/'textures/entity/wind_general.png').read_bytes()).decode(),
                        'width':SIZE,'height':SIZE,'uv_width':SIZE,'uv_height':SIZE}]}
    (ROOT/'model-source/wind_general/wind_general.bbmodel').write_text(json.dumps(model,separators=(',',':'))+'\n',encoding='utf-8',newline='\n')


if __name__=='__main__':
    build();islands=pack()
    geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.tnc.wind_general','texture_width':SIZE,'texture_height':SIZE,'visible_bounds_width':6,'visible_bounds_height':9,'visible_bounds_offset':[0,3,0]},'bones':bones}]}
    for folder,name,value in [('geo/entity','wind_general.geo.json',geo),('animations/entity','wind_general.animation.json',animations())]:
        (OUT/folder/name).write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')
    source_dir=ROOT/'model-source/wind_general';source_dir.mkdir(parents=True,exist_ok=True)
    (source_dir/'uv_layout.json').write_text(json.dumps({'construction':'Cuboids only; stepped/angled silhouette, geometric facial features and armor; flat block colors, no photographic albedo or sculpted mesh',
        'pieces':sum(len(b['cubes']) for b in bones),'head_cuboids':next(len(b['cubes']) for b in bones if b['name']=='head'),
        'bones':len(bones),'atlas':[SIZE,SIZE],'palette':COLORS,'faces':islands},indent=2)+'\n',encoding='utf-8',newline='\n')
    export_blockbench(animations())
    print(len(bones),'bones;',sum(len(b['cubes']) for b in bones),'pieces;',len(islands),'unique UV faces')
