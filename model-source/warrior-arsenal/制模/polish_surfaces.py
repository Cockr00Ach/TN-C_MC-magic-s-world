from pathlib import Path
from zipfile import ZipFile
import hashlib
import io
import json
import os
import tempfile

import numpy as np
from PIL import Image, ImageDraw
from scipy.ndimage import gaussian_filter
from scipy.ndimage import distance_transform_edt
from roster import RES

ROOT = Path('TN-C-战士全兵器精修').resolve(strict=True)
SOURCE = ROOT / 'TN-C-战士兵器精修.zip'
OUT = ROOT / 'TN-C-战士兵器表面精修.zip'
BASE_SHA = '1ab38a80322c89c30f76dd1bb3f3ef2d8e4c358f22ec4b40550c5f49e1260534'
FACADES = {
    f'assets/tnc/models/item/{key}.json': key for key in ('greatsword', 'hammer')
}
SWORD_ISLANDS = [(0,0,256,256),(256,128,384,256),(256,0,384,128),
                 (384,0,512,160),(384,160,512,256)]


def islands(name, size):
    if name.endswith('/bronze_atlas.png'):
        return [(x,0,x+64,256) for x in range(0,384,64)] + [
            (x,y,x+32,y+64) for y in range(0,192,64) for x in range(384,512,32)
        ] + [(384,192,512,256)]
    if size == (512,256):
        return SWORD_ISLANDS
    side = 256
    return [(x,y,x+side,y+side)
            for y in range(0,size[1],side) for x in range(0,size[0],side)]


def polish(tile, long_blade):
    pixels = np.asarray(tile, dtype=np.float32)
    axis = (2.45, 1.25, 0) if long_blade else (1.15, 1.10, 0)
    field = gaussian_filter(pixels, sigma=axis, mode='nearest')
    local = gaussian_filter(pixels, sigma=(.85,.85,0), mode='nearest')
    detail = pixels - local
    # Keep deep engraved marks. Reduce common abrupt steps in the reflected light,
    # especially rows inherited from the width of individual blade sections.
    edge = np.max(np.abs(detail), axis=2, keepdims=True)
    keep = np.clip((edge - 13.) / 30., 0., 1.)
    surface = pixels + (field - pixels) * (.82 - .57 * keep)
    out = Image.fromarray(np.uint8(np.rint(surface).clip(0,255)))
    return out.resize((tile.width * 2, tile.height * 2), Image.Resampling.BICUBIC)


def refine(name, artwork):
    result = Image.new('RGB', (artwork.width*2, artwork.height*2))
    for i, box in enumerate(islands(name, artwork.size)):
        blade = (artwork.size == (512,256) and i == 0) or (name.endswith('/bronze_atlas.png') and i < 6)
        result.paste(polish(artwork.crop(box), blade), (box[0]*2, box[1]*2))
    return result


def surface_facade(key):
    old = Image.open(RES / f'assets/tnc/textures/item/{key}.png').convert('RGBA')
    base = np.asarray(old, dtype=np.float32)
    covered = base[:,:,3] > 0
    rgb = base[:,:,:3]
    gold = rgb[:,:,0] > 1.20 * np.maximum(1, rgb[:,:,2])
    composed = rgb.copy()
    for mask in (covered & gold, covered & ~gold):
        # Interpolate within the same material so steel never picks up the hilt colour.
        weight = gaussian_filter(mask.astype(np.float32), 1.04)
        smoothed = gaussian_filter(rgb * mask[:,:,None], (1.04,1.04,0)) / np.maximum(.0001, weight[:,:,None])
        composed[mask] = .24 * rgb[mask] + .76 * smoothed[mask]
    dist, (ys,xs) = distance_transform_edt(~covered, return_indices=True)
    composed[~covered] = composed[ys[~covered], xs[~covered]]
    im = Image.fromarray(np.uint8(composed.clip(0,255))).resize((256,256), Image.Resampling.BICUBIC)
    im.putalpha(old.getchannel('A').resize(im.size, Image.Resampling.NEAREST))
    stream = io.BytesIO()
    im.save(stream,'PNG',optimize=True)
    return stream.getvalue()


