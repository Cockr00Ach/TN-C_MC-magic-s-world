"""Paint each sea-garden atlas in six UV islands, with separate stem, blade and light materials."""
from pathlib import Path
import math, random
from PIL import Image

ASSET = Path(__file__).resolve().parents[1] / "src/main/resources/assets/tnc/textures/block/seagarden"
# Lower stem, photosynthetic blade, secondary blade, petal or rib, luminous organ, fruit.
PALETTES = {
    "tidebreath_lantern": ("163d48","237d79","4ea99b","b98273","e8f9b2","62cbd1"),
    "undertow_bindweed": ("302d34","496b46","867254","9c513b","ffc979","a97652"),
    "waking_tide_fan": ("313654","467e8f","7778a5","c18d80","b5f1db","855c7b"),
    "moonreef_blossom": ("292749","4e6b88","5ca89c","a38cc7","fff0ac","db9fae"),
    "veilspore_anemone": ("29263d","5a486b","874964","bb6b6c","ffc5ac","674678"),
    "titan_tidekelp": ("384536","2f796b","779947","b89e5a","a1efd2","a6a05d"),
}
def atlas(key, swatches):
    rng = random.Random(key)
    image = Image.new("RGB",(96,64))
    for tile, color in enumerate(swatches):
        base = bytes.fromhex(color)
        for y in range(32):
            for x in range(32):
                edge = abs(x-15.5)/15.5
                u, v = x/31, y/31
                if tile == 0:
                    level = .68 + .21*math.cos(13*u+v*2)**2 + .10*v
                elif tile in (1,2):
                    rib = .13 if abs((y-abs(x-15)*.72) % 7.5 - 1) < .86 else 0
                    level = .67 + .28*(1-edge) + .15*int(edge>.92) + .20*v + rib
                elif tile == 3:
                    level = .66 + .30*v + .21*(1-edge) + .16*math.cos(12*u+v*2)**8
                elif tile == 4:
                    level = .88 + .18*(1-edge) + .11*v
                else:
                    level = .68 + .26*(1-edge**1.6) + .15*math.sin(9*u+.75*v)**6 + .12*v
                speck = rng.uniform(-.025,.025)
                image.putpixel((tile%3*32+x,tile//3*32+y),tuple(min(255,max(0,round(c*(level+speck)))) for c in base))
    return image
if __name__ == "__main__":
    ASSET.mkdir(parents=True,exist_ok=True)
    for name, colors in PALETTES.items():
        atlas(name,colors).save(ASSET/(name+".png"))

