from pathlib import Path
import math,json,hashlib,base64,uuid,sys,argparse
from PIL import Image,ImageDraw
REPO_ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description="Generate an independent detailed fish-school model candidate.")
parser.add_argument("--output-root",type=Path,default=REPO_ROOT/"work/fish-school-detail-candidate")
parser.add_argument("--overwrite",action="store_true")
args=parser.parse_args();ROOT=args.output_root.resolve()
if not args.overwrite:
 for rel in ["model-source/fish-school/detail-spec.json","src/main/java/com/tnc/tnc/fish/client/FishSchoolModel.java","src/main/resources/assets/tnc/textures/entity/fish_school_living.png","src/main/resources/assets/tnc/textures/entity/fish_school_metal.png"]:
  if (ROOT/rel).exists():raise SystemExit("Candidate already exists; use a new output root or explicit --overwrite: "+str(ROOT/rel))
DENSITY=8; ATLAS=512
SPEC=ROOT/'model-source/fish-school/detail-spec.json'
PARTS=[]
def part(name,center,size,material,rotation=(0,0,0),tail=False,variants='both'):
 PARTS.append(dict(name=name,center=center,size=size,material=material,rotation=rotation,tail=tail,variants=variants))
part('streamlined_core',(0,0,0),(2.9,3.9,6.2),'scales')
part('shoulders',(0,-.1,2.8),(3.3,3.8,3.6),'scales')
part('tapered_rear',(0,0,-3.0),(2.1,2.8,2.5),'scales')
part('belly',(0,1.65,.2),(2.5,.7,5.4),'belly')
for side in [-1,1]:
 for up in [-1,1]:part(f'flank_bevel_{side}_{up}',(side*1.35,up*1.4,.1),(.65,1.2,5.8),'scales',(0,0,-side*up*22.5))
part('cheeks',(0,-.15,4.65),(2.9,3.15,2.5),'head')
part('upper_snout',(0,-.25,6.1),(2.3,2.35,1.5),'head')
part('snout_bevel',(0,-.25,6.85),(1.65,1.85,1.65),'head',(0,45,0))
part('lower_jaw',(0,1.0,5.85),(2.1,.45,2.2),'belly')
for s in [-1,1]:
 part('mouth_'+str(s),(s*1.17,.55,6.1),(.08,.12,1.7),'mouth')
 part('gill_cover_'+str(s),(s*1.55,.05,3.7),(.35,2.8,1.4),'head',(s*8,0,0))
 for j in range(3):part(f'gill_slit_{s}_{j}',(s*1.765,.15,3.3+j*.33),(.08,1.85,.1),'gill',(s*12,0,0))
 part('pectoral_'+str(s),(s*1.9,1.0,1.8),(.4,2.6,2.2),'fin',(0,s*12,-s*20))
 part('pelvic_'+str(s),(s*.8,2.1,-1.3),(.25,1.7,1.9),'fin',(-15,0,s*15))
 # Living eyes have rounded stepped sockets and two small highlights; metal eyes use a faceted bezel.
 part('eye_socket_'+str(s),(s*1.55,-.6,5.05),(.32,1.7,1.7),'socket',variants='living')
 part('eye_white_'+str(s),(s*1.77,-.6,5.05),(.22,1.4,1.4),'white',variants='living')
 part('eye_pupil_'+str(s),(s*1.91,-.55,5.28),(.12,.9,.82),'pupil',variants='living')
 part('eye_glint_'+str(s),(s*2.0,-.85,5.5),(.08,.27,.27),'white',variants='living')
 part('eye_bezel_'+str(s),(s*1.63,-.6,5.05),(.38,1.4,1.4),'gold',(45,0,0),variants='metal')
 part('eye_recess_'+str(s),(s*1.86,-.6,5.05),(.13,1.12,1.12),'socket',(45,0,0),variants='metal')
 part('eye_crystal_'+str(s),(s*1.97,-.6,5.05),(.17,.83,.83),'gem',(45,0,0),variants='metal')
 part('crystal_glint_'+str(s),(s*2.08,-.85,5.12),(.09,.25,.35),'white',(45,0,0),variants='metal')
 # A few overlapping physical plates carry the metallic body relief; scales themselves remain in the texture.
 for j in range(4):
  part(f'armor_{s}_{j}',(s*1.62,-.05,1.7-j*1.2),(.23,2.8,1.15),'armor',(s*4,0,0),variants='metal')
  part(f'armor_rivet_{s}_{j}',(s*1.78,-.9,1.9-j*1.2),(.13,.22,.22),'gold',variants='metal')
 part('operculum_rim_'+str(s),(s*1.76,-1.15,3.85),(.12,.18,1.4),'gold',variants='metal')
