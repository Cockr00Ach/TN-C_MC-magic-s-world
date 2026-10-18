"""Validate and install-candidate the three Leviathan appearance assets; preserve every other entry."""
import argparse
import copy
import hashlib
import io
import json
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image
from water_leviathan_materials import face_rects

ASSETS = {
    'assets/tnc/geo/entity/water_leviathan.geo.json',
    'assets/tnc/textures/entity/water_leviathan_bedrock.png',
    'assets/tnc/textures/entity/water_leviathan_emissive.png',
}


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def validate(assets, animations):
    geo = json.loads(assets['assets/tnc/geo/entity/water_leviathan.geo.json'])['minecraft:geometry'][0]
    assert geo['description']['identifier'] == 'geometry.tnc.water_leviathan'
    bones = {b['name']: b for b in geo['bones']}
    assert len(bones) == len(geo['bones']), 'duplicate bone'
    for bone in bones.values():
        ancestors = {bone['name']}
        current = bone
        while current.get('parent'):
            parent = current['parent']
            assert parent in bones and parent not in ancestors, 'invalid bone hierarchy'
            ancestors.add(parent)
            current = bones[parent]
    assert all(name in bones for animation in json.loads(animations)['animations'].values()
               for name in animation.get('bones', {})), 'missing animated bone'
    atlas = np.asarray(Image.open(io.BytesIO(assets['assets/tnc/textures/entity/water_leviathan_bedrock.png'])).convert('RGBA'))
    emissive = np.asarray(Image.open(io.BytesIO(assets['assets/tnc/textures/entity/water_leviathan_emissive.png'])).convert('RGBA'))
    assert atlas.shape == emissive.shape == (1024, 1024, 4)
    occupied = np.zeros((1024, 1024), dtype=bool)
    count = 0
    for bone in bones.values():
        for cube in bone.get('cubes', []):
            assert all(v>0 and int(v)==v for v in cube['size'])
            for x, y, w, h in face_rects(cube).values():
                assert 0<=x<x+w<=1024 and 0<=y<y+h<=1024, 'out-of-bounds UV'
                assert not occupied[y:y+h, x:x+w].any(), 'overlapping face UV'
                assert (atlas[y:y+h, x:x+w, 3] == 255).all(), 'transparent face'
                occupied[y:y+h, x:x+w] = True
            count += 1
    assert not ((emissive[:, :, 3]>0) & (atlas[:, :, 3]==0)).any()
    return {'bones': len(bones), 'cubes': count, 'atlas': [1024, 1024], 'validated_faces': count*6}


def prepare(base, root, output, report):
    if output.exists() or output.resolve() == base.resolve():
        raise ValueError('output must be a new separate jar')
    before = sha(base)
    data = {name: (root/'src/main/resources'/name).read_bytes() for name in ASSETS}
    with zipfile.ZipFile(base) as old:
        assert 'com/tnc/tnc/boss/TNWaterLeviathanEntity.class' in old.namelist(), 'install the initial Boss first'
        assert len(old.namelist()) == len(set(old.namelist())), 'duplicate jar entries'
        evidence = validate(data, old.read('assets/tnc/animations/entity/water_leviathan.animation.json'))
        with zipfile.ZipFile(output, 'w') as new:
            for info in old.infolist():
                new.writestr(copy.copy(info), data.get(info.filename, old.read(info.filename)))
        with zipfile.ZipFile(output) as new:
            assert new.testzip() is None
            assert new.namelist() == old.namelist()
            changed = [name for name in old.namelist() if old.read(name) != new.read(name)]
            assert set(changed) == ASSETS, 'unexpected update scope'
            evidence['preserved_entries'] = len(old.namelist())-len(changed)
            evidence['changed_entries'] = sorted(changed)
    assert sha(base) == before, 'base changed while preparing'
    evidence.update(base=str(base), base_sha256=before, output=str(output),
                    output_sha256=sha(output), unrelated_entries_identical=True)
    report.write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding='utf8')
    print(json.dumps(evidence, ensure_ascii=True, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    for name in ('base', 'output', 'report'):
        parser.add_argument('--'+name, type=Path, required=True)
    args = parser.parse_args()
    prepare(args.base, Path(__file__).resolve().parents[1], args.output, args.report)
