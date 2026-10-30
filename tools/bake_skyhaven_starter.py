"""Bake only authored island chunks from the isolated, verified construction save.

Never exports players, quests, accounts, live NPCs, waiting platforms or overworld.
"""
import io
import json
import pathlib
import struct
import zipfile
import zlib

import nbtlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = pathlib.Path('D:/TNC-Audits/dungeons-20261019/saves/zero-forge')
DEST = ROOT / 'src/main/resources/data/tnc/skyhaven/starter.zip'


def region_subset(path, entities=False):
    raw = path.read_bytes()
    rx, rz = map(int, path.stem.split('.')[1:])
    out = bytearray(8192)
    count = 0
    for index in range(1024):
        cx, cz = rx * 32 + index % 32, rz * 32 + index // 32
        if not (-17 <= cx <= 16 and -17 <= cz <= 16):
            continue
        location = int.from_bytes(raw[index * 4:index * 4 + 4], 'big')
        offset = (location >> 8) * 4096
        if not offset:
            continue
        length = int.from_bytes(raw[offset:offset + 4], 'big')
        assert raw[offset + 4] == 2
        tag = nbtlib.File.parse(io.BytesIO(zlib.decompress(raw[offset + 5:offset + 4 + length])))
        if entities:
            tag['Entities'] = nbtlib.List[nbtlib.Compound]([
                e for e in tag.get('Entities', [])
                if str(e['id']) in {'minecraft:painting', 'minecraft:armor_stand'}
            ])
            if not tag['Entities']:
                continue
        else:
            # Saved ticks refer to the audit world's clock. Fluid sources and leaves
            # resume naturally; do not import its backlog of pending simulation.
            tag['block_ticks'] = nbtlib.List[nbtlib.Compound]()
            tag['fluid_ticks'] = nbtlib.List[nbtlib.Compound]()
            tag['LastUpdate'] = nbtlib.Long(0)
            tag['InhabitedTime'] = nbtlib.Long(0)
        stream = io.BytesIO()
        tag.write(stream)
        payload = zlib.compress(stream.getvalue())
        record = struct.pack('>I', len(payload) + 1) + b'\x02' + payload
        sectors = (len(record) + 4095) // 4096
        assert sectors < 256
        out[index * 4:index * 4 + 4] = ((len(out) // 4096 << 8) | sectors).to_bytes(4, 'big')
        out.extend(record + b'\0' * (sectors * 4096 - len(record)))
        count += 1
    return bytes(out) if count else None, count


def main():
    realm = SOURCE / 'dimensions/tnc/skyhaven'
    state = nbtlib.load(SOURCE / 'data/tnc_sky_island_v5.dat')
    assert str(state['data']['Phase']) == 'COMPLETE'
    assert int(state['data']['OriginX']) == -260 and int(state['data']['OriginZ']) == -260
    state['data']['GroundPortalX'] = nbtlib.Int(0)
    state['data']['GroundPortalY'] = nbtlib.Int(0)
    state['data']['GroundPortalZ'] = nbtlib.Int(0)
    state['data']['ActiveChunks'] = nbtlib.LongArray([])
    state['data']['WaitUntilTick'] = nbtlib.Long(0)
    state['data']['LastError'] = nbtlib.String('')
    DEST.parent.mkdir(parents=True, exist_ok=True)
    total = 0
    with zipfile.ZipFile(DEST, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for kind in ['region', 'entities']:
            for path in sorted((realm / kind).glob('*.mca')):
                data, count = region_subset(path, kind == 'entities')
                if data:
                    archive.writestr(f'realm/{kind}/{path.name}', data)
                    if kind == 'region':
                        total += count
        for path in sorted((realm / 'data').glob('tnc_*.dat')):
            tag = nbtlib.load(path)['data']
            assert tag.get('Done', tag.get('Phase', -1)) in (1, 2), path
            archive.writestr('realm/data/' + path.name, path.read_bytes())
        buffer = io.BytesIO()
        state.save(buffer, gzipped=True)
        archive.writestr('island.dat', buffer.getvalue())
        archive.writestr('manifest.json', json.dumps({'revision': 1, 'chunks': total,
            'origin': [-260, 90, -260], 'source': 'isolated verified authored scenery',
            'excludes': ['players', 'progress', 'accounts', 'NPCs', 'overworld', 'waiting platform']}))
    assert total >= 1024, total
    print(f'Baked {total} chunks, {DEST.stat().st_size} bytes')


if __name__ == '__main__':
    main()
