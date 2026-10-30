"""Validate actual client captures and keep the accepted portrait cores / tortoise untouched."""
import hashlib,json
from pathlib import Path
import numpy as np
from PIL import Image,ImageChops,ImageFilter

root=Path(__file__).resolve().parents[1];run=root/'work/boss-bar-materials-client-20261026'
previous=root/'work/boss-hud-portraits-client-20261026/screenshots'
report=json.loads((run/'report.json').read_text(encoding='utf-8'))
assert report['failures']==[] and report['cacheCleared'] and report['foreignBarPreserved'] and report['variantsRendered']==13,report
atlas=Image.open(root/'src/main/resources/assets/tnc/textures/gui/boss_hud/materials_v1.png').convert('RGBA')
assert atlas.size==(1024,2048)
tiles=[atlas.crop((0,i*128,1024,i*128+128)) for i in range(12)]
assert len({hashlib.sha256(v.tobytes()).hexdigest() for v in tiles})==12
for tile in tiles:
    assert tile.getchannel('A').getextrema()==(0,255)
    assert tile.crop((130,45,894,83)).getchannel('A').getextrema()==(0,0),'Health channel must remain transparent'
names=['apollo','dark_giant','stone_lancer','water_leviathan','volcanic_tortoise','yan_dark',
       'wind_general','wind_warden','canglan_warlord','ink_abyss_drake','ink_crane_boss','ink_winged_tiger','ink_celestial_dragon']
portrait_deltas={}
for name,start,end in [('epic-bars-elements.png',0,6),('epic-bars-wind-and-ink.png',6,13)]:
    old=Image.open(previous/name).convert('RGB');new=Image.open(run/'screenshots'/name).convert('RGB')
    assert old.size==new.size==(1400,1000)
    for i in range(start,end):
        if i==4:continue
        portrait=Image.open(root/'art/boss-hud-portraits-20261026'/(names[i]+'.png'))
        core=np.asarray(portrait.getchannel('A').resize((72,72),Image.Resampling.BILINEAR).filter(ImageFilter.MinFilter(3)))==255
        assert core.sum()>40,names[i]
        top=97+(i-start)*124;area=(298,top,370,top+72)
        delta=np.abs(np.asarray(old.crop(area)).astype(int)-np.asarray(new.crop(area)).astype(int)).max(2)
        # A texture split may change one raster edge; opaque face centers must remain visually identical.
        changed=int((delta[core]>2).sum());assert changed<=3,(names[i],changed)
        portrait_deltas[names[i]]=changed
        bar=(416,top+25,1010,top+42)
        assert ImageChops.difference(old.crop(bar),new.crop(bar)).getbbox() is not None,('Material did not reach live GUI',names[i])
    if start==0:
        assert ImageChops.difference(old.crop((260,572,1100,686)),new.crop((260,572,1100,686))).getbbox() is None,'Tortoise changed'
log=(root/'work/boss-bar-materials-client-20261026.log').read_text(encoding='utf-8',errors='replace')
assert 'Failed to load texture: tnc:textures/gui/boss_hud/materials_v1.png' not in log
report.update(materialVariants=12,volcanicTortoisePixelIdentical=True,acceptedPortraitCoreDifferences=portrait_deltas,
              atlasBytes=(root/'src/main/resources/assets/tnc/textures/gui/boss_hud/materials_v1.png').stat().st_size)
(run/'verified.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
