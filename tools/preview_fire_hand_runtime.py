"""Preview the actual FireHandMesh export, without inventing replacement geometry or shader effects.

Compile/run tools/FireHandModelExport.java first. Outputs static GLB poses and a labelled QA sheet.
The PNG uses ordinary alpha blending; the game's heat-refraction post effect is deliberately absent.
"""
from pathlib import Path
import argparse,json,math,struct
import numpy as np
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parents[1]

def normalize(v):
    length=np.linalg.norm(v)
    return v/max(1e-9,length)

def faces(data):
    scale=.7+.3*data['tier'];height=.9+.42*data['tier'];age=data['age']
    t=max(0,min(1,age/14));emerge=-2.8*(1-t*t*(3-2*t))
    result=[]
    for q in data['quads']:
        xyz=np.array(q['xyz'],dtype=float)*scale+np.array([0,height+emerge*scale,0])
        alpha=np.clip((xyz[:,1]+.16)/.65,0,1);alpha=alpha*alpha*(3-2*alpha)
        result.append((xyz,np.array(q['rgba']),alpha,q['layer']))
    return result

def render(data,width=480,height=600,back=False,wide=False):
    # Fixed camera across phases: changes in hand silhouette and size remain visible.
    center=np.array([-.10,2.8 if wide else 2.45,0]);eye=center+normalize(np.array([1.8,.7,-4.4 if back else 4.4]))*(14 if wide else 11)
    forward=normalize(center-eye);right=normalize(np.cross(forward,[0,1,0]));up=np.cross(right,forward)
    matrix=np.stack([right,up,forward]);f=1/math.tan(math.radians(35)/2)*height/2
    yy=np.arange(height)[:,None,None]/height
    color=np.broadcast_to(np.array([.026,.029,.037])*(1-yy)+np.array([.07,.058,.049])*yy,(height,width,3)).copy()
    flat=[]
    for xyz,rgba,alpha,layer in faces(data):
        camera=(xyz-eye)@matrix.T
        if camera[:,2].min()<.05:continue
        p=np.stack([camera[:,0]/camera[:,2]*f+width/2,height/2-camera[:,1]/camera[:,2]*f,camera[:,2]],axis=1)
        # Render batches match the runtime layer order, depth-sorted within a batch.
        flat.append(({'STRUCTURE':0,'PLASMA':1,'CORONA':2}[layer],-camera[:,2].mean(),p,rgba,alpha))
    flat.sort(key=lambda x:(x[0],x[1]))
    for layer,depth,p,rgba,alpha in flat:
        for inds in ((0,1,2),(0,2,3)):
            tri=p[list(inds)]; aa=alpha[list(inds)]*rgba[3]
            x0=max(0,int(np.floor(tri[:,0].min())));x1=min(width,int(np.ceil(tri[:,0].max()))+1)
            y0=max(0,int(np.floor(tri[:,1].min())));y1=min(height,int(np.ceil(tri[:,1].max()))+1)
            if x1<=x0 or y1<=y0:continue
            ax,ay=tri[0,:2];bx,by=tri[1,:2];cx,cy=tri[2,:2]
            area=(by-cy)*(ax-cx)+(cx-bx)*(ay-cy)
            if abs(area)<1e-7:continue
            xx,yy=np.meshgrid(np.arange(x0,x1)+.5,np.arange(y0,y1)+.5)
            u=((by-cy)*(xx-cx)+(cx-bx)*(yy-cy))/area
            v=((cy-ay)*(xx-cx)+(ax-cx)*(yy-cy))/area;w=1-u-v
            inside=(u>=0)&(v>=0)&(w>=0)
            opacity=(u*aa[0]+v*aa[1]+w*aa[2])*inside
            dst=color[y0:y1,x0:x1]
            dst[:]=dst*(1-opacity[:,:,None])+rgba[:3]*opacity[:,:,None]
    # Gamma encode after alpha compositing, without extra bloom or concept-only lighting.
    return Image.fromarray((np.clip(color,0,1)**(1/2.2)*255).astype(np.uint8))

