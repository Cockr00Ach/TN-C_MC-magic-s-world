"""Read-only occupancy projections for choosing abyss-citadel placement."""
from pathlib import Path
import json
import numpy as np
from PIL import Image
from building_assets import read_blueprint, AIR

source = Path(r"D:\mc建组\the-distortion-citadel.schem")
blocks, palette, version, entities = read_blueprint(source)
solid = np.array([s.split('[')[0] not in AIR for s in palette])[blocks]
out = Path('work/citadel-analysis')
out.mkdir(parents=True, exist_ok=True)
print('palette', palette)
for name, axis in [('front',1), ('side',2), ('top',0)]:
    occupancy = solid.sum(axis=axis)
    if name != 'top': occupancy = occupancy[::-1]
    pixels = np.zeros((*occupancy.shape,3),dtype=np.uint8)
    pixels[:] = (22,27,36)
    pixels[occupancy>0] = (184,198,208)
    pixels[occupancy>20] = (219,159,105)
    Image.fromarray(pixels).resize((pixels.shape[1]*3,pixels.shape[0]*3)).save(out/(name+'.png'))
counts = solid.sum(axis=(1,2))
print('layers', [(y,int(counts[y])) for y in range(len(counts)) if counts[y] and (y%8==0 or y<8 or y>375)])
(out/'layers.json').write_text(json.dumps(counts.tolist()),encoding='utf8')
