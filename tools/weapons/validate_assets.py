"""Check actual Minecraft element bounds, UV opacity and bow override resources."""
from pathlib import Path
import json
import math
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'src/main/resources/assets/tnc'
MELEE=['dragon_sword','frost_katana','star_rapier','crimson_warblade','doom_scythe','earthshaker_hammer','sky_halberd']
BOWS=['bow','moon_bow','storm_bow','ember_bow']
IDS=MELEE+[name for bow in BOWS for name in [bow]+[bow+'_pulling_'+str(i) for i in range(3)]]+['storm_arrow','moon_arrow','ember_arrow']


def main():
    blocks=faces=0
    for id in IDS:
        model=json.loads((ASSETS/'models/item'/f'{id}.json').read_text('utf8'))
        image=Image.open(ASSETS/'textures/item'/f'{id}.png').convert('RGBA')
        assert image.size==(128,128),id
        assert model['textures']['0']=='tnc:item/'+id,id
        assert model['elements'],id
        for e in model['elements']:
            assert all(-16<=v<=32 for v in e['from']+e['to']),id
            assert all(e['to'][i]>e['from'][i] for i in range(3)),id
            for face in e['faces'].values():
                uv=face['uv'];assert face['texture']=='#0' and all(math.isfinite(v) and 0<=v<=16 for v in uv),id
                assert uv[0]!=uv[2] and uv[1]!=uv[3],id
                u,v=(uv[0]+uv[2])*4,(uv[1]+uv[3])*4
                assert image.getpixel((min(127,int(u)),min(127,int(v))))[3]==255,id+' samples transparent material'
        for override in model.get('overrides',[]):
            assert (ASSETS/'models'/Path(override['model'].split(':')[1]+'.json')).is_file(),id
        blocks+=len(model['elements']);faces+=sum(len(e['faces']) for e in model['elements'])
    for id in MELEE:
        import subprocess
        before=json.loads(subprocess.check_output(['git','show','4a3f9332:src/main/resources/assets/tnc/models/item/'+id+'.json'],cwd=ROOT))
        after=json.loads((ASSETS/'models/item'/f'{id}.json').read_text('utf8'))
        assert before['tnc_grip']==after['tnc_grip'] and before['display']==after['display'],id+' mount changed'
    print(f'PASS: {len(IDS)} models / {blocks} real blocks / {faces} submitted quads; UV opacity and seven existing mounts verified')


if __name__=='__main__':main()
