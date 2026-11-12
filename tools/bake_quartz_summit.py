"""Add the offshore arena to the fresh-world snapshot without opening a save."""
import argparse
from collections import defaultdict
from copy import deepcopy
from hashlib import sha256
from pathlib import Path
from tempfile import NamedTemporaryFile
import json
import zipfile

import nbtlib as n
import numpy as np

from finish_skyhaven_gateway import anvil


DATA = Path(__file__).resolve().parents[1] / 'src/main/resources/data/tnc'
STARTER = DATA / 'skyhaven/starter.zip'
TEMPLATE = DATA / 'structures/quartz_summit.nbt'
ORIGIN = (1152, 144, -416)
SIZE = (253, 104, 253)
ARRIVAL = (1248, 162, -290)
REGION = 'realm/region/r.2.-1.mca'
OCEAN_REGION = 'realm/region/r.0.0.mca'
FLOOR = {'minecraft:smooth_quartz', 'minecraft:polished_diorite', 'minecraft:sea_lantern'}


def read_template(path):
    template = n.load(path)
    if int(template['DataVersion']) != 3465 or tuple(template['size']) != SIZE or template['entities']:
        raise ValueError('Expected the Minecraft 1.20.1 Quartz Summit template')
    palette = list(template['palette'])
    positions = {}
    for block in template['blocks']:
        pos = tuple(map(int, block['pos']))
        state = int(block['state'])
        if ('nbt' in block or not 0 <= state < len(palette) or pos in positions
                or not all(0 <= v < limit for v, limit in zip(pos, SIZE))):
            raise ValueError(f'Invalid arena block at {pos}')
        if not str(palette[state]['Name']).startswith('minecraft:') or palette[state]['Name'] == 'minecraft:air':
            raise ValueError(f'Expected a non-air vanilla block at {pos}')
        positions[pos] = state
    if len(positions) != 111188:
        raise ValueError('Incomplete Quartz Summit template')
    x, y, z = (a - b for a, b in zip(ARRIVAL, ORIGIN))
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            floor = positions.get((x + dx, y - 1, z + dz))
            if (floor is None or palette[floor]['Name'] not in FLOOR
                    or any((x + dx, y + dy, z + dz) in positions for dy in range(3))):
                raise ValueError('Arrival must have a clear 5×5 floor and three blocks of headroom')
    return palette, positions


def make_region(ocean, palette, positions):
    groups = defaultdict(lambda: defaultdict(list))
    ox, oy, oz = ORIGIN
    for (dx, dy, dz), state in positions.items():
        x, y, z = ox + dx, oy + dy, oz + dz
        groups[x // 16, z // 16][y // 16].append((y % 16 * 256 + z % 16 * 16 + x % 16, state))
    base = anvil.read_chunk(anvil.region_chunks(ocean)[16 + 16 * 32])
    if (base['Status'] != 'minecraft:full' or base['block_entities']
            or base['block_ticks'] or base['fluid_ticks']
            or any(s['biomes']['palette'] != ['tnc:skyhaven'] for s in base['sections'])
            or any(s['block_states']['palette'][0]['Name'] != 'minecraft:air'
                   or len(s['block_states']['palette']) != 1 for s in base['sections'] if int(s['Y']) >= 4)):
        raise ValueError('Expected a fully generated, undecorated ocean chunk')
    records = {}
    for (cx, cz), changes in groups.items():
        if (cx // 32, cz // 32) != (2, -1):
            raise ValueError('Arena must fit in its reserved region')
        chunk = deepcopy(base)
        chunk['xPos'], chunk['zPos'] = n.Int(cx), n.Int(cz)
        chunk['structures'] = n.Compound({'starts': n.Compound(), 'References': n.Compound()})
        chunk.pop('Heightmaps', None)
        chunk['isLightOn'] = n.Byte(0)
        for section in chunk['sections']:
            section.pop('BlockLight', None)
            section.pop('SkyLight', None)
            entries = changes.pop(int(section['Y']), None)
            if not entries:
                continue
            used = sorted({state for _, state in entries})
            ids = {state: i + 1 for i, state in enumerate(used)}
            values = np.zeros(4096, dtype=np.uint16)
            for index, state in entries:
                values[index] = ids[state]
            section['block_states'] = anvil.encode(
                [n.Compound({'Name': n.String('minecraft:air')})] + [palette[i] for i in used], values)
        if changes:
            raise ValueError('Arena exceeds ocean chunk build height')
        records[cx % 32 + cz % 32 * 32] = anvil.serialize(chunk)
    return anvil.pack_region(bytes(8192), records), len(records)


def bake(starter=STARTER, template=TEMPLATE):
    palette, positions = read_template(template)
    with zipfile.ZipFile(starter) as archive:
        files = {name: archive.read(name) for name in archive.namelist()}
    region, count = make_region(files[OCEAN_REGION], palette, positions)
    manifest = json.loads(files['manifest.json'])
    arenas = manifest.setdefault('arenas', {})
    prior_count = 0
    if REGION in files:
        prior = arenas.get('quartz_summit', {})
        if prior.get('region_sha256') != sha256(files[REGION]).hexdigest():
            raise ValueError('Reserved region contains unknown data; refusing to replace it')
        prior_count = len(anvil.region_chunks(files[REGION]))
    manifest['chunks'] += count - prior_count
    files[REGION] = region
    arenas['quartz_summit'] = {
        'dimension': 'tnc:skyhaven', 'origin': ORIGIN, 'size': SIZE, 'arrival': ARRIVAL,
        'arrival_yaw': -90, 'blocks': len(positions), 'chunks': count, 'region': REGION,
        'region_sha256': sha256(region).hexdigest(),
    }
    manifest['revision'] = max(3, manifest['revision'])
    files['manifest.json'] = json.dumps(manifest).encode()
    with NamedTemporaryFile(dir=starter.parent, suffix='.zip', delete=False) as stream:
        output = Path(stream.name)
    try:
        with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for name, payload in files.items():
                archive.writestr(name, payload)
        output.replace(starter)
    finally:
        output.unlink(missing_ok=True)
    return arenas['quartz_summit']


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--starter', type=Path, default=STARTER)
    parser.add_argument('--template', type=Path, default=TEMPLATE)
    options = parser.parse_args()
    print(json.dumps(bake(options.starter, options.template)))
