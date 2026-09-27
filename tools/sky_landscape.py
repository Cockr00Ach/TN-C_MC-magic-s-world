"""Offline, deterministic sky-island landscape revision; never opens a live save."""
from pathlib import Path
import argparse
from collections import Counter
import json
import gzip
import io
import numpy as np
import nbtlib
from PIL import Image
from export_town_structures import state_text, state_tag
from generate_sky_island_structures import boundary_radius

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'modpack' / '元素觉醒1.4.3-魔改版-20260915' / 'kubejs' / 'data' / 'tnc'
NATURAL = {'grass_block', 'dirt', 'coarse_dirt', 'rooted_dirt', 'stone', 'andesite',
           'granite', 'diorite', 'gravel', 'sand', 'moss_block', 'tuff', 'podzol'}
TAVERN_ORIGIN=(416,89,270)
TAVERN_DOOR=(419,90,291)

def opaque_cover(name):
    block=name.split('[')[0].split(':')[-1]
    return block in NATURAL|{'cobblestone','stone_bricks'} or block.endswith(('_planks','_log','_wood'))

def edge_profiles(heights):
    profiles={}
    for key,line in [('west',heights[92:429,120]),('east',heights[92:429,400]),
                     ('north',heights[92,120:401]),('south',heights[428,120:401])]:
        levels=[]
        for radius in range(65):
            if radius==0:levels.append(line.astype(float));continue
            kernel=np.exp(-.5*(np.arange(-radius,radius+1)/(radius/2+.5))**2);kernel/=kernel.sum()
            levels.append(np.convolve(np.pad(line.astype(float),radius,mode='edge'),kernel,mode='valid'))
        profiles[key]=levels
    return profiles

def blend_height(x,z,base,heights,profiles=None):
    if 120<=x<=400 and 92<=z<=428:return int(base)
    bx,bz=min(400,max(120,x)),min(428,max(92,z))
    distance=float(np.hypot(x-bx,z-bz))
    rim=boundary_radius(float(np.arctan2(z-260,x-260)))-float(np.hypot(x-260,z-260))
    if rim<=8:return int(base)
    edge=float(heights[bz,bx])
    if profiles is not None:
        # Smooth along the cut as we travel outward, rather than extruding every hill as a rib.
        radius=min(64,round(distance*.8));dx,dz=abs(x-bx),abs(z-bz)
        vx=profiles['west' if x<120 else 'east'][radius][bz-92]
        vz=profiles['north' if z<92 else 'south'][radius][bx-120]
        smooth=(vx*dx+vz*dz)/max(1,dx+dz)
        t=min(1,distance/12);edge=edge*(1-t)+smooth*t
    delta=max(0,edge-base)
    width=max(32,delta*1.9)+5*np.sin(z*.061+x*.037)
    # Fade before the outer cliff, even where the source hill meets a corner.
    width=min(width,distance+max(0,rim-8))
    t=max(0,min(1,1-distance/max(1,width)))
    return round(base+delta*t*t*(3-2*t))

