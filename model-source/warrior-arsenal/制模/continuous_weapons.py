"""Bake stepped Java item bodies into bevelled Forge OBJ surfaces."""
from collections import defaultdict
import io
import json
import math
from pathlib import Path
import re
from zipfile import ZipFile, ZIP_DEFLATED

import numpy as np
from PIL import Image
from scipy.ndimage import distance_transform_edt, gaussian_filter, gaussian_filter1d
from scipy.spatial.transform import Rotation
import shapely
from shapely.geometry import Polygon, box
from shapely.geometry.polygon import orient

from roster import ROOT, RES, all_ids, tex_ref

BASE = ROOT / 'TN-C-战士兵器表面精修.zip'
OUTPUT = ROOT / 'TN-C-战士兵器连续曲面.zip'
PREFIX = 'assets/tnc/'
MESH_PATH = 'models/item/weapon_surfaces/'
TEXTURE_PATH = 'item/weapon_surfaces/'
TEXELS_PER_UNIT = 16
FACE_CORNERS = {
    'down': (5,4,0,1), 'up': (2,3,7,6), 'west': (0,4,7,3),
    'north': (1,0,3,2), 'east': (5,1,2,6), 'south': (4,5,6,7),
}
CORNERS = np.array(((0,0,0),(1,0,0),(1,1,0),(0,1,0),(0,0,1),(1,0,1),(1,1,1),(0,1,1)))
BRONZE = {'bronze_dao','bronze_dagger','bronze_spear','bronze_greatsword'}
ROSTER = {key: group for group,key in all_ids()}
BODY = {'Blade','Edge','Tempered steel','Ground edges','Forged hammer cheek','Striking bevel',
        'honed tip'}
SHAPED = ('limb','laminate','siyah','feather','vane','forged head','crescent wing','silver hook',
          'transverse ge','iron heel','tapered haft','turned white-wax wood','tapered blade socket')


class Mesh:
    def __init__(self):
        self.faces = []

    def add(self, xyz, uv, material, expected=None):
        xyz, uv = np.asarray(xyz), np.asarray(uv)
        normal = np.cross(xyz[1]-xyz[0], xyz[2]-xyz[0])
        length = np.linalg.norm(normal)
        if length < 1e-9:
            return
        normal /= length
        if expected is not None and np.dot(normal, expected) < 0:
            xyz, uv, normal = xyz[::-1], uv[::-1], -normal
        self.faces.append((xyz,uv,normal,material))

    def encode(self, name):
        data = ['mtllib ' + name + '.mtl', 'o ' + name]
        positions, texcoords, normals, materials = {}, {}, {}, {}
        previous = None

        def index(store, values, kind):
            key = tuple(round(float(n),6) for n in values)
            if key not in store:
                store[key] = len(store)+1
                data.append(kind+' '+' '.join(map(str,key)))
            return store[key]

        for xyz, uv, normal, material in self.faces:
            if material != previous:
                tag = materials.setdefault(material,'m'+str(len(materials)))
                data.append('usemtl ' + tag)
                previous = material
            n = index(normals,normal,'vn')
            refs = [f'{index(positions,p/16,"v")}/{index(texcoords,t,"vt")}/{n}' for p,t in zip(xyz,uv)]
            data.append('f '+' '.join(refs))
        mtl = ''.join(f'newmtl {alias}\nKa 0 0 0\nKd 1 1 1\nmap_Kd {loc}\n\n' for loc,alias in materials.items())
        return ('\n'.join(data)+'\n').encode('ascii'), mtl.encode('ascii')