part('dorsal_rear',(0,-2.8,-1.45),(.35,2.2,3.6),'fin',(-18,0,0))
part('dorsal_leading',(0,-2.7,1.2),(.38,1.8,1.8),'fin',(22.5,0,0))
part('dorsal_metal_edge',(0,-3.9,-1.5),(.42,.2,3.2),'edge',(-18,0,0),variants='metal')
part('dorsal_copper_tip',(0,-4.1,-2.5),(.5,.35,.55),'gold',variants='metal')
# Tail parts are authored in global model coordinates; the exported bone pivots at z=-3.
part('tail_peduncle',(0,0,-4.5),(1.3,1.8,2.8),'scales',tail=True)
part('tail_upper',(0,-1.7,-6.45),(.45,2.6,2.6),'tail',(-18,0,0),tail=True)
part('tail_lower',(0,1.7,-6.45),(.45,2.6,2.6),'tail',(18,0,0),tail=True)
part('tail_upper_tip',(0,-2.7,-7.1),(.4,1.5,1.5),'tail',(-28,0,0),tail=True)
part('tail_lower_tip',(0,2.7,-7.1),(.4,1.5,1.5),'tail',(28,0,0),tail=True)
part('tail_upper_edge',(0,-3.2,-7.2),(.5,.2,1.5),'edge',(-28,0,0),tail=True,variants='metal')
part('tail_lower_edge',(0,3.2,-7.2),(.5,.2,1.5),'edge',(28,0,0),tail=True,variants='metal')
part('tail_socket',(0,0,-5.2),(1.4,1.95,.45),'gold',tail=True,variants='metal')
# Keep everything inside the existing physical hull, including the full animated tail swing.
def vertices(p,angle=0):
 c=p['center'];sz=p['size'];rx,ry,rz=[math.radians(v) for v in p['rotation']]
 for ix in [-1,1]:
  for iy in [-1,1]:
   for iz in [-1,1]:
    x,y,z=[sz[i]*v/2 for i,v in enumerate((ix,iy,iz))]
    y,z=y*math.cos(rx)-z*math.sin(rx),y*math.sin(rx)+z*math.cos(rx)
    x,z=x*math.cos(ry)+z*math.sin(ry),-x*math.sin(ry)+z*math.cos(ry)
    x,y=x*math.cos(rz)-y*math.sin(rz),x*math.sin(rz)+y*math.cos(rz)
    x,y,z=x+c[0],y+c[1],z+c[2]
    if p['tail']:x,z=x*math.cos(angle)+(z+3)*math.sin(angle),-x*math.sin(angle)+(z+3)*math.cos(angle)-3
    yield x*.7/16,y*.7/16,z*.7/16
# Budget: 90 selected cubes/fish, 1080 cubes per twelve-fish school. No baked animation frames.
for variant in ['living','metal']:
 selected=[p for p in PARTS if p['variants'] in ('both',variant)]
 assert len(selected)<=90
 for p in selected:
  for phase in range(65):
   a=math.sin(phase*math.pi/32)*(.35 if variant=='living' else .12)
   for x,y,z in vertices(p,a):
    assert abs(x)<=.18+1e-6,(p['name'],'width',x)
    assert abs(y)<=.23+1e-6,(p['name'],'height',y)
    assert abs(z)<=.36+1e-6,(p['name'],'length',z)
print('Selected cubes:',{v:sum(p['variants'] in ('both',v) for p in PARTS) for v in ['living','metal']})
# Allocate independent padded UV nets at eight texels per old model unit, not a resized old texture.
x=y=2;rowh=0
for p in PARTS:
 w,h,d=[v*DENSITY for v in p['size']];uw=math.ceil(2*(w+d))+2;vh=math.ceil(d+h)+2
 if x+uw>ATLAS-2:x=2;y+=rowh+2;rowh=0
 assert y+vh<=ATLAS-2,(p['name'],y+vh)
 p['uv']=[x,y];p['uv_size']=[uw,vh];x+=uw+2;rowh=max(rowh,vh)
