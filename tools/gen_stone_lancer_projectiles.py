"""Boss-only earth/gold and blackstone variants; reuse existing stitched block textures."""
import json
from pathlib import Path

root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/tnc/models/projectile'
for name in ['earth_spear_sharp','earth_spike_sharp','earth_orb','earth_clod','earth_shell']:
    model=json.loads((root/(name+'.json')).read_text())
    model['textures']={'stone':'tnc:block/earth_amber_stone',
                       'black':'minecraft:block/polished_blackstone',
                       'gold':'minecraft:block/gilded_blackstone',
                       'particle':'tnc:block/earth_amber_stone'}
    for i,element in enumerate(model.get('elements',[])):
        for j,face in enumerate(element['faces'].values()):
            face['texture']='#gold' if i%7==0 else '#black' if (i+j)%3==0 else '#stone'
    (root/('stone_lancer_'+name+'.json')).write_bytes((json.dumps(model,indent=2)+'\n').encode())

# The remote-response core shares the authored earth orb mesh at triple in-world scale.
(root/'stone_lancer_gravity_core.json').write_bytes((root/'stone_lancer_earth_orb.json').read_bytes())