def box_faces(mesh, element, textures):
    lo, hi = np.array(element['from']), np.array(element['to'])
    vertices = lo + CORNERS*(hi-lo)
    center = (lo+hi)/2
    rotation = element.get('rotation',{})
    if rotation.get('angle'):
        origin = np.array(rotation['origin'])
        axis, angle = rotation['axis'], rotation['angle']
        coords = np.vstack([vertices,center])-origin
        if rotation.get('rescale'):
            coords[:,[i for i in range(3) if i!='xyz'.index(axis)]] /= math.cos(math.radians(angle))
        coords = Rotation.from_euler(axis,angle,degrees=True).apply(coords)+origin
        vertices,center = coords[:-1],coords[-1]
    for side, face in element['faces'].items():
        a,b,c,d = np.array(face['uv'])/16
        if side == 'up':
            uv = [(c,b),(a,b),(a,d),(c,d)]
        elif side == 'down':
            uv = [(c,b),(a,b),(a,d),(c,d)]
        else:
            uv = [(a,d),(c,d),(c,b),(a,b)]
        quad = vertices[list(FACE_CORNERS[side])]
        mesh.add(quad,np.roll(uv,-face.get('rotation',0)//90,axis=0),
                 tex_ref(face['texture'],textures),quad.mean(axis=0)-center)


def plane(key, family):
    if family == 'longbows' or key in BRONZE or key in ('greatsword','hammer'):
        return 2, 'south', 'north'
    return 1, 'up', 'down'


def groups(elements, key, family, depth, length):
    buckets = defaultdict(list)
    none = []
    if family == 'longbows' or (family == 'spears' and key not in BRONZE):
        for e in elements:
            name = e.get('name','')
            if 'rotation' not in e and (any(s in name for s in SHAPED) or 'bowstring' in name):
                buckets['bowstring' if 'bowstring' in name else 'body'].append(e)
            else:
                none.append(e)
        return buckets,none

    heads = [e for e in elements if e.get('name') in BODY or e.get('name','').startswith('forged bevel')]
    if key in ('yue_bronze_jian','cloudridge_jian'):
        blade_start = min(e['from'][length] for e in heads if e.get('name')=='Blade')
        heads = [e for e in heads if e['to'][length] >= blade_start-.20]
    head_start = min((e['from'][length] for e in heads), default=100.)
    for e in elements:
        name = e.get('name','')
        if 'rotation' in e:
            none.append(e)
        elif e in heads:
            buckets['body'].append(e)
        elif (name in ('Inlay','Fitting') and depth == 1 and
                e['from'][length] > head_start+.9 and
                min(e['to'][i]-e['from'][i] for i in (0,length)) < .34):
            # Old facades stamped relief a cell at a time. Bake it into the skin of the casting.
            buckets['body'].append(e)
        elif name == 'Haft':
            buckets['haft'].append(e)
        else:
            none.append(e)
    return buckets,none


def painted_surface(elements, textures, lookup, axes, depth, side, positive, origin, size):
    width,height = size
    image = np.zeros((height,width,3),dtype=np.float32)
    heights = np.full((height,width),-np.inf,dtype=np.float32)
    density = TEXELS_PER_UNIT
    for e in elements:
        face = e['faces'].get(side)
        if face is None:
            continue
        lo,hi = np.array(e['from']),np.array(e['to'])
        low = (lo[list(axes)]-origin)*density
        high = (hi[list(axes)]-origin)*density
        left,bottom = np.maximum(np.floor(low).astype(int),0)
        right,top = np.minimum(np.ceil(high).astype(int),size)
        if left>=right or bottom>=top:
            continue
        source = lookup(tex_ref(face['texture'],textures))
        a,b,c,d = np.array(face['uv'])/16
        if depth == 1 and not positive:
            b,d = d,b
        if depth == 2:
            b,d = d,b
            if not positive:
                a,c = c,a
        s,t = source.shape[1],source.shape[0]
        u = ((a+(c-a)*np.clip((np.arange(left,right)+.5-low[0])/(high[0]-low[0]),0,1))*s).astype(int)
        v = ((b+(d-b)*np.clip((np.arange(bottom,top)+.5-low[1])/(high[1]-low[1]),0,1))*t).astype(int)
        samples = source[np.clip(v,0,t-1)[:,None],np.clip(u,0,s-1)[None,:]]
        level = hi[depth] if positive else -lo[depth]
        area = np.s_[bottom:top,left:right]
        visible = (samples[:,:,3]>0) & (heights[area] <= level)
        image[area][visible] = samples[:,:,:3][visible]
        heights[area][visible] = level
    exists = np.isfinite(heights)
    if not np.any(exists):
        raise ValueError(f'No painted surface on {side}')
    yi,xi = distance_transform_edt(~exists,return_distances=False,return_indices=True)
    image = image[yi,xi]
    gradient = gaussian_filter(image,(1.75,1.15,0))
    # Keep the light bands, but soften the boundaries of colour stamped into the casting.
    return np.uint8(np.clip(.38*image+.62*gradient,0,255))


def polygons(geometry):
    return [geometry] if geometry.geom_type == 'Polygon' else [
        p for p in shapely.get_parts(geometry) if p.geom_type == 'Polygon'
    ]


def straight_blade(elements, length):
    steel = [e for e in elements if e.get('name') in ('Blade','Edge')]
    low = min(e['from'][length] for e in steel)
    high = max(e['to'][length] for e in steel)
    stops = np.linspace(low,high,92)
    profile = []
    for y in stops:
        row = [e for e in steel if e['from'][length]-.07 <= y <= e['to'][length]+.07]
        if row:
            profile.append((y,min(e['from'][0] for e in row),max(e['to'][0] for e in row)))
    ys, left, right = np.array(profile).T
    left = gaussian_filter1d(left,5,mode='nearest')
    right = gaussian_filter1d(right,5,mode='nearest')
    # Keep the real tip, while allowing the last section to taper into it.
    tip_pieces = [e for e in steel if e['to'][length]>=high-.07]
    tip = (min(e['from'][0] for e in tip_pieces)+max(e['to'][0] for e in tip_pieces))*.5
    fade = np.clip((ys-(high-.68))/.68,0,1)
    left = left*(1-fade)+(tip-.024)*fade
    right = right*(1-fade)+(tip+.024)*fade
    return Polygon(np.vstack([np.column_stack([left,ys]),np.column_stack([right,ys])[::-1]])).simplify(.05)


def continuous(mesh, elements, textures, lookup, axes, depth, front, back, location, name, item):
    a,b = axes
    footprint = shapely.union_all([box(e['from'][a],e['from'][b],e['to'][a],e['to'][b]) for e in elements],
                                  grid_size=.0001)
    if footprint.is_empty:
        return
    smoothing = .22 if name=='body' else .13
    if name=='bowstring':
        smoothing=.075
    # Closing fills tiny staircase notches. Opening rounds the opposing corners.
    rounded = footprint.buffer(smoothing,quad_segs=3).buffer(-smoothing,quad_segs=3)
    opened = rounded.buffer(-smoothing*.58,quad_segs=3).buffer(smoothing*.58,quad_segs=3)
    if opened.is_empty or abs(opened.area-footprint.area)>footprint.area*.14:
        opened = rounded
    if opened.is_empty:
        opened = footprint
    opened = opened.simplify(.044 if name!='bowstring' else .023,preserve_topology=True)
    if name=='body' and item in ('yue_bronze_jian','cloudridge_jian'):
        blade = straight_blade(elements,b)
        if blade.is_valid and blade.area > footprint.area*.8:
            opened = blade
    origin = np.array(footprint.bounds[:2])-.22
    dimensions = np.array(footprint.bounds[2:])-origin+.22
    width,height = [int(math.ceil(x*TEXELS_PER_UNIT)) for x in dimensions]
    # Power-of-two texture dimensions allow Forge to keep its normal mipmap levels.
    tw,th = (1 << (width-1).bit_length()),(1 << (2*height-1).bit_length())
    positive = painted_surface(elements,textures,lookup,axes,depth,front,True,origin,(width,height))
    negative = painted_surface(elements,textures,lookup,axes,depth,back,False,origin,(width,height))
    padding_top = (th//2)-height
    raster = np.concatenate([np.pad(img,((0,padding_top),(0,tw-width),(0,0)),mode='edge')
                             for img in (positive,negative)])
    low,high = np.array([(e['from'][depth],e['to'][depth]) for e in elements]).T
    center = float(np.median((low+high)/2))
    half = max(.035,float(np.percentile((high-low)/2,80)))

    def uv(xy, sign):
        return ((xy-origin)*TEXELS_PER_UNIT+np.array([0,0 if sign>0 else th/2])) / np.array([tw,th])

    def coords(xy, section):
        out = np.zeros((len(xy),3))
        out[:,a],out[:,b],out[:,depth] = xy[:,0],xy[:,1],section
        return out

    before = len(mesh.faces)
    for poly in polygons(opened):
        if poly.area < .013:
            continue
        poly = orient(poly,sign=1)
        bevel = min(.26,max(.068,half*.66))
        inner = poly.buffer(-bevel,quad_segs=2).simplify(.035,preserve_topology=True)
        if inner.is_empty:
            bevel *= .40
            inner = poly.buffer(-bevel,quad_segs=2).simplify(.025,preserve_topology=True)
        lip = half * (.43 if name=='body' else .64)
        for sign in (1,-1):
            expected = np.eye(3)[depth]*sign
            if not inner.is_empty:
                for tri in shapely.get_parts(shapely.constrained_delaunay_triangles(inner)):
                    xy = np.asarray(tri.exterior.coords)[:3]
                    mesh.add(coords(xy,center+sign*half),uv(xy,sign),location,expected)
            ring = poly if inner.is_empty else poly.difference(inner)
            for tri in shapely.get_parts(shapely.constrained_delaunay_triangles(ring)):
                xy = np.asarray(tri.exterior.coords)[:3]
                dist = shapely.distance(shapely.points(xy),poly.boundary)
                local_half = lip+(half-lip)*np.clip(dist/max(.015,bevel),0,1)
                if not inner.is_empty:
                    on_inner = shapely.distance(shapely.points(xy),inner.boundary) < 1e-6
                    local_half[on_inner] = half
                mesh.add(coords(xy,center+sign*local_half),uv(xy,sign),location,expected)
        for boundary in (poly.exterior,*poly.interiors):
            xy = np.asarray(boundary.coords)
            for p,q in zip(xy[:-1],xy[1:]):
                start,end = np.array([p,q]),np.array([p,q])
                shape = np.vstack([coords(start,center+lip),coords(end[::-1],center-lip)])
                normal2 = np.array([q[1]-p[1],p[0]-q[0]])
                expected = coords(normal2[None,:],0)[0]
                mesh.add(shape,np.vstack([uv(start,1),uv(end[::-1],-1)]),location,expected)
    if len(mesh.faces)==before:
        return
    stream = io.BytesIO()
    Image.fromarray(raster).save(stream,'PNG',optimize=True)
    return stream.getvalue()


def generate():
    with ZipFile(BASE) as base, ZipFile(OUTPUT.with_suffix('.tmp'),'w',ZIP_DEFLATED,compresslevel=6) as out:
        names = set(base.namelist())
        cache = {}

        def lookup(ref):
            if ref not in cache:
                namespace,location = ref.split(':',1)
                path = f'assets/{namespace}/textures/{location}.png'
                data = base.read(path) if path in names else (RES/path).read_bytes()
                cache[ref] = np.asarray(Image.open(io.BytesIO(data)).convert('RGBA'))
            return cache[ref]

        results = {}
        for name in base.namelist():
            data = base.read(name)
            if not name.startswith(PREFIX+'models/item/') or not name.endswith('.json'):
                out.writestr(name,data)
                continue
            stem = Path(name).stem
            item = re.sub(r'_pulling_[0-2]$','',stem)
            if item not in ROSTER or item=='bronze_halberd' or '/warrior_ghost/' in name:
                out.writestr(name,data)
                continue
            doc = json.loads(data)
            if not doc.get('elements'):
                out.writestr(name,data)
                continue
            depth, front, back = plane(item,ROSTER[item])
            axes = (0,3-depth)
            selected, remaining = groups(doc['elements'],item,ROSTER[item],depth,axes[1])
            mesh = Mesh()
            for group, elements in selected.items():
                leaf = stem+'_'+group
                surface = continuous(mesh,elements,doc['textures'],lookup,axes,depth,front,back,
                                     'tnc:'+TEXTURE_PATH+leaf,group,item)
                if surface:
                    out.writestr(PREFIX+'textures/'+TEXTURE_PATH+leaf+'.png',surface)
                else:
                    remaining.extend(elements)
            if not mesh.faces:
                out.writestr(name,data)
                continue
            for element in remaining:
                box_faces(mesh,element,doc['textures'])
            obj,mtl = mesh.encode(stem)
            out.writestr(PREFIX+MESH_PATH+stem+'.obj',obj)
            out.writestr(PREFIX+MESH_PATH+stem+'.mtl',mtl)
            results[stem] = (len(doc['elements']),len(mesh.faces),len(remaining))
            # Forge renders the OBJ. The warrior trail reads the legacy endpoints from this JSON.
            doc['loader'] = 'forge:obj'
            doc['model'] = 'tnc:'+MESH_PATH+stem+'.obj'
            doc['automatic_culling'] = False
            doc['emissive_ambient'] = False
            doc['textures'].setdefault('particle',next(iter(doc['textures'].values())))
            out.writestr(name,json.dumps(doc,separators=(',',':'),ensure_ascii=False))
    assert len(results)==59, results.keys()
    with ZipFile(OUTPUT.with_suffix('.tmp')) as z:
        assert z.testzip() is None
    OUTPUT.with_suffix('.tmp').replace(OUTPUT)
    for stem,result in results.items():
        print(stem,*result)


if __name__ == '__main__':
    generate()
