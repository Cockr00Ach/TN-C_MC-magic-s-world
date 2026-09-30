"""Full-scale landmark export. Original worlds/blueprints are read-only.

16-cube templates are assembled by the resumable landmark runtime, not vanilla's
limited structure-reference radius. Source commands, mobs and inventories are omitted.
"""
import argparse
import hashlib
import json
from collections import deque
from pathlib import Path
import zipfile

import numpy as np
from PIL import Image, ImageDraw
from building_assets import AIR, read_blueprint, state_text
from import_buildings import compatible, dilate, runs, write_json
from export_stonecrest_fortress import WorldReader, replacement_for, is_artificial
from export_town_structures import write_piece
from survey_world_buildings import section_array


def text_state(s):
    return s.name+('['+','.join(f'{k}={v}' for k,v in s.properties)+']' if s.properties else '')


def fill_holes(mask):
    outside=np.zeros_like(mask); queue=deque()
    h,w=mask.shape
    for z in range(h):
        for x in (0,w-1):
            if not mask[z,x] and not outside[z,x]: outside[z,x]=True; queue.append((x,z))
    for x in range(w):
        for z in (0,h-1):
            if not mask[z,x] and not outside[z,x]: outside[z,x]=True; queue.append((x,z))
    while queue:
        x,z=queue.popleft()
        for nx,nz in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)):
            if 0<=nx<w and 0<=nz<h and not mask[nz,nx] and not outside[nz,nx]:
                outside[nz,nx]=True; queue.append((nx,nz))
    return ~outside


def landscape_mask(bounds, polygon):
    x0, _, z0, x1, _, z1 = bounds
    if len(polygon) < 3 or any(not (x0 <= x <= x1 and z0 <= z <= z1) for x, z in polygon):
        raise ValueError('Landscape polygon must fit the source bounds')
    image = Image.new('1', (x1-x0+1, z1-z0+1))
    ImageDraw.Draw(image).polygon([(x-x0, z-z0) for x, z in polygon], fill=1)
    return np.asarray(image, dtype=bool)


def ground_profile(blocks, palette, fallback):
    # Roofs, leaves and decorative granite are not a reliable ground-height signal.
    names = [s.split('[')[0].split(':')[-1] for s in palette]
    soil = np.array([n in {'grass_block', 'dirt', 'coarse_dirt', 'podzol', 'mycelium',
                           'moss_block', 'sand', 'red_sand', 'mud', 'rooted_dirt'}
                     or n.endswith(('_grass_block', '_dirt', '_podzol')) for n in names])
    result = np.full(blocks.shape[1:], fallback, dtype=np.int16)
    for y in range(blocks.shape[0]):
        result[soil[blocks[y]]] = y
    return result


def height_runs(mask, heights):
    result = []
    for row, values in zip(mask, heights):
        runs_in_row = []
        for x in np.flatnonzero(row):
            x, h = int(x), int(values[x])
            if runs_in_row and runs_in_row[-1][1]+1 == x and runs_in_row[-1][2] == h:
                runs_in_row[-1][1] = x
            else:
                runs_in_row.append([x, x, h])
        result.append(runs_in_row)
    return result


