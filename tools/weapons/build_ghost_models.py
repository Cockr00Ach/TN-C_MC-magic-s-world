"""Low-cost translucent weapon silhouettes; keep the detailed real item intact."""
from pathlib import Path
import json
import weapon_gen as gen


def main():
    output=Path(gen.MODELS)/'warrior_ghost';output.mkdir(exist_ok=True)
    for original in gen.WEAPONS:
        weapon=dict(original)
        weapon['_pixel_parts']=gen.expand_profile_legacy(weapon)
        _,rects=gen.build_texture(weapon)
        model=gen.build_model(weapon,rects)
        target=output/(weapon['id']+'.json')
        target.write_bytes((json.dumps(model,ensure_ascii=False,indent=2)+'\n').encode('utf8'))
        assert len(model['elements'])<128
        print(weapon['id'],len(model['elements']),'ghost blocks')


if __name__=='__main__':main()
