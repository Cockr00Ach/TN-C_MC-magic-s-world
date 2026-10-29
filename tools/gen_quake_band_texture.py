"""Generate the repeating warm translucent wave surface and its narrow gold crest."""
import argparse
import math
from pathlib import Path
from PIL import Image

def smooth(t):
    t = max(0.0, min(1.0, t))
    return t*t*(3-2*t)

def generate(root):
    size = 128
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(size):
        v = y / (size-1)
        edge = smooth(v/0.12) * smooth((1-v)/0.12)
        ridge = math.exp(-0.5*((v-0.5)/0.028)**2)
        for x in range(size):
            u = x/size
            flow = math.sin(math.tau*(u*3-v*1.5))
            strand = (0.5+0.5*math.sin(math.tau*(u*2-v*3)))**10
            alpha = edge * ((0.25+0.012*flow)*(1-ridge) + 0.50*ridge)
            base = (255, 164, 86)
            gold = (255, 231, 163)
            warm = min(1.0, ridge + 0.07*strand)
            color = tuple(round((base[i]*(1-warm)+gold[i]*warm)*(0.97+0.03*flow)) for i in range(3))
            image.putpixel((x,y), (*color, round(alpha*255)))
    path = root/"src/main/resources/assets/tnc/textures/entity/quake_band.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    path.with_suffix(".png.mcmeta").write_text('{"texture":{"blur":true,"clamp":false}}\n', encoding="utf-8")
    print(f"wrote {path} (128x128 RGBA)")

if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root",type=Path,default=Path(__file__).resolve().parents[1])
    generate(parser.parse_args().root)