def read_world(world,bounds):
    x0,y0,z0,x1,y1,z1=bounds
    blocks=np.zeros((y1-y0+1,z1-z0+1,x1-x0+1),dtype=np.uint16)
    palette=['minecraft:air']; ids={palette[0]:0}; artificial=[False]; remaps={}
    reader=WorldReader(world); source_files={}
    for cz in range(z0//16,z1//16+1):
        for cx in range(x0//16,x1//16+1):
            chunk=reader.chunk(cx,cz)
            if chunk is None: continue
            region=world/'region'/f'r.{cx//32}.{cz//32}.mca'
            if region.name not in source_files: source_files[region.name]=hashlib.sha256(region.read_bytes()).hexdigest()
            for sy in sorted(chunk.sections):
                if sy*16>y1 or sy*16+15<y0: continue
                arr,pal=section_array(chunk,sy)
                if arr is None: continue
                mapping=[]
                for s in pal:
                    t=compatible(text_state(replacement_for(s)))
                    if t not in ids:
                        ids[t]=len(palette); palette.append(t); artificial.append(is_artificial(s))
                    mapping.append(ids[t])
                    if text_state(s)!=t: remaps[text_state(s)]=t
                ax,bx=max(x0,cx*16),min(x1+1,cx*16+16)
                ay,by=max(y0,sy*16),min(y1+1,sy*16+16)
                az,bz=max(z0,cz*16),min(z1+1,cz*16+16)
                blocks[ay-y0:by-y0,az-z0:bz-z0,ax-x0:bx-x0]=np.asarray(mapping,dtype=np.uint16)[arr[ay-sy*16:by-sy*16,az-cz*16:bz-cz*16,ax-cx*16:bx-cx*16]]
            reader.chunks.pop((cx,cz),None)
        print(f'world rows {cz-z0//16+1}/{z1//16-z0//16+1}',flush=True)
    return blocks,palette,np.asarray(artificial),dict(source_world=world.name,source_bounds=bounds,source_regions_sha256=source_files,palette_replacements=remaps)


def export(args):
    landscape = None
    if args.world:
        blocks,palette,artificial,provenance=read_world(args.source,args.bounds)
        # Roofs/walls, not a flat imported lawn, define the footprint. Preserve enclosed courtyards.
        ground=args.ground_y-args.bounds[1]
        if args.landscape_profile:
            landscape = json.loads(args.landscape_profile.read_text(encoding='utf8'))
            if landscape['bounds'] != args.bounds:
                raise ValueError('Profile bounds disagree with command line')
            mask = landscape_mask(args.bounds, landscape['polygon'])
            provenance['landscape_polygon'] = landscape['polygon']
            provenance['selection'] = landscape['selection']
        else:
            mask=fill_holes(dilate(np.any(artificial[blocks[max(0,ground+3):]],axis=0),2))
        if args.exclude:
            for x0,x1,z0,z1 in args.exclude:
                mask[max(0,z0-args.bounds[2]):z1-args.bounds[2]+1,max(0,x0-args.bounds[0]):x1-args.bounds[0]+1]=False
    else:
        blocks,original,version,_=read_blueprint(args.source)
        palette=[compatible(s) for s in original]
        solid=np.asarray([s.split('[')[0] not in AIR for s in palette])[blocks]
        ys,zs,xs=np.where(solid)
        bounds=[int(xs.min()),int(ys.min()),int(zs.min()),int(xs.max()),int(ys.max()),int(zs.max())]
        x0,y0,z0,x1,y1,z1=bounds
        blocks=blocks[y0:y1+1,z0:z1+1,x0:x1+1]
        mask=np.any(solid[y0:y1+1,z0:z1+1,x0:x1+1],axis=0)
        ground=0
        provenance=dict(source_file=args.source.name,source_sha256=hashlib.sha256(args.source.read_bytes()).hexdigest(),
                        source_bounds=bounds,source_data_version=version,palette_replacements={a:b for a,b in zip(original,palette) if a!=b})
    known=set(AIR)
    for jarpath in args.registry_jar:
        with zipfile.ZipFile(jarpath) as jar:
            for name in jar.namelist():
                parts=name.split('/')
                if len(parts)==4 and parts[0]=='assets' and parts[2]=='blockstates' and parts[3].endswith('.json'):
                    known.add(parts[1]+':'+parts[3][:-5])
    used=np.unique(blocks[:,mask]); unknown={palette[int(i)].split('[')[0] for i in used}-known
    if unknown: raise ValueError(f'Unmapped blocks: {sorted(unknown)}')
    margin=0 if args.floating else args.blend
    h,d,w=blocks.shape; dims=[w+margin*2,h,d+margin*2]
    if max(dims[0],dims[2])>2048 or h>384: raise ValueError(f'Oversized landmark {dims}')
    building=np.pad(mask,margin); terrain=building if args.floating else dilate(building,margin)
    solidflags=np.asarray([s.split('[')[0] not in AIR for s in palette])
    expected=int((solidflags[blocks]&mask[None]).sum()); pieces=[]
    for z in range(0,d,16):
        for y in range(0,h,16):
            for x in range(0,w,16):
                tile=blocks[y:y+16,z:z+16,x:x+16]
                occupied=solidflags[tile]&mask[None,z:z+16,x:x+16]
                records=[(int(lx),int(ly),int(lz),palette[int(tile[ly,lz,lx])],None) for ly,lz,lx in np.argwhere(occupied)]
                if not records: continue
                name=f'p_{x}_{y}_{z}'; size=[tile.shape[2],tile.shape[0],tile.shape[1]]
                n,_=write_piece(args.output/'structures/landmarks'/args.asset/(name+'.nbt'),tuple(size),records,3465)
                pieces.append(dict(resource=f'tnc:landmarks/{args.asset}/{name}',offset=[x+margin,y,z+margin],size=size,blocks=n))
        print(f'{args.asset}: exported z={z}/{d}, {len(pieces)} pieces',flush=True)
    assert sum(p['blocks'] for p in pieces)==expected
    manifest=dict(dimensions=dims,anchor_local=[dims[0]//2,ground,dims[2]-1],floating=args.floating,sunken=args.sunken,blend_distance=args.blend,
                  mask_rows=runs(building),terrain_mask_rows=runs(terrain),pieces=pieces,piece_count=len(pieces),block_count=expected,
                  **provenance,policy='No source entities, commands, inventories or block-entity payloads; full-size architecture')
    if landscape:
        reference = landscape['placement_reference_y'] - args.bounds[1]
        if not 0 <= reference < h:
            raise ValueError('Landscape placement reference must fit source height')
        manifest['anchor_local'][1] = reference
        manifest['placement_reference_y'] = landscape['placement_reference_y']
        heights = np.pad(ground_profile(blocks, palette, ground), margin, constant_values=ground)
        manifest['ground_height_rows'] = height_runs(building, heights)
        manifest['terrain_profile_version'] = 1
        manifest['revision'] = 'full-axis-20261001'
    write_json(args.output/'buildings'/(args.asset+'.json'),manifest)
    structure_id=args.structure_id or args.asset
    write_json(args.output/'worldgen/structure'/(structure_id+'.json'),dict(type='tnc:large_landmark',asset=args.asset,
               biomes='#minecraft:is_overworld' if args.floating else '#tnc:has_structure/imported_buildings',step='surface_structures',spawn_overrides={},terrain_adaptation='none'))
    salt = landscape['placement_salt'] if landscape else int(hashlib.sha256(args.asset.encode()).hexdigest()[:7],16)
    write_json(args.output/'worldgen/structure_set'/(structure_id+'.json'),dict(structures=[dict(structure='tnc:'+structure_id,weight=1)],
               placement=dict(type='minecraft:random_spread',locate_offset=[8,0,8],spacing=160,separation=100,salt=salt)))
    preview=Path('work/landmark-previews'); preview.mkdir(parents=True,exist_ok=True)
    Image.fromarray(building.astype(np.uint8)*255).save(preview/(args.asset+'-mask.png'))
    print(f'DONE {args.asset}: {dims}, {expected} blocks, {len(pieces)} pieces',flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('source',type=Path); p.add_argument('asset')
    p.add_argument('--output',type=Path,default=Path('src/main/resources/data/tnc'))
    p.add_argument('--world',action='store_true'); p.add_argument('--bounds',type=int,nargs=6)
    p.add_argument('--ground-y',type=int); p.add_argument('--exclude',type=int,nargs=4,action='append')
    p.add_argument('--floating',action='store_true'); p.add_argument('--registry-jar',type=Path,action='append',required=True)
    p.add_argument('--sunken',action='store_true'); p.add_argument('--blend',type=int,default=24)
    p.add_argument('--structure-id')
    p.add_argument('--landscape-profile', type=Path)
    a=p.parse_args()
    if a.world and (a.bounds is None or a.ground_y is None): p.error('world requires bounds and ground-y')
    export(a)