def build_revision(scene,names,entities):
    original=scene.copy(); names=list(names);ids={s:i for i,s in enumerate(names)}
    def sid(s):
        if s not in ids:ids[s]=len(names);names.append(s)
        return ids[s]
    heights=terrain_heights(original,names)
    profiles=edge_profiles(heights)
    grass=sid('minecraft:grass_block[snowy=false]');dirt=sid('minecraft:dirt');stone=sid('minecraft:stone')
    # The transition grows outwards only: not one town voxel is removed or raised.
    for z in range(520):
        for x in range(520):
            base=int(heights[z,x])
            if base<55:continue
            top=blend_height(x,z,base,heights,profiles)
            if top<=base:continue
            for y in range(base,top+1):
                scene[y,z,x]=grass if y==top else dirt if y>=top-3 else stone
    # Gently raise an organic meadow underneath the tavern and its approach.
    ox,oy,oz=TAVERN_ORIGIN
    for z in range(oz-14,oz+58+14):
        for x in range(ox-14,ox+71+14):
            if x<=400:continue
            base=int(heights[z,x]);distance=np.hypot(max(ox-x,0,x-(ox+70)),max(oz-z,0,z-(oz+57)))
            t=max(0,1-distance/14);top=round(base+max(0,oy-base)*t*t*(3-2*t))
            for y in range(base,top+1):scene[y,z,x]=grass if y==top else dirt
    # Clear only the reviewed building envelope, with all old states retained in the patch.
    scene[oy:oy+43,oz:oz+58,ox:ox+71]=0
    resources=ROOT/'src/main/resources/data/tnc'
    manifest=json.loads((resources/'buildings/fantasy_tavern.json').read_text(encoding='utf-8'))
    for p in manifest['pieces']:
        t=nbtlib.load(resources/'structures'/(p['resource'].split(':')[1]+'.nbt'))
        palette=[state_text(s) for s in t['palette']]
        for b in t['blocks']:
            if 'nbt' in b:raise ValueError('Tavern block entities need an explicit migration policy')
            pos=np.array(b['pos'].unpack())+np.array(p['offset'])-np.array([24,0,24])+np.array(TAVERN_ORIGIN)
            x,y,z=pos;scene[y,z,x]=sid(palette[int(b['state'])])
    # Solid continuous support at ground level, also under the sparse template edges.
    for z in range(oz,oz+58):
        for x in range(ox,ox+71):
            base=int(heights[z,x])
            for y in range(base,oy):scene[y,z,x]=dirt
            if scene[oy,z,x]==0:scene[oy,z,x]=grass
    # Continue the ACTUAL east farm path at (397,84,265), then climb gently outside town.
    path=sid('minecraft:dirt_path')
    route={};nodes=[(397,84,265),(403,84,265),(411,89,279),(411,89,291),(419,89,291)]
    for a,b in zip(nodes,nodes[1:]):
        for t in np.linspace(0,1,max(abs(b[0]-a[0]),abs(b[2]-a[2]))*3+1):
            cx,cy,cz=np.rint(np.array(a)*(1-t)+np.array(b)*t).astype(int)
            for x in range(cx-1,cx+2):
                for z in range(cz-1,cz+2):
                    if x<=TAVERN_DOOR[0]:route[x,z]=max(route.get((x,z),0),int(cy))
    route.pop((397,266),None) # Existing composter beside the road; retain it in place.
    for (x,z),target in route.items():
        base=min(int(heights[z,x]),target)
        if x<=400:
            allowed={'minecraft:air','minecraft:dirt','minecraft:grass_block','minecraft:dirt_path','minecraft:farmland',
                     'minecraft:grass','minecraft:wheat','minecraft:carrots','minecraft:potatoes','minecraft:beetroots'}
            for v in original[base:target+4,z,x]:
                if names[v].split('[')[0] not in allowed:raise ValueError(f'Approach would touch town architecture {x,z,names[v]}')
        for y in range(base,target):scene[y,z,x]=dirt
        scene[target,z,x]=path
        for dx,dz,facing in [(-1,0,'east'),(0,-1,'south'),(1,0,'west'),(0,1,'north')]:
            if (x+dx,z+dz) in route and route[x+dx,z+dz]<target:
                scene[target,z,x]=sid(f'minecraft:stone_brick_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]');break
        scene[target+1:target+4,z,x]=0
    # Both older and current expanded portals are runtime-placed, absent from the scene manifest.
    # Preserve their entire reservation including approach and future visual structures.
    scene[:,400:449,185:234]=original[:,400:449,185:234]
    # Exposed intermediate meadow caps that end up under foundations must not remain grass.
    yy,zz,xx=np.where((scene==grass)&(scene!=original))
    for y,z,x in zip(yy,zz,xx):
        if y+1<scene.shape[0] and opaque_cover(names[scene[y+1,z,x]]):scene[y,z,x]=dirt
    changed=scene!=original
    # Include air in the building envelope as guards: a player room cannot hide between voxels.
    guards=changed.copy();guards[oy:oy+43,oz:oz+58,ox:ox+71]=True
    for x,y,z in entities:
        if guards[y,z,x]:raise ValueError(f'Would touch original block entity at {x,y,z}')
    protected=changed[:,92:429,120:401].copy()
    for (x,z),target in route.items():
        if 120<=x<=400 and 92<=z<=428:protected[:,z-92,x-120]=False
    if protected.any():raise ValueError('Town protection mask violated outside the small path connection')
    assert np.all(scene[oy-1,oz:oz+58,ox:ox+71]!=0)
    for z in (oz+21,oz+22):
        assert names[scene[oy+1,z,ox+4]].startswith('minecraft:spruce_door[')
    return original,scene,names,guards,changed

