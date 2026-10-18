"""Use DarkMistRenderer's continuous vapor instead of ink particle emissions."""
import json
from pathlib import Path

SPELLS=('black_mist','night_grace','where_light_cannot_reach','dark_city','devour_light')
def batches(radius,tier):
    # The cloud entity now draws broad soft sheets every frame at the synced radius.
    # Emitting ink here would overlay the unwanted black specks on that fog.
    return []

def restore(directory):
    for name in SPELLS:
        path=directory/(name+'.json')
        data=json.loads(path.read_text(encoding='utf-8-sig'))
        cloud=data['release']['target']['cloud']
        cloud['client_data']['particles']=batches(cloud['volume']['radius'],data['learn']['tier'])
        path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
        print(name, sum(b['count'] for b in cloud['client_data']['particles']))

if __name__=='__main__':
    root=Path(__file__).resolve().parents[1]
    restore(root/'modpack/元素觉醒1.4.3-魔改版-20260915/kubejs/data/tnc/spells')
