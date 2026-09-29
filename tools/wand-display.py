"""Consistent grip transforms; inventory thumbnails retain vanilla scale."""
import json
from pathlib import Path

def model(name, tier):
    scale = [0, 1.3, 1.55, 1.7, 1.85, 2.0][tier]
    display = {}
    for hand, sign in [('righthand', 1), ('lefthand', -1)]:
        display['thirdperson_' + hand] = {'rotation': [0, sign*90, -sign*35], 'translation': [0, 3, 1], 'scale': [scale]*3}
        display['firstperson_' + hand] = {'rotation': [0, sign*90, -sign*25], 'translation': [sign*1, 3, 0], 'scale': [scale]*3}
    return {'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'tnc:item/wands/'+name}, 'display': display}

if __name__ == '__main__':
    root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/tnc/models/item'
    for element in ['water','fire','lightning','wind','earth','light','dark']:
        for tier in range(1,6):
            name=f'{element}_wand_{tier}'
            (root/(name+'.json')).write_text(json.dumps(model(name,tier),indent=2)+'\n',encoding='utf-8')
