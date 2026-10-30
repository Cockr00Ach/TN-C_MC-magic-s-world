"""Verify texture coverage, native HUD integration, and unchanged bars/tortoise captures."""
import hashlib,json
from pathlib import Path
from PIL import Image,ImageChops
import numpy as np

root=Path(__file__).resolve().parents[1]
run=root/'work/boss-hud-portraits-client-20261026'
previous=root/'work/epic-boss-hud-refined-client-20261026/screenshots'
report=json.loads((run/'report.json').read_text(encoding='utf-8'))
assert not report['failures'],report
assert report['foreignBarPreserved'] and report['cacheCleared'] and report['variantsRendered']==13
atlas=Image.open(root/'src/main/resources/assets/tnc/textures/gui/boss_hud/portraits_v1.png').convert('RGBA')
assert atlas.size==(1024,768)
tiles=[atlas.crop((i%4*256,i//4*256,i%4*256+256,i//4*256+256)) for i in range(12)]
assert len({hashlib.sha256(tile.tobytes()).hexdigest() for tile in tiles})==12
for tile in tiles:
    assert tile.getchannel('A').getextrema()==(0,255)
    assert tile.getchannel('A').getbbox() is not None
raster_differences={}
for name in ('epic-bars-elements.png','epic-bars-wind-and-ink.png'):
    old=Image.open(previous/name).convert('RGB');new=Image.open(run/'screenshots'/name).convert('RGB')
    assert old.size==new.size==(1400,1000)
    # The previous sun icon's scroll reached 14 px into the bar. Exclude its full drawing footprint.
    # Texture blits split font batches: half-pixel scaled digit edges may round to a neighboring texel.
    area=(416,72,1100,940)
    delta=np.abs(np.asarray(old.crop(area)).astype(int)-np.asarray(new.crop(area)).astype(int)).max(2)
    changed=int((delta>0).sum());maximum=int(delta.max())
    assert changed<=64 and maximum<=32,('Unexpected non-icon raster differences',name,changed,maximum)
    raster_differences[name]={'pixels':changed,'maximumChannelDelta':maximum}
    for i in range(6 if name=='epic-bars-elements.png' else 7):
        top=112+i*124
        assert ImageChops.difference(old.crop((416,top,1100,top+52)),new.crop((416,top,1100,top+52))).getbbox() is None,('Health surface or ornament changed',name,i)
    assert ImageChops.difference(old.crop((260,60,374,940)),new.crop((260,60,374,940))).getbbox() is not None
old=Image.open(previous/'epic-bars-elements.png').convert('RGB');new=Image.open(run/'screenshots/epic-bars-elements.png').convert('RGB')
assert ImageChops.difference(old.crop((260,572,1100,686)),new.crop((260,572,1100,686))).getbbox() is None
log=(root/'work/boss-hud-portraits-client-20261026.log').read_text(encoding='utf-8',errors='replace')
assert 'Failed to load texture: tnc:textures/gui/boss_hud/portraits_v1.png' not in log
report.update(atlasPortraits=12,staticAtlasBytes=(root/'src/main/resources/assets/tnc/textures/gui/boss_hud/portraits_v1.png').stat().st_size,
              volcanicTortoisePixelIdentical=True,barBodiesAndRightOrnamentsPixelIdentical=True,
              nonIconScaledFontRasterDifferences=raster_differences)
(run/'verified.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
