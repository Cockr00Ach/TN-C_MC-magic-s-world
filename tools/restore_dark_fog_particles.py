"""Restore bounded native mist batches without changing combat or caster vision."""
import json
from pathlib import Path

SPELLS=('black_mist','night_grace','where_light_cannot_reach','dark_city','devour_light')
def batches(radius,tier):
    def batch(pid,shape,count,extent):
        return {'particle_id':pid,'shape':shape,'origin':'CENTER','count':count,
                'min_speed':.005,'max_speed':.025,'extent':extent}
    return [batch('minecraft:squid_ink','CIRCLE',6+tier*2,radius*.92),
            batch('minecraft:large_smoke','SPHERE',2+tier,radius*.45)]

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