def run():
    assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == BASE_SHA
    current_dir = ROOT / '预览'
    samples = ('greatsword', 'earthshaker_hammer', 'doom_scythe', 'bronze_atlas')
    sheet = Image.new('RGB',(912,680),'#1c232c')
    pen = ImageDraw.Draw(sheet)
    pen.text((218,12),'PREVIOUS MATERIAL',fill='#d9e2ec')
    pen.text((620,12),'REFINED MATERIAL',fill='#d9e2ec')
    count = 0
    pngs = {}
    with ZipFile(SOURCE) as base, tempfile.NamedTemporaryFile(dir=ROOT, suffix='.tmp',delete=False) as stage:
        stage_path = Path(stage.name)
        with ZipFile(stage, 'w') as dest:
            for info in base.infolist():
                data = base.read(info)
                if info.filename in FACADES:
                    key = FACADES[info.filename]
                    model = json.loads(data)
                    model['textures']['facade'] = f'tnc:item/arsenal_26/{key}_facade'
                    data = json.dumps(model,ensure_ascii=False,separators=(',',':')).encode('utf-8')
                if info.filename.endswith('.png'):
                    artwork = Image.open(io.BytesIO(data))
                    assert artwork.mode == 'RGB', info.filename
                    fixed = refine(info.filename, artwork)
                    stream = io.BytesIO()
                    fixed.save(stream, 'PNG', optimize=True)
                    data = stream.getvalue()
                    pngs[info.filename] = fixed.size
                    key = Path(info.filename).stem
                    if key in samples:
                        y = 45+samples.index(key)*155
                        box = (0,45,256,198)
                        pen.text((14,y+7),key,fill='#dce4ee')
                        sheet.paste(artwork.crop(box).resize((340,130),Image.Resampling.NEAREST),(216,y))
                        sheet.paste(fixed.crop(tuple(v*2 for v in box)).resize((340,130),Image.Resampling.LANCZOS),(563,y))
                    count += 1
                dest.writestr(info, data)
            for key in FACADES.values():
                name = f'assets/tnc/textures/item/arsenal_26/{key}_facade.png'
                dest.writestr(name, surface_facade(key), compress_type=8)
    try:
        assert count == 41, count
        with ZipFile(stage_path) as polished, ZipFile(SOURCE) as base:
            assert polished.testzip() is None and len(polished.namelist()) == len(base.namelist()) + 2
            for name in base.namelist():
                if name in pngs:
                    with Image.open(io.BytesIO(polished.read(name))) as im:
                        assert im.mode == 'RGB' and im.size == pngs[name]
                elif name in FACADES:
                    new = json.loads(polished.read(name))
                    before = json.loads(base.read(name))
                    before['textures']['facade'] = new['textures']['facade']
                    assert before == new
                    assert new['textures']['facade'].replace('tnc:', 'assets/tnc/textures/')+'.png' in polished.namelist()
                else:
                    assert polished.read(name) == base.read(name), name
        os.replace(stage_path, OUT)
    finally:
        stage_path.unlink(missing_ok=True)
    sheet.save(current_dir / '武器材质局部对照.png')
    render_preview(current_dir)
    print(count, OUT, hashlib.sha256(OUT.read_bytes()).hexdigest())


def render_preview(folder):
    import gallery

    original_texture = gallery.texture
    weapons = ('greatsword', 'doom_scythe', 'spear', 'earthshaker_hammer', 'ember_bow', 'bronze_halberd')
    width, height = 160, 230
    page = Image.new('RGB', (width * len(weapons), 2 * (height + 34)), '#18222c')
    pen = ImageDraw.Draw(page)
    for row, (label, pack) in enumerate((('调整前', SOURCE), ('调整后', OUT))):
        with ZipFile(pack) as archive:
            names = set(archive.namelist())

            def texture(ref, patched):
                namespace, location = ref.split(':', 1)
                path = f'assets/{namespace}/textures/{location}.png'
                if path not in names:
                    return original_texture(ref, patched)
                return Image.open(io.BytesIO(archive.read(path))).convert('RGBA')

            original_model = gallery.model

            def model(ref, patched):
                key = ref.split('/')[-1]
                resolved = original_model(ref, patched)
                if row and key in FACADES.values():
                    return {**resolved, 'textures': {**resolved['textures'],
                            'facade': f'tnc:item/arsenal_26/{key}_facade'}}
                return resolved

            gallery.texture = texture
            gallery.model = model
            try:
                y = row * (height + 34)
                pen.text((8,y+4), label, font=gallery.font(16,True), fill='#edf2f6')
                for col, key in enumerate(weapons):
                    model = gallery.panel(key, True, (width,height-10), yaw=-29, pitch=12, roll=-11)
                    page.paste(model, (col*width,y+28))
                    pen.text((col*width+width//2,y+254), gallery.file_name(key),
                             anchor='mm', font=gallery.font(12), fill='#edf2f6')
            finally:
                gallery.texture = original_texture
                gallery.model = original_model
    page.save(folder / '武器表面精修对照.png')


if __name__ == '__main__':
    run()

