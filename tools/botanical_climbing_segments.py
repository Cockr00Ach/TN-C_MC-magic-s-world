"""Continuous living stems for the two botanical plants that grow real climbable blocks."""
from math import cos, sin, pi, tau
import json
from pathlib import Path
from botanical_surface import Mesh, mix, radial, plus, write_bbmodel
from rebuild_magic_plant_world_models import ASSETS, ROOT

PLANTS={'ladder_vine':('botanical_vine_segment','梯脊藤'), 'paper_tree':('botanical_paper_segment','墨信树')}

def axis(species,stem,height):
    phase=.4+.36*tau+pi*stem+height*(.20 if species=='paper_tree' else .33)*tau
    rad=.69 if species=='paper_tree' else 1.35
    return radial((8,0,8),phase,rad)

def join_root(m):
    if m.species not in PLANTS or not m.age: return
    # The sculpted trunk is eight tapered sections of seven faces. Its end-ring
    # centroid accounts for the stage-specific narrowing and optional height rescale.
    ring=[part[1][2] for part in m.faces[49:56]]
    center=tuple(sum(p[k] for p in ring)/7 for k in range(3))
    count=1 if m.species=='paper_tree' else 2
    for stem in range(count):
        start=center if stem==0 else (16-center[0],center[1],16-center[2])
        x,_,z=axis(m.species,stem,0)
        end=(x,15.997,z)
        m.part='抽高的顶芽'
        m.stem([start,mix(start,end,.5),(x,15.66,z),end],[.27,.30,.35,.34],0,7)

def segment(species,level):
    paper=species=='paper_tree'
    m=Mesh(species,level);strands=[]
    for k in range(1 if paper else 2):
        body=[]
        for j in range(13):
            f=j/12
            # At cell boundaries both curves share a vertical tangent and radius.
            phase=level-1+(3*f*f-2*f*f*f)
            x,_,z=axis(species,k,phase)
            body.append((x,16*f,z))
        strands.append(body)
        m.part='书皮主干' if paper else '对绞的活藤'
        widths=[.34+.12*sin(pi*j/12) for j in range(13)]
        if (paper and level>=2) or level==3:
            for j in range(10,13):widths[j]*=(13-j)/3
        m.stem(body,widths,0,7)
        for j in (3,6,9,11):
            y=body[j][1];a=(level*4+j)*2.399+k*pi
            h=1.25 if j<10 else .70
            reach=(3.9 if paper else 2.45)*(.72+.24*sin(j*1.56))
            tip=radial(body[j],a,reach,h)
            m.part='垂挂纸叶' if paper else '新叶和卷须'
            m.stem([body[j],radial(body[j],a-.24,reach*.50,h*.14),tip],[.21,.15,.075],0,5)
            if paper:
                m.blade(tip,a+.68,2.18,2.07,-1.85,.40,3,twist=-.42,steps=4)
                m.blade(tip,a-.59,1.74,1.46,-1.29,.32,2,twist=.47,steps=4)
                spine=radial(tip,a+.67,.62,-.18)
                m.wire([tip,spine,radial(spine,a+.95,.7,-.63)],.045,4)
            else:
                m.blade(tip,a+.49,1.98,1.35,.97,.50,1,twist=.26)
                if j in (3,9):
                    arm=radial(tip,a-.54,1.35,.34)
                    m.wire([tip,arm,radial(arm,a+.15,.75,.33),radial(arm,a+.89,.71,.63),radial(arm,a+1.44,.33,.83)],.086,3)
    if not paper:
        m.part='可攀的藤筋'
        for j in (3,7,10):
            l,r=strands[0][j],strands[1][j+1]
            m.stem([l,plus(mix(l,r,.51),(0,-.22,0)),r],[.15,.20,.14],3,5)
    # Keep tips in the cell and the mating surfaces coplanar.
    m.faces=[(org,[(x,max(0.,min(16.,y)),z) for x,y,z in pts],uv,g) for org,pts,uv,g in m.faces]
    return m

def generate(projects=None):
    written=[]
    def save(p,data):
        p.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf8')
        written.append(p)
    for species,(stem,name) in PLANTS.items():
        variants={}
        for height in (1,2,3):
            mid=stem if height==1 else f'{stem}_{height}'
            geo='botanical_meshes/'+mid+'.json'
            save(ASSETS/geo,segment(species,height).export_faces())
            save(ASSETS/f'models/block/{mid}.json',dict(loader='tnc:botanical_mesh',render_type='minecraft:cutout',
                    ambientocclusion=False,glow_enabled=True,textures={'plant':'tnc:block/botanical/'+species,'particle':'tnc:block/botanical/'+species},geometry='tnc:'+geo))
            variants[f'segment={height}']={'model':'tnc:block/'+mid}
        save(ASSETS/f'blockstates/{stem}.json',dict(variants=variants))
        if projects:
            from rebuild_botanical_sculptures import make
            whole=make(species,3)
            for h in range(1,3 if species=='paper_tree' else 4):
                extra=segment(species,h)
                whole.faces.extend((f'第{h}节 · '+n,[(x,y+h*16,z) for x,y,z in pts],uv,g) for n,pts,uv,g in extra.faces)
            write_bbmodel(whole,name+'整株',ASSETS/f'textures/block/botanical/{species}.png',Path(projects)/'攀生整株'/(name+'.bbmodel'))
    return written

