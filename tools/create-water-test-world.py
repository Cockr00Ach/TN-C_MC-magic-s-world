"""Create a NEW isolated flat client world from a completed dev world's level metadata.

Never copies player data/regions, never overwrites an existing save. Requires nbtlib.
"""
from pathlib import Path
import argparse
import nbtlib as n

parser = argparse.ArgumentParser()
parser.add_argument('--source-level', type=Path, required=True)
parser.add_argument('--target', type=Path, required=True)
parser.add_argument('--repair-test-checkpoint', action='store_true')
args = parser.parse_args()
def pause_island(target):
    n.File({'data':n.Compound({'Version':n.Int(5),'Phase':n.String('BUILDING'),
                              'ActiveChunks':n.LongArray([0]),
                              'WaitUntilTick':n.Long(9223372036854775807)})},gzipped=True).save(target/'data/tnc_sky_island_v5.dat')

if args.repair_test_checkpoint:
    if str(n.load(args.target/'level.dat')['Data']['LevelName']) != 'TN-C 水法测试 20260928':
        raise SystemExit('Refusing to modify a non-test world')
    pause_island(args.target)
    print('Paused starter-island construction only in the named water test world')
    raise SystemExit(0)
if args.target.exists():
    raise SystemExit('Target already exists; not changing it')
level = n.load(args.source_level)
data = level['Data']
data['LevelName'] = n.String('TN-C 水法测试 20260928')
data['GameType'] = n.Int(1)
data['allowCommands'] = n.Byte(1)
data['Difficulty'] = n.Byte(2)
data['SpawnX'], data['SpawnY'], data['SpawnZ'] = n.Int(0), n.Int(64), n.Int(0)
data['Time'], data['DayTime'] = n.Long(0), n.Long(6000)
data['raining'], data['thundering'] = n.Byte(0), n.Byte(0)
data.pop('Player', None)
rules = data['GameRules']
rules['doMobSpawning'] = n.String('false')
rules['doDaylightCycle'] = n.String('false')
rules['doWeatherCycle'] = n.String('true')
data['WorldGenSettings']['generate_features'] = n.Byte(0)
layers = n.List[n.Compound]([n.Compound({'block': n.String(block), 'height': n.Int(height)}) for block,height in
                           [('minecraft:bedrock',1),('minecraft:stone',123),('minecraft:dirt',3),('minecraft:grass_block',1)]])
data['WorldGenSettings']['dimensions']['minecraft:overworld']['generator'] = n.Compound({
    'type': n.String('minecraft:flat'), 'settings': n.Compound({
        'biome': n.String('minecraft:plains'), 'features': n.Byte(0), 'lakes': n.Byte(0),
        'layers': layers, 'structure_overrides': n.List[n.String]([])})})
args.target.mkdir(parents=True)
level.save(args.target/'level.dat')
# Pause only this test world's starter-island job; production generation code stays unchanged.
(args.target/'data').mkdir()
pause_island(args.target)
print(f'Created new flat TEST world (no player data or existing chunks): {args.target}')
