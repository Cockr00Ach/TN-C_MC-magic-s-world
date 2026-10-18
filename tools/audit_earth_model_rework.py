from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
for name in ['earth_clod','earth_orb','earth_shell','earth_salvo','earth_spear','earth_pillar','earth_mud_plate','earth_root']:
    model=json.loads((root/f'src/main/resources/assets/tnc/models/projectile/{name}.json').read_text(encoding='utf-8'))
    assert 0<len(model['elements'])<=32
    for box in model['elements']:
        assert all(-16<=v<=32 for v in box['from']+box['to'])
        assert all(a<b for a,b in zip(box['from'],box['to']))
        for face in box['faces'].values():assert face['texture'].removeprefix('#') in model['textures']
        if 'rotation' in box:assert box['rotation']['angle'] in (-45,-22.5,0,22.5,45)
    assert all(v.startswith('minecraft:block/') for v in model['textures'].values())
baseline=root/'work/wind-earth-source-baseline'
for name in ['magic/TNTornadoEntity','magic/TNWindBladeEntity','client/TNTornadoRenderer','client/TNWindBladeRenderer']:
    relative=f'src/main/java/com/tnc/tnc/{name}.java'
    assert (root/relative).read_bytes()==(baseline/relative).read_bytes()
print('PASS: 8 solid textured models have valid elements and material references; tornado and emitted blade sources restored exactly.')
