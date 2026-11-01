"""Finish only the shipped fresh-world snapshot; never opens or edits any player save."""
from pathlib import Path
import importlib.util
import json
import math
import shutil
import zipfile

import nbtlib as n
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('anvil_helpers', ROOT / 'tools/repair-town-landscape.py')
anvil = importlib.util.module_from_spec(spec)
spec.loader.exec_module(anvil)


def finish(path):
    with zipfile.ZipFile(path) as z:
        files = {name: z.read(name) for name in z.namelist()}
    regions, chunks, decoded = {}, {}, {}

    def chunk(x, z):
        cx, cz = x // 16, z // 16
        name = f'realm/region/r.{cx // 32}.{cz // 32}.mca'
        if name not in regions:
            raw = files[name]
            regions[name] = [raw, anvil.region_chunks(raw)]
        key = (cx, cz)
        if key not in chunks:
            chunks[key] = anvil.read_chunk(regions[name][1][cx % 32 + cz % 32 * 32])
        return chunks[key]

    def section(x, y, z):
        c = chunk(x, z)
        key = (x // 16, y // 16, z // 16)
        if key not in decoded:
            s = next((v for v in c['sections'] if int(v['Y']) == y // 16), None)
            if s is None:
                s = n.Compound({'Y': n.Byte(y // 16), 'block_states': n.Compound({'palette': n.List[n.Compound]([n.Compound({'Name': n.String('minecraft:air')})])})})
                c['sections'].append(s)
            states = s['block_states']
            pal = list(states['palette'])
            decoded[key] = [s, pal, anvil.decode(states), {anvil.state_key(v): i for i, v in enumerate(pal)}]
        return decoded[key]

    def get(x, y, z):
        _, pal, values, _ = section(x, y, z)
        return str(pal[int(values[y % 16 * 256 + z % 16 * 16 + x % 16])]['Name'])

    modified, columns = set(), []

    def put(x, y, z, name):
        s, pal, values, ids = section(x, y, z)
        tag = n.Compound({'Name': n.String(name)})
        if name == 'minecraft:grass_block':
            tag['Properties'] = n.Compound({'snowy': n.String('false')})
        key = anvil.state_key(tag)
        if key not in ids:
            ids[key] = len(pal)
            pal.append(tag)
        index = y % 16 * 256 + z % 16 * 16 + x % 16
        if int(values[index]) != ids[key]:
            values[index] = ids[key]
            modified.add((x // 16, z // 16))

    natural = {'minecraft:air', 'minecraft:cave_air', 'minecraft:grass_block', 'minecraft:dirt',
               'minecraft:stone', 'minecraft:andesite', 'minecraft:coarse_dirt', 'minecraft:rooted_dirt',
               'minecraft:moss_block', 'minecraft:grass', 'minecraft:tall_grass', 'minecraft:fern'}
    # Expanded gate centre and underside come from the published island anchors.
    gx, gz, underside = -51, 164, 179
    for x in range(gx - 35, gx + 36):
        for z in range(gz - 35, gz + 36):
            radius = math.hypot(x - gx, z - gz)
            if radius > 34:
                continue
            block_above = get(x, underside, z)
            supports_gate = abs(x - gx) <= 17 and abs(z - gz) <= 17 and block_above not in natural
            old = next((y for y in range(underside - 1, 139, -1)
                        if get(x, y, z) not in {'minecraft:air', 'minecraft:cave_air', 'minecraft:grass', 'minecraft:tall_grass', 'minecraft:fern'}), None)
            if old is None or old >= underside - 1:
                continue
            if not supports_gate and get(x, old, z) not in natural:
                continue  # Never bury an adjacent authored building or container.
            blend = max(0., min(1., 1 - max(0., radius - 20) / 14))
            blend = blend * blend * (3 - 2 * blend)
            top = underside - 1 if supports_gate else round(old + (underside - 1 - old) * blend)
            if top <= old:
                continue
            # Only close the visible gap above the existing ground, never fill caves.
            if get(x, old, z) == 'minecraft:grass_block':
                put(x, old, z, 'minecraft:dirt')
            for y in range(old + 1, top + 1):
                if get(x, y, z) not in {'minecraft:air', 'minecraft:cave_air', 'minecraft:grass', 'minecraft:tall_grass', 'minecraft:fern'}:
                    continue
                if supports_gate:
                    name = 'minecraft:polished_deepslate' if y == top else 'minecraft:deepslate_bricks' if y >= top - 2 else 'minecraft:cobbled_deepslate'
                else:
                    name = 'minecraft:grass_block' if y == top else 'minecraft:dirt' if y >= top - 3 else 'minecraft:stone'
                put(x, y, z, name)
            columns.append((x, z, top + 1))

    for (cx, sy, cz), (s, pal, values, _) in decoded.items():
        if (cx, cz) in modified:
            s['block_states'] = anvil.encode(pal, values)
    for (cx, cz), c in chunks.items():
        if (cx, cz) not in modified:
            continue
        # Opaque additions only: each heightmap can safely grow, preserving higher
        # roof/leaf/portal tops. Re-light changed chunks instead of retaining old air.
        for key, words in c.get('Heightmaps', {}).items():
            array = [int(v) & ((1 << 64) - 1) for v in words]
            for x, z, height in columns:
                if (x // 16, z // 16) != (cx, cz):
                    continue
                index = z % 16 * 16 + x % 16
                word, shift = index // 7, index % 7 * 9
                old_height = array[word] >> shift & 511
                if height > old_height:
                    array[word] = array[word] & ~(511 << shift) | height << shift
            c['Heightmaps'][key] = n.LongArray(np.array(array, dtype=np.uint64).view(np.int64))
        c['isLightOn'] = n.Byte(0)
        for s in c['sections']:
            s.pop('BlockLight', None)
            s.pop('SkyLight', None)
        name = f'realm/region/r.{cx // 32}.{cz // 32}.mca'
        regions[name][1][cx % 32 + cz % 32 * 32] = anvil.serialize(c)
    for name, (raw, records) in regions.items():
        if any(f'realm/region/r.{x // 32}.{z // 32}.mca' == name for x, z in modified):
            files[name] = anvil.pack_region(raw, records)
    manifest = json.loads(files['manifest.json'])
    manifest.update(revision=2, gateway_foundation={'centre': [gx, underside - 1, gz], 'radius': 34,
                    'changed_chunks': len(modified), 'raised_columns': len(columns)})
    files['manifest.json'] = json.dumps(manifest).encode()
    temp = path.with_suffix('.new')
    with zipfile.ZipFile(temp, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name, data in files.items():
            archive.writestr(name, data)
    temp.replace(path)
    print(json.dumps(manifest['gateway_foundation']))


if __name__ == '__main__':
    target = ROOT / 'src/main/resources/data/tnc/skyhaven/starter.zip'
    backup = ROOT / 'work/backups/skyhaven-before-gateway-foundation.zip'
    backup.parent.mkdir(parents=True, exist_ok=True)
    if not backup.exists():
        shutil.copy2(target, backup)
    finish(target)
