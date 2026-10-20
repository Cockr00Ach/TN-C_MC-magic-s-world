"""Add only the model-first wind general to the installed pack."""
import argparse
import zipfile
from pathlib import Path
from prepare_water_leviathan_update import main

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for name in ('base','built','output','report'):p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args()
    with zipfile.ZipFile(a.built) as jar:
        entries={n for n in jar.namelist() if n.startswith('com/tnc/tnc/boss/wind/') and n.endswith('.class') and 'Audit' not in n}
    entries|={'assets/tnc/geo/entity/wind_general.geo.json','assets/tnc/animations/entity/wind_general.animation.json',
              'assets/tnc/textures/entity/wind_general.png','assets/tnc/models/item/wind_general_spawn_egg.json',
              'assets/tnc/lang/zh_cn.json','assets/tnc/lang/en_us.json'}
    main(a.base,a.built,a.output,a.report,entries=entries,translation_keys=('entity.tnc.wind_general','item.tnc.wind_general_spawn_egg'))