SPEC.parent.mkdir(parents=True,exist_ok=True);SPEC.write_text(json.dumps({'texture_size':ATLAS,'density':DENSITY,'parts':PARTS},ensure_ascii=False,indent=2),encoding='utf-8')
for variant in ['living','metal']:
 metal=variant=='metal';im=Image.new('RGBA',(ATLAS,ATLAS),'#516d87');draw=ImageDraw.Draw(im)
 colors={'scales':'#8faabf' if metal else '#6ca8cb','head':'#adbfcd' if metal else '#8ac3d5','belly':'#e1ebef' if metal else '#e2eed9','fin':'#2c4563' if metal else '#347ca7','tail':'#304d70' if metal else '#3480a6','socket':'#273c54' if metal else '#326c8e','white':'#effaff','pupil':'#132e42','gill':'#493e36' if metal else '#a45b6b','gold':'#c5a15c','gem':'#39e6ea','armor':'#91a9bd','edge':'#e4f3ff','mouth':'#394a5c' if metal else '#5e6570'}
 for p in PARTS:
  if p['variants'] not in ('both',variant):continue
  u,v=p['uv'];uw,vh=p['uv_size'];m=p['material'];draw.rectangle((u,v,u+uw-1,v+vh-1),fill=colors[m])
  if not metal and p['name'] in ['streamlined_core','shoulders','tapered_rear']:
   depth=p['size'][2]*DENSITY;height=p['size'][1]*DENSITY
   draw.rectangle((u,v+int(depth+height*.6),u+uw-1,v+vh-1),fill='#dce9d7')
  if m in ['scales','armor']:
   for yy in range(v+2,v+vh-2,5):
    for xx in range(u+1+(3 if ((yy-v)//5)%2 else 0),u+uw-3,6):
     draw.line([(xx,yy),(xx+2,yy+2),(xx+5,yy)],fill='#e0edf4' if metal else '#b6dce4',width=1)
     draw.line([(xx,yy+1),(xx+2,yy+3),(xx+5,yy+1)],fill='#4f6a85' if metal else '#4383ad',width=1)
  if m in ['fin','tail']:
   for xx in range(u+2,u+uw-2,4):draw.line([(u+uw//2,v+1),(xx,v+vh-2)],fill='#a4c0d6' if metal else '#98d9e1',width=1)
  if m in ['head','armor','edge','gold']:
   draw.line((u+1,v+1,u+uw-2,v+1),fill='#f4fbff' if metal else '#cee8e3',width=1)
   draw.line((u+1,v+vh-2,u+uw-2,v+vh-2),fill='#435d79' if metal else '#497d9a',width=1)
  if m=='gem':
   draw.line((u+1,v+1,u+uw//2,v+1),fill='#dbffff',width=2);draw.point((u+uw-2,v+vh-2),fill='#087782')
 dst=ROOT/f'src/main/resources/assets/tnc/textures/entity/fish_school_{variant}.png';dst.parent.mkdir(parents=True,exist_ok=True);im.save(dst)
# The same spec generates native ModelPart geometry. Scale inside the model preserves the existing world-space size/hitbox.
def num(v):return f'{v:.6f}'.rstrip('0').rstrip('.')+'f' if v else '0f'
lines=['package com.tnc.tnc.fish.client;','', 'import com.mojang.blaze3d.vertex.PoseStack;','import com.mojang.blaze3d.vertex.VertexConsumer;','import net.minecraft.client.model.geom.*;','import net.minecraft.client.model.geom.builders.*;','', '/** Generated from model-source/fish-school/detail-spec.json; texture density is 8 texels/model unit. */','public final class FishSchoolModel {','    private final ModelPart root,tail;','    public FishSchoolModel(ModelPart root) { this.root=root;tail=root.getChild("tail"); }','    public static LayerDefinition layer(boolean metal) {','        MeshDefinition mesh=new MeshDefinition();PartDefinition root=mesh.getRoot();','        PartDefinition tail=root.addOrReplaceChild("tail",CubeListBuilder.create(),PartPose.offset(0,0,-24));']
for p in PARTS:
 if p['variants']!='both':lines.append('        if ('+('metal' if p['variants']=='metal' else '!metal')+') {')
 parent='tail' if p['tail'] else 'root';c=list(p['center']);c[2]+=3 if p['tail'] else 0;sz=p['size'];a=[-v*DENSITY/2 for v in sz];dims=[v*DENSITY for v in sz];u,v=p['uv'];rot=[math.radians(v) for v in p['rotation']]
 lines.append('        '+parent+'.addOrReplaceChild("'+p['name']+'",CubeListBuilder.create().texOffs('+str(u)+','+str(v)+').addBox('+','.join(num(v) for v in a+dims)+'),PartPose.offsetAndRotation('+','.join(num(v*DENSITY) for v in c)+','+','.join(num(v) for v in rot)+'));')
 if p['variants']!='both':lines.append('        }')
lines+=['        return LayerDefinition.create(mesh,512,512);','    }','    public void render(PoseStack pose,VertexConsumer vertices,int light,int overlay,double age,int lane,boolean metal) {','        tail.yRot=(float)Math.sin(age*.85+lane*1.3)*(metal?.12f:.35f);','        pose.pushPose();pose.scale(.125f,.125f,.125f);','        root.render(pose,vertices,light,overlay,1,1,1,1);','        pose.popPose();','    }','}']
p=ROOT/'src/main/java/com/tnc/tnc/fish/client/FishSchoolModel.java';p.parent.mkdir(parents=True,exist_ok=True);p.write_text('\n'.join(lines)+'\n',encoding='utf-8')





