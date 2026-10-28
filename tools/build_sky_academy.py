"""Author the approved sky landscape offline. Never reads/writes a live world.

Coordinates are island-local; architecture, roads, portal and tavern are immutable.
The approved natural fringe of the imported town is reshaped across its old cut.
The output is a compact, chunk-bounded overlay, applied only to newly built islands.
"""
from pathlib import Path
import gzip, io, json, math, random
import numpy as np
import nbtlib
from scipy.ndimage import gaussian_filter, distance_transform_edt, binary_dilation
from sky_landscape import ROOT, terrain_heights, preview
from export_town_structures import state_tag

OUT=ROOT/'work/sky-academy'
DATA=ROOT/'src/main/resources/data/tnc/sky_academy'
RNG=random.Random(261026)
LANDS=[('sky_west_meadow','51','风铃草甸','FIELD',6800,62,240,32,91),
       ('sky_south_garden','52','花坡庭地','FIELD',7200,142,432,32,87),
       ('sky_cloud_pasture','53','云湾大牧场','PASTURE',14500,334,432,48,91)]

class Garden:
    def __init__(self):
        RNG.seed(261026)
        cache=np.load(ROOT/'work/sky-landscape/revision.npz')
        self.scene=cache['scene'].copy(); self.before=self.scene.copy()
        self.names=cache['names'].tolist();self.ids={s:i for i,s in enumerate(self.names)}
        self.height=terrain_heights(self.scene,self.names)
        self.original_height=self.height.copy();self.landmarks={};self.routes=[]
        self.protected=np.zeros((520,520),dtype=bool)
        self.protected[92:429,120:401]=True
        self.protected[254:344,402:503]=True # complete tavern incl basement, approach and support
        self.protected[400:449,185:234]=True # portal reservation
        for p in json.loads((ROOT/'src/main/resources/data/tnc/housing/catalog.json').read_text('utf-8'))['plots']:
            if p['id'].startswith('sky_'):continue
            x,y,z=p['min'];xx,yy,zz=p['max'];self.protected[max(0,z-3):zz+4,max(0,x-3):xx+4]=True
        self.prepare_fringe()

    def prepare_fringe(self):
        """Open only verified natural columns in an organic band across the cut.

        Unknown/modded blocks, walls, farm beds and even old paths remain protected.
        Logs are removable only away from any manufactured block; this preserves
        timber architecture instead of assuming that every log belongs to a tree.
        """
        natural={'air','cave_air','void_air','grass_block','dirt','coarse_dirt',
                 'rooted_dirt','stone','andesite','diorite','granite','tuff',
                 'deepslate','sand','gravel','clay','moss_block','podzol',
                 'grass','tall_grass','fern','large_fern','vine','glow_lichen',
                 'dandelion','poppy','azure_bluet','oxeye_daisy','blue_orchid',
                 'allium','sunflower','rose_bush','peony','lilac','dead_bush',
                 'brown_mushroom','red_mushroom','dripstone_block','pointed_dripstone',
                 'infested_stone','seagrass','tall_seagrass'}
        bases=[n.split('[')[0].split(':')[-1] for n in self.names]
        safe=np.array([n.startswith('minecraft:') and (b in natural or
                      b.endswith(('_leaves','_ore','_tulip')) or
                      b in {w+'_log' for w in ('oak','dark_oak','spruce','birch','jungle','acacia')})
                      for n,b in zip(self.names,bases)])
        # Includes underground construction and block entities, not just surface roofs.
        built=np.any(~safe[self.before],axis=0)
        built=binary_dilation(built,iterations=5)
        self.architecture=built
        z,x=np.indices(self.height.shape)
        inside=(x>=120)&(x<=400)&(z>=92)&(z<=428)
        inner=np.minimum.reduce([x-120,400-x,z-92,428-z])
        outer=np.hypot(np.maximum(120-x,np.maximum(x-400,0)),np.maximum(92-z,np.maximum(z-428,0)))
        signed=np.where(inside,-inner,outer)
        # The inner treeline and outer clearing do not follow the four straight edges.
        width=32+8*np.sin(x*.051+z*.027)+6*np.cos(z*.079-x*.019)
        band=(signed>-width)&(signed<width+18)&(self.original_height>=65)
        reserved=np.zeros_like(built)
        reserved[254:344,402:503]=True;reserved[400:449,185:234]=True
        for p in json.loads((ROOT/'src/main/resources/data/tnc/housing/catalog.json').read_text('utf-8'))['plots']:
            if p['id'].startswith('sky_'):continue
            a,_,b=p['min'];c,_,d=p['max'];reserved[max(0,b-3):d+4,max(0,a-3):c+4]=True
        self.fringe=band&~built&~reserved
        self.protected[self.fringe]=False
        self.protected|=built|reserved
        self.fringe_weight=np.maximum(0,1-np.abs(signed)/(width+np.where(inside,0,18)))
        self.fringe_weight*=np.minimum(1,distance_transform_edt(~(built|reserved))/9)

    def soften_fringe(self):
        # Smooth across BOTH sides. Outward-only extrusion retains the square plateau.
        smooth=gaussian_filter(self.original_height.astype(float),sigma=11,mode='nearest')
        zgrid,xgrid=np.indices(self.height.shape)
        # Broad, very shallow undulation makes clearings read as meadows, not a moat.
        target=smooth+1.3*np.sin(xgrid*.048+zgrid*.021)*np.cos(zgrid*.044)
        changed_columns=0
        for z,x in zip(*np.where(self.fringe)):
            old=int(self.height[z,x]);w=float(self.fringe_weight[z,x])
            if w<.08:continue
            new=round(old+(target[z,x]-old)*min(1,w*1.6))
            # Natural-only column was checked above, so clearing cannot erase a roof.
            self.box(x,min(old,new)-4,z,x,219,z,'air')
            self.box(x,min(old,new)-4,z,x,new-4,z,'stone')
            self.box(x,new-3,z,x,new-1,z,'dirt');self.put(x,new,z,'grass_block[snowy=false]')
            self.height[z,x]=new;changed_columns+=1
        self.fringe_columns=changed_columns

    def fringe_groves(self):
        # Scattered groves straddle the former cut, with wide, winding meadow windows.
        # No row planting and no tree whose complete crown would be clipped by protection.
        centers=[]
        for _ in range(1800):
            x=RNG.randrange(80,440);z=RNG.randrange(52,466)
            if not self.fringe[z,x] or self.fringe_weight[z,x]<.12:continue
            grove=math.sin(x*.047+z*.023)+math.cos(z*.058-x*.018)
            if grove<.45 or any((x-a)**2+(z-b)**2<150 for a,b in centers):continue
            r=RNG.randint(4,6)
            if self.protected[z-r-5:z+r+6,x-r-5:x+r+6].any():continue
            y=int(self.height[z,x])
            if 'grass_block' not in self.names[self.scene[y,z,x]]:continue
            if np.ptp(self.height[z-3:z+4,x-3:x+4])>7:continue
            if np.any(self.scene[y+1:min(220,y+20),z-3:z+4,x-3:x+4]!=0):continue
            self.tree(x,z,RNG.randint(10,19),r);centers.append((x,z))
        self.fringe_trees=centers

    def sid(self,name):
        if ':' not in name:name='minecraft:'+name
        if name not in self.ids:self.ids[name]=len(self.names);self.names.append(name)
        return self.ids[name]

    def allowed(self,x,z):
        return 2<=x<518 and 2<=z<518 and not self.protected[z,x] and self.original_height[z,x]>=55

    def put(self,x,y,z,name):
        x,y,z=round(x),round(y),round(z)
        if 0<=y<220 and self.allowed(x,z):self.scene[y,z,x]=self.sid(name)

    def box(self,x0,y0,z0,x1,y1,z1,name):
        for z in range(round(z0),round(z1)+1):
            for x in range(round(x0),round(x1)+1):
                if self.allowed(x,z):self.scene[max(0,round(y0)):min(220,round(y1)+1),z,x]=self.sid(name)

    def grade(self,cx,cz,rx,rz,target):
        for z in range(max(2,cz-rz-12),min(518,cz+rz+13)):
            for x in range(max(2,cx-rx-12),min(518,cx+rx+13)):
                if not self.allowed(x,z):continue
                angle=math.atan2(z-cz,x-cx)
                d=math.hypot((x-cx)/rx,(z-cz)/rz)/(1+.045*math.sin(angle*3)+.025*math.sin(angle*7))
                if d>1.25:continue
                blend=max(0,min(1,(1.25-d)/.35));blend=blend*blend*(3-2*blend)
                old=int(self.height[z,x]);new=round(old*(1-blend)+target*blend)
                self.box(x,min(old,new)-4,z,x,219,z,'air')
                self.box(x,min(old,new)-4,z,x,new-4,z,'stone')
                self.box(x,new-3,z,x,new-1,z,'dirt');self.put(x,new,z,'grass_block[snowy=false]')
                self.height[z,x]=new

    def ridge(self,cx,cz,top):
        # A broad convex rock shoulder rather than a flat disc with a ring cliff.
        for z in range(cz-39,cz+40):
            for x in range(cx-59,cx+60):
                if not self.allowed(x,z):continue
                old=int(self.height[z,x]);dx=x-cx;dz=z-cz
                d=math.hypot(dx/59,dz/39)
                if d>=1:continue
                cap=top-.011*dx*dx-.020*dz*dz+1.5*math.sin(dx*.10)*math.sin(dz*.12)
                fade=min(1,max(0,(1-d)/.3));fade=fade*fade*(3-2*fade)
                cap=old+max(0,cap-old)*fade
                if cap<=old:continue
                new=round(cap)
                self.box(x,old,z,x,219,z,'air');self.box(x,old,z,x,new-3,z,'stone')
                self.box(x,new-2,z,x,new-1,z,'dirt');self.put(x,new,z,'grass_block[snowy=false]')
                self.height[z,x]=new

    def path(self,nodes,width=3):
        self.routes.append(nodes)
        cells={}
        for a,b in zip(nodes,nodes[1:]):
            distance=max(abs(b[0]-a[0]),abs(b[2]-a[2]))
            for t in np.linspace(0,1,max(2,distance*2)):
                point=np.array(a)*(1-t)+np.array(b)*t
                x,y,z=np.rint(point).astype(int)
                for dx in range(-width//2+1,width//2+1):
                    for dz in range(-width//2+1,width//2+1):
                        if not self.allowed(x+dx,z+dz):continue
                        q=(x+dx,z+dz);d=(q[0]-point[0])**2+(q[1]-point[2])**2
                        if q not in cells or d<cells[q][0]:cells[q]=(d,y)
        # One final surface per column. Repeatedly painting wide, sloping samples
        # used to lower the preceding step twice and create two-block ledges.
        for (x,z),(_,y) in cells.items():
            old=int(self.height[z,x]);self.box(x,min(old,y)-2,z,x,y-1,z,'stone')
            self.put(x,y,z,'mossy_cobblestone' if (x+z)%13==0 else 'gravel')
            self.box(x,y+1,z,x,max(y+4,old+4),z,'air');self.height[z,x]=y

    def tree(self,x,z,h=15,r=6):
        y=int(self.height[z,x])+1
        if not self.allowed(x,z):return
        # Bent trunk, buttress roots and five offset crowns; no floating trunk cubes.
        for j in range(h):
            dx=round(math.sin(j/h*1.3)*2)
            self.put(x+dx,y+j,z,'oak_log[axis=y]')
            if j<h//3:self.put(x+dx+1,y+j,z,'oak_log[axis=y]')
        for dx,dz in ((-1,0),(1,0),(0,1),(0,-1)):
            for j in range(1,4):self.put(x+dx*j,y+max(0,2-j),z+dz*j,'oak_log[axis=y]')
        for dx,dz,dy,rr in ((0,0,0,r),(4,1,-3,r-1),(-3,2,-2,r-1),(1,-4,-1,r-1),(-1,-1,3,r-2)):
            for xx in range(-rr,rr+1):
                for zz in range(-rr,rr+1):
                    for yy in range(-3,4):
                        if (xx/rr)**2+(zz/rr)**2+(yy/3)**2<1+RNG.random()*.12:
                            px,py,pz=x+2+dx+xx,y+h+dy+yy,z+dz+zz
                            if self.allowed(px,pz) and 0<=py<220 and self.scene[py,pz,px]==0:
                                self.put(px,py,pz,'oak_leaves[distance=1,persistent=true,waterlogged=false]')

    def cottage(self,x,z,y,w=11,d=13):
        self.grade(x,z,w//2+4,d//2+4,y)
        self.box(x-w//2,y,z-d//2,x+w//2,y,z+d//2,'stone_bricks')
        for yy in range(1,7):
            for xx in range(-w//2+1,w//2+1):
                for zz in range(-d//2+1,d//2+1):
                    edge=abs(xx)==w//2 or abs(zz)==d//2
                    state='stripped_spruce_log[axis=y]' if abs(xx)==w//2 and abs(zz)==d//2 else 'calcite'
                    self.put(x+xx,y+yy,z+zz,state if edge else 'air')
        for zz in (-d//2+1,d//2):
            for xx in (-3,3):self.box(x+xx,y+3,z+zz,x+xx+1,y+4,z+zz,'glass')
        self.box(x-1,y+1,z+d//2,x+1,y+3,z+d//2,'air')
        # Steep overhanging roof, ridge and timber fascia.
        for xx in range(-w//2-1,w//2+2):
            roof=y+7+(w//2+1-abs(xx))//2
            self.box(x+xx,roof,z-d//2-1,x+xx,roof,z+d//2+1,'dark_oak_planks')
        self.put(x,y+5,z+d//2+1,'lantern[hanging=true,waterlogged=false]')
        self.box(x-3,y+1,z-3,x-1,y+1,z-3,'spruce_slab[type=bottom,waterlogged=false]')
        self.put(x+3,y+1,z-3,'crafting_table')

    def basin(self,cx,cz,rx,rz,y):
        self.grade(cx,cz,rx+5,rz+5,y)
        for z in range(cz-rz-2,cz+rz+3):
            for x in range(cx-rx-2,cx+rx+3):
                angle=math.atan2(z-cz,x-cx)
                d=(((x-cx)/rx)**2+((z-cz)/rz)**2)/(1+.10*math.sin(angle*3)+.05*math.cos(angle*5))
                if d<1:
                    self.box(x,y-4,z,x,y,z,'stone')
                    depth=1 if d>.8 else 2 if d>.4 else 3
                    self.put(x,y-depth,z,'sand' if d>.65 else 'clay')
                    self.box(x,y-depth+1,z,x,y,z,'water[level=0]')
                elif d<1.16:self.put(x,y,z,'mossy_cobblestone' if (x+z)%4==0 else 'gravel')

    def river(self,nodes,width=4):
        for a,b in zip(nodes,nodes[1:]):
            for t in np.linspace(0,1,max(abs(a[0]-b[0]),abs(a[2]-b[2]))*3+2):
                x,y,z=np.rint(np.array(a)*(1-t)+np.array(b)*t).astype(int)
                self.box(x-width,y-3,z-1,x+width,y+1,z+1,'stone')
                self.box(x-width+1,y,z-1,x+width-1,y,z+1,'water[level=0]')
                self.box(x-width+1,y+1,z-1,x+width-1,y+7,z+1,'air')

    def arch(self,x,z,y):
        for side in (-1,1):
            for h in range(13):
                self.box(x+side*6-1,y+h,z-2,x+side*6+1,y+h,z+2,'mossy_stone_bricks' if h%4==0 else 'stone_bricks')
        for dx in range(-6,7):
            top=round(y+14-abs(dx)*.4)
            self.box(x+dx,top,z-2,x+dx,top+2,z+2,'stone_bricks')

    def build(self):
        self.soften_fringe()
        # Arrival: broad low flowered foreground, curved approach to the preserved portal.
        self.grade(253,462,43,27,86)
        self.arch(245,458,87)
        self.path([(209,89,449),(220,88,455),(245,86,458),(270,86,468)],5)
        self.landmarks['arrival']=[245,87,466]
        # North ridge: stone mountain rather than a tower. Open southern panorama.
        self.ridge(258,53,140)
        for x in range(247,270):
            for z in range(46,65):
                if (x-258)**2+(z-55)**2<115:
                    self.box(x,136,z,x,138,z,'stone_bricks');self.box(x,139,z,x,144,z,'air')
                    self.put(x,138,z,'mossy_stone_bricks' if (x+z)%7==0 else 'stone_bricks');self.height[z,x]=138
        self.tree(235,53,22,10)
        self.path([(258,138,65),(279,132,70),(300,120,78),(322,int(self.height[91,322]),91)],5)
        self.landmarks['stargaze']=[258,139,61]
        # Wind forest: open pockets, natural tall canopy, an arch with three clue stones.
        self.grade(90,180,21,40,106)
        self.arch(88,171,107)
        self.landmarks['wind']=[88,107,190]
        for i,(x,z) in enumerate(((79,180),(88,176),(97,180))):
            self.box(x,106,z,x,108,z,'chiseled_stone_bricks');self.landmarks['rune'+str(i)]=[x,108,z]
        for x,z,h in ((65,155,21),(81,142,17),(105,151,23),(64,192,19),(103,207,15),(72,220,18)):
            self.tree(x,z,h,7)
        self.path([(88,106,191),(91,106,216),(105,110,233),(119,int(self.height[245,119]),245)],3)
        # Meadow farms: stepped terrace beds and three houses around a working court.
        self.grade(87,323,26,44,91)
        for x,z in ((68,299),(98,293),(70,347)):self.cottage(x,z,91)
        self.landmarks['farm']=[93,92,320]
        for z in range(327,339):
            for x in range(88,109):
                self.put(x,91,z,'water[level=0]' if x==98 else 'farmland[moisture=7]')
                if x!=98:self.put(x,92,z,'wheat[age=7]')
        self.path([(119,int(self.height[319,119]),319),(108,95,319),(93,91,319),(70,91,319)],5)
        self.path([(70,91,307),(70,91,340)],3)
        for x,z in ((60,318),(60,333),(108,347),(101,362)):self.tree(x,z,12,5)
        # Mirror lake: a source-fed upper pool and a broad lower lake, with two visible drops.
        self.basin(423,133,13,12,111);self.basin(443,177,22,22,94)
        self.river([(423,111,144),(424,111,150),(428,94,159),(436,94,166)],3)
        self.river([(461,94,181),(477,94,186),(488,83,192),(491,83,197)],3)
        self.landmarks['lake']=[425,95,188]
        # Approach from the public northern ridge. The imported east boundary is a
        # real stone wall: a path aimed through its protected columns left a 5-block gap.
        self.path([(300,120,78),(350,125,70),(420,115,70),(440,111,118),
                   (440,105,140),(473,94,162),(473,94,204),(425,94,204),(425,94,193)],3)
        for x,z in ((410,158),(468,157),(431,211),(413,213)):self.tree(x,z,15,6)
        # Creek workshops are south of the tavern, never inside its foundation.
        self.grade(426,371,20,28,92)
        self.cottage(417,354,92);self.cottage(437,379,92)
        self.landmarks['workshop']=[420,93,369]
        self.path([(401,int(self.height[365,401]),365),(409,94,365),(420,92,369),(431,92,371)],5)
        self.river([(448,92,348),(448,92,367),(452,89,382)],2)
        self.tree(411,394,16,6)
        for id,number,name,kind,price,x,z,size,y in LANDS:
            # Gently rolled buildable meadows, low corner markers, no perimeter wall.
            self.grade(x+size//2,z+size//2,size//2+6,size//2+6,y)
            for xx,zz in ((x,z),(x+size-1,z),(x,z+size-1),(x+size-1,z+size-1)):
                ground=int(self.height[zz,xx]);self.box(xx,ground,zz,xx,ground+1,zz,'mossy_stone_bricks')
            self.landmarks[id]=[x+size//2,y+1,z+size-1]
        self.path([(78,91,271),(78,91,281),(115,95,281),(115,91,319)],3)
        self.path([(158,87,463),(178,87,470),(220,88,455)],3)
        self.path([(358,91,479),(329,89,480),(300,87,475),(270,86,468)],3)
        self.fringe_groves()
        # Trees added after roads may overhang them. Trim foliage only, preserving
        # authored arches and clearing a walkable three-block-high corridor.
        for nodes in self.routes:
            for a,b in zip(nodes,nodes[1:]):
                length=max(abs(a[0]-b[0]),abs(a[2]-b[2]))
                for t in np.linspace(0,1,length*2+1):
                    x,_,z=np.rint(np.array(a)*(1-t)+np.array(b)*t).astype(int)
                    for dx in (-1,0,1):
                        for dz in (-1,0,1):
                            xx,zz=x+dx,z+dz
                            if not self.allowed(xx,zz):continue
                            y=int(self.height[zz,xx])
                            for yy in range(y+1,min(220,y+4)):
                                n=self.names[self.scene[yy,zz,xx]]
                                if '_leaves' in n or n.split('[')[0].endswith(('_log','grass','fern')):self.put(xx,yy,zz,'air')
        # Flower drifts and fern banks: clustered, grounded and never on floors/paths.
        flowers=['azure_bluet','oxeye_daisy','blue_orchid','allium']
        for _ in range(3800):
            x=RNG.randrange(30,490);z=RNG.randrange(30,490)
            if not self.allowed(x,z):continue
            y=int(self.height[z,x]);base=self.names[self.scene[y,z,x]]
            if 'grass_block' not in base or self.scene[y+1,z,x]!=0:continue
            if math.sin(x*.12)+math.cos(z*.1)>.8:
                self.put(x,y+1,z,flowers[(x//27+z//31)%4] if RNG.random()<.35 else 'grass')
        return self

    def export(self):
        OUT.mkdir(parents=True,exist_ok=True);DATA.mkdir(parents=True,exist_ok=True)
        changed=self.scene!=self.before
        assert not changed[:,self.protected].any(),'Protected town/portal/property changed'
        y,z,x=np.where(changed);new=self.scene[y,z,x]
        used=sorted(set(new.tolist()));ids={v:i for i,v in enumerate(used)}
        tilekeys=(z//16)*33+x//16;tiles=[]
        for tile in sorted(set(tilekeys.tolist())):
            sel=tilekeys==tile
            rows=np.column_stack((x[sel],y[sel],z[sel],[ids[int(v)] for v in new[sel]])).astype(np.int32)
            rows=rows[np.lexsort((rows[:,1],rows[:,0],rows[:,2]))]
            # Split tall sculpted chunks to keep per-piece parsing bounded.
            for start in range(0,len(rows),4096):tiles.append(nbtlib.Compound({'Blocks':nbtlib.IntArray(rows[start:start+4096].ravel())}))
        root=nbtlib.File({'Version':nbtlib.Int(1),'Palette':nbtlib.List[nbtlib.Compound]([state_tag(self.names[i]) for i in used]),'Tiles':nbtlib.List[nbtlib.Compound](tiles)})
        stream=io.BytesIO();root.write(stream);(DATA/'landscape.nbt').write_bytes(gzip.compress(stream.getvalue(),mtime=0))
        (DATA/'sites.json').write_text(json.dumps(self.landmarks,ensure_ascii=False,indent=2)+'\n','utf-8')
        catalog_path=ROOT/'src/main/resources/data/tnc/housing/catalog.json'
        catalog=json.loads(catalog_path.read_text('utf-8'));catalog['plots']=[p for p in catalog['plots'] if not p['id'].startswith('sky_')]
        for id,number,name,kind,price,px,pz,size,py in LANDS:
            catalog['plots'].append({'id':id,'number':number,'name':name,'kind':kind,'price':price,
                'min':[px,py-10,pz],'max':[px+size-1,py+48,pz+size-1],
                'entry':[px+size//2,py+1,pz+size-1],
                'detail':f'{size}×{size}格云溪新岛土地；低石桩标记四角，入口连公共步道。可建设产魔花园、庭院与农场。'})
        catalog_path.write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n','utf-8')
        preview(self.scene,self.names,OUT/'landscape-top.png')
        preview(self.before,self.names,OUT/'source-top.png')
        np.savez_compressed(OUT/'scene.npz',scene=self.scene,names=self.names,height=self.height)
        report={'changed_blocks':len(x),'tiles':len(tiles),'protected_untouched':True,
                'natural_fringe_columns':self.fringe_columns,'fringe_groves':self.fringe_trees,
                'sites':self.landmarks,'routes':self.routes}
        (OUT/'build.json').write_text(json.dumps(report,indent=2)+'\n','utf-8');print(json.dumps(report),flush=True)

if __name__=='__main__':Garden().build().export()
