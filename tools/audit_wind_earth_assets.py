"""Verify that the visual overhaul preserves all spell gameplay data."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
paths = json.loads((ROOT / 'work/wind-earth-asset-paths.json').read_text(encoding='utf-8'))
visual = {'particles', 'travel_particles', 'model', 'show_particles'}

def mechanics(value):
    if isinstance(value, dict):
        return {k: mechanics(v) for k, v in value.items() if k not in visual}
    if isinstance(value, list):
        return [mechanics(v) for v in value]
    return value

spells = 0
for relative in paths:
    current = json.loads((ROOT / relative).read_text(encoding='utf-8-sig'))
    baseline = ROOT / 'work/wind-earth-source-baseline' / relative
    if '/spells/' in relative:
        assert mechanics(current) == mechanics(json.loads(baseline.read_text(encoding='utf-8-sig'))), relative
        spells += 1
    if '/models/projectile/earth_' in relative:
        assert 0 < len(current['elements']) <= 32, relative
        for element in current['elements']:
            assert all(-16 <= v <= 32 for v in element['from'] + element['to']), relative
            assert all(a < b for a, b in zip(element['from'], element['to'])), relative
            if 'rotation' in element:
                assert element['rotation']['angle'] in (-45, -22.5, 0, 22.5, 45), relative
        assert all(v.startswith('minecraft:block/') for v in current['textures'].values()), relative
assert spells == 35, spells
print(f'PASS: {spells} spells retain identical gameplay data; five earth projectile models have valid bounds and rotations.')