def export_patch(before,after,names,guards,output):
    # Only used states are included, so unrelated Conquest blocks are not dependencies.
    y,z,x=np.where(guards);old=before[y,z,x];new=after[y,z,x]
    used=sorted(set(old.tolist())|set(new.tolist()));mapping={v:i for i,v in enumerate(used)}
    old=np.array([mapping[int(v)] for v in old]);new=np.array([mapping[int(v)] for v in new])
    tile_ids=(z//16)*33+x//16
    tiles=[]
    for tile in sorted(set(tile_ids.tolist())):
        take=tile_ids==tile
        rows=np.column_stack((x[take],y[take],z[take],old[take],new[take])).astype(np.int32)
        # Finish ground-up columns together instead of leaving whole exposed intermediate layers.
        values=rows[np.lexsort((rows[:,1],rows[:,0],rows[:,2]))].ravel()
        tiles.append(nbtlib.Compound({'Changes':nbtlib.IntArray(values)}))
    root=nbtlib.File({'Revision':nbtlib.Int(1),'Tavern':nbtlib.IntArray(TAVERN_DOOR),
         'Palette':nbtlib.List[nbtlib.Compound]([state_tag(names[i]) for i in used]),
         'Tiles':nbtlib.List[nbtlib.Compound](tiles)})
    output.parent.mkdir(parents=True,exist_ok=True)
    buffer=io.BytesIO();root.write(buffer)
    output.write_bytes(gzip.compress(buffer.getvalue(),mtime=0))
    return {'tiles':len(tiles),'checked_voxels':len(x),'changed_voxels':int(np.count_nonzero(before!=after)),
            'tavern_origin':TAVERN_ORIGIN,'tavern_entrance':TAVERN_DOOR,'palette_size':len(used)}

def audit_save(world,completed=False):
    from export_stonecrest_fortress import WorldReader
    from survey_world_buildings import section_array
    state=nbtlib.load(world/'data/tnc_sky_island_v5.dat')['data']
    origin=np.array([int(state['OriginX']),int(state['OriginY']),int(state['OriginZ'])])
    patch=nbtlib.load(ROOT/'src/main/resources/data/tnc/sky_island/landscape_v1.nbt')
    palette=patch['Palette'];reader=WorldReader(world);mismatches=Counter();examples=[];be_hits=[]
    for tile in patch['Tiles']:
        values=np.array(tile['Changes']).reshape(-1,5)
        coords=values[:,:3]+origin
        for cx,sy,cz in np.unique(coords//16,axis=0):
            mask=np.all(coords//16==[cx,sy,cz],axis=1)
            points=coords[mask];pairs=values[mask,3:]
            chunk=reader.chunk(int(cx),int(cz))
            if chunk is None:raise ValueError(f'Missing chunk {cx,cz}')
            arr,pal=section_array(chunk,int(sy))
            if arr is None:actuals=np.zeros(len(points),dtype=int);pal=None
            else:actuals=arr[points[:,1]%16,points[:,2]%16,points[:,0]%16]
            for pi,(point,pair,actual) in enumerate(zip(points,pairs,actuals)):
                a=pal[int(actual)] if pal else None
                name=a.name if a else 'minecraft:air';props=dict(a.properties) if a else {}
                compatible=False
                for expected in (pair[1:] if completed else pair):
                    b=palette[int(expected)]
                    expected_props={str(k):str(v) for k,v in b.get('Properties',{}).items()}
                    if name.endswith('_leaves'):expected_props.pop('distance',None)
                    if name==str(b['Name']) and all(props.get(k)==v for k,v in expected_props.items()):compatible=True
                if name=='minecraft:water' and int(props.get('level','0'))>0 and str(palette[int(pair[0])]['Name'])=='minecraft:air' and str(palette[int(pair[1])]['Name']).split(':')[-1] in NATURAL:
                    compatible=not completed
                if name=='minecraft:grass_block' and str(palette[int(pair[1])]['Name'])=='minecraft:dirt' and str(palette[int(pair[0])]['Name'])=='minecraft:air':compatible=True
                if not compatible:
                    key=(str(palette[int(pair[0])]['Name']),name);mismatches[key]+=1
                    if mismatches[key]<=3:examples.append((point.tolist(),key,props))
            guard=set(map(tuple,points.tolist()))
            for be in chunk.block_entities:
                p=tuple(int(be[k].value) for k in ('x','y','z'))
                if p in guard and not (completed and str(be['id'].value)=='minecraft:campfire'):be_hits.append(p)
    result={'origin':origin.tolist(),'mismatches':[(a,b,n) for (a,b),n in mismatches.most_common()],
            'examples':examples,'block_entities':be_hits}
    print(json.dumps(result,ensure_ascii=False),flush=True)
    return result

def load_scene():
    manifest = json.loads((PACK / 'sky_island/manifest.json').read_text(encoding='utf-8'))
    w,h,d = manifest['dimensions']
    scene = np.zeros((h,d,w), dtype=np.uint16)
    names = ['minecraft:air']; ids = {names[0]:0}
    entities = set()
    for i,p in enumerate(manifest['pieces']):
        resource = p['resource'].split(':')[1]
        t = nbtlib.load(PACK / 'structures' / (resource + '.nbt'))
        palette = [state_text(s) for s in t['palette']]
        for s in palette:
            if s not in ids: ids[s]=len(names); names.append(s)
        offset = np.array(p['offset'])
        coords = np.array([b['pos'].unpack() for b in t['blocks']], dtype=np.int32) + offset
        if len(coords):
            x,y,z=coords.T
            states=np.array([ids[palette[int(b['state'])]] for b in t['blocks']],dtype=np.uint16)
            # Runtime skips structure-void, not ordinary air.
            keep=np.array([names[s]!='minecraft:structure_void' for s in states])
            scene[y[keep],z[keep],x[keep]]=states[keep]
        for b in t['blocks']:
            if 'nbt' in b: entities.add(tuple((np.array(b['pos'].unpack())+offset).tolist()))
        if i%50==0: print('scene',i,'/',len(manifest['pieces']),flush=True)
    return scene,names,ids,entities,manifest

def terrain_heights(scene,names):
    natural = np.array([s.split('[')[0].split(':')[-1] in NATURAL and s.startswith('minecraft:') for s in names])
    mask=natural[scene]
    # Template rocks are included conservatively; trees and buildings are not terrain.
    return np.where(mask.any(axis=0),scene.shape[0]-1-np.argmax(mask[::-1],axis=0),-1)

def preview(scene,names,path):
    colors=[]
    for s in names:
        s=s.split('[')[0]
        if s=='minecraft:air': c=(31,43,61)
        elif any(v in s for v in ('leaves','grass','moss','fern')): c=(77,117,65)
        elif any(v in s for v in ('water','ice')): c=(58,112,143)
        elif any(v in s for v in ('wood','log','planks','fence')): c=(124,87,54)
        elif any(v in s for v in ('brick','terracotta','roof')): c=(164,109,85)
        else: c=(158,155,140)
        colors.append(c)
    occupied=scene!=0
    top=scene.shape[0]-1-np.argmax(occupied[::-1],axis=0)
    z,x=np.indices(top.shape)
    rgb=np.asarray(colors,dtype=np.uint8)[scene[top,z,x]]
    dz,dx=np.gradient(top.astype(float))
    light=np.clip(.88+(-dx*.45-dz*.55)/np.sqrt(1+dx*dx+dz*dz),.35,1.25)
    rgb=np.clip(rgb.astype(float)*light[:,:,None],0,255).astype(np.uint8)
    path.parent.mkdir(parents=True,exist_ok=True)
    Image.fromarray(rgb).resize((1040,1040),Image.Resampling.NEAREST).save(path)

def main():
    p=argparse.ArgumentParser();p.add_argument('--inspect',action='store_true');p.add_argument('--build',action='store_true');p.add_argument('--audit-save',type=Path);p.add_argument('--verify-complete',action='store_true');a=p.parse_args()
    if a.audit_save:audit_save(a.audit_save,a.verify_complete);return
    out=ROOT/'work/sky-landscape'
    if a.build:
        cache=np.load(out/'baseline.npz');scene=cache['scene'];names=cache['names'].tolist()
        before,after,names,guards,changed=build_revision(scene,names,cache['entities'])
        target=ROOT/'src/main/resources/data/tnc/sky_island/landscape_v1.nbt'
        report=export_patch(before,after,names,guards,target)
        preview(before,names,out/'before.png');preview(after,names,out/'after.png')
        np.savez_compressed(out/'revision.npz',scene=after,names=np.array(names))
        (out/'report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
        print(json.dumps(report),flush=True);return
    scene,names,ids,entities,m=load_scene()
    heights=terrain_heights(scene,names)
    print('terrain perimeter quantiles', {edge:np.percentile(v,[0,25,50,75,100]).tolist() for edge,v in
          [('west',heights[92:429,120]),('east',heights[92:429,400]),('north',heights[92,120:401]),('south',heights[428,120:401])]},flush=True)
    out.mkdir(parents=True,exist_ok=True)
    np.savez_compressed(out/'baseline.npz',scene=scene,names=np.array(names),entities=np.array(sorted(entities)))
    preview(scene,names,out/'before.png')
    print('cached',out,flush=True)

if __name__=='__main__':main()