def glb(data,path):
    vertices=[];colors=[];indices=[]
    for xyz,rgba,alpha,layer in faces(data):
        start=len(vertices);vertices.extend(xyz.tolist());colors.extend([[*rgba[:3],float(a*rgba[3])] for a in alpha]);indices.extend([start,start+1,start+2,start,start+2,start+3])
    pos=np.asarray(vertices,dtype='<f4');col=np.asarray(colors,dtype='<f4');ind=np.asarray(indices,dtype='<u4')
    chunks=[pos.tobytes(),col.tobytes(),ind.tobytes()];blob=b''.join(chunks)
    doc={'asset':{'version':'2.0','generator':'FireHandMesh exact runtime export'},'scene':0,'scenes':[{'nodes':[0]}],
         'nodes':[{'mesh':0,'name':f"Fire hand / tick {data['age']:g}"}],
         'buffers':[{'byteLength':len(blob)}],
         'bufferViews':[],'accessors':[
             {'bufferView':0,'componentType':5126,'count':len(pos),'type':'VEC3','min':pos.min(axis=0).tolist(),'max':pos.max(axis=0).tolist()},
             {'bufferView':1,'componentType':5126,'count':len(col),'type':'VEC4'},
             {'bufferView':2,'componentType':5125,'count':len(ind),'type':'SCALAR'}],
         'materials':[{'name':'Plasma vertex colors','pbrMetallicRoughness':{'baseColorFactor':[1,1,1,1],'metallicFactor':0,'roughnessFactor':.7},'alphaMode':'BLEND','doubleSided':True,'extensions':{'KHR_materials_unlit':{}}}],
         'extensionsUsed':['KHR_materials_unlit'],
         'meshes':[{'primitives':[{'attributes':{'POSITION':0,'COLOR_0':1},'indices':2,'material':0}]}]}
    offset=0
    for i,chunk in enumerate(chunks):doc['bufferViews'].append({'buffer':0,'byteOffset':offset,'byteLength':len(chunk),'target':34963 if i==2 else 34962});offset+=len(chunk)
    meta=json.dumps(doc,separators=(',',':')).encode();meta+=b' '*((-len(meta))%4);blob+=b'\0'*((-len(blob))%4)
    total=12+8+len(meta)+8+len(blob)
    path.write_bytes(struct.pack('<III',0x46546c67,2,total)+struct.pack('<II',len(meta),0x4e4f534a)+meta+struct.pack('<II',len(blob),0x004e4942)+blob)

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--input',type=Path,default=ROOT/'work/fire-hand-refinement-20261113/mesh');ap.add_argument('--output',type=Path,default=ROOT/'model-source/fire-plasma-hand');a=ap.parse_args();a.output.mkdir(parents=True,exist_ok=True)
    poses=[(22,'01 / OPEN  ·  tick 22'),(36,'02 / CRUSH  ·  tick 36'),(66,'03 / RUPTURE  ·  tick 66')]
    font_path=Path('C:/Windows/Fonts/arial.ttf');font=ImageFont.truetype(str(font_path),18) if font_path.exists() else ImageFont.load_default()
    sheet=Image.new('RGB',(1440,1250),(13,14,18));draw=ImageDraw.Draw(sheet)
    draw.text((26,14),'FIRE HAND  /  exact runtime mesh  /  tier 3, heat 80',(236,213,177),font=font)
    for index,(tick,label) in enumerate(poses):
        data=json.loads((a.input/f'pose-{tick}.json').read_text())
        for view in range(2):sheet.paste(render(data,480,580,bool(view)),(index*480,55+view*590))
        draw.text((index*480+20,39),label,(232,193,142),font=font)
        glb(data,a.output/f'fire-hand-tick-{tick}.glb')
    draw.text((24,1228),'Top: palm side  /  Bottom: tendon side  /  ordinary alpha, no screen refraction or added bloom',(170,161,151),font=font)
    sheet.save(a.output/'fire-hand-runtime-preview.png')
    palette_sheet=Image.new('RGB',(1440,650),(13,14,18));pd=ImageDraw.Draw(palette_sheet)
    pd.text((24,12),'GIANT HAND / tier-specific fire materials / exact runtime geometry',(236,213,177),font=font)
    for i,(filename,title) in enumerate([('pose-36.json','T3 / EMBER'),('pose-blue-36.json','T4 / BLUE GHOSTFIRE'),('pose-ion-36.json','T5 / WHITE-BLUE ION')]):
        data=json.loads((a.input/filename).read_text());palette_sheet.paste(render(data,480,580,wide=True),(i*480,55));pd.text((i*480+24,37),title,(232,193,142),font=font)
        if i:glb(data,a.output/('fire-hand-blue-ghost.glb' if i==1 else 'fire-hand-white-blue-ion.glb'))
    palette_sheet.save(a.output/'fire-hand-fire-palettes.png')

    # A small motion reference, again using runtime mesh exports and the same fixed camera.
    ticks=[14,22,28,32,36,46,56,62,66,72];frames=[]
    for tick in ticks:
        data=json.loads((a.input/f'pose-{tick}.json').read_text());im=render(data,320,400);ImageDraw.Draw(im).text((12,12),f'tick {tick}',fill=(238,215,177));frames.append(im)
    frames[0].save(a.output/'fire-hand-runtime-motion.gif',save_all=True,append_images=frames[1:],duration=[400,280,180,160,280,280,280,200,220,400],loop=0)
    verification=json.loads((a.input/'verification.json').read_text());verification['preview']='Direct offline alpha compositing of runtime quads; not a Minecraft screenshot.'
    (a.output/'verification.json').write_text(json.dumps(verification,indent=2)+'\n',encoding='utf8')
    print('Exported',a.output)

if __name__=='__main__':main()
