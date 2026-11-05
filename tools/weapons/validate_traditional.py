"""Audit the shipped detailed and ghost geometry, materials, mounts and recipe links."""
import json
import math
from pathlib import Path
from PIL import Image
import traditional_weapons as t
import weapon_gen as g


def main():
    total=0
    root=Path(g.ROOT)/'src/main/resources'
    for w in t.definitions():
        id=w['id'];models=[]
        tex=Image.open(Path(g.TEX)/(id+'.png')).convert('RGBA')
        assert tex.size==(128,128)
        for folder in ('','warrior_ghost/'):
            m=json.loads((Path(g.MODELS)/(folder+id+'.json')).read_text('utf8'))
            models.append(m)
            for e in m['elements']:
                assert all(math.isfinite(v) and -16<=v<=32 for v in e['from']+e['to']),id
                assert all(e['from'][i]<e['to'][i] for i in range(3)),id
                for face in e['faces'].values():
                    uv=face['uv']
                    assert face['texture']=='#0' and all(0<=v<=16 for v in uv),id
                    assert uv[0]!=uv[2] and uv[1]!=uv[3],id
                    assert tex.getpixel((int((uv[0]+uv[2])*4),int((uv[1]+uv[3])*4)))[3]==255,id
        m,ghost=models
        assert len(m['elements'])>=300 and len(ghost['elements'])<128,id
        assert m['tnc_grip']==ghost['tnc_grip'],id
        for slot in ('thirdperson_righthand','thirdperson_lefthand','firstperson_righthand','firstperson_lefthand'):
            assert m['display'][slot]==ghost['display'][slot],id
        for left in (False,True):
            slot='thirdperson_lefthand' if left else 'thirdperson_righthand'
            actual=g.transform_point(m['tnc_grip'],m['display'][slot],left)
            reference={'rotation':[0,90 if left else -90,-55 if left else 55],'translation':[0,4,.5],'scale':[.85]*3}
            target=g.transform_point([3,3,8],reference,left)
            assert max(abs(a-b) for a,b in zip(actual,target))<1e-5,id+' hand anchor'
        tag={'longsword':'longswords','greatsword':'greatswords','spear':'spears','hammer':'hammers'}[w['family']]
        assert 'tnc:'+id in json.loads((root/'data/tnc/tags/items'/f'{tag}.json').read_text('utf8'))['values']
        for lang in ('en_us','zh_cn'):
            assert 'item.tnc.'+id in json.loads((root/'assets/tnc/lang'/f'{lang}.json').read_text('utf8'))
        recipe=json.loads((root/'data/tnc/recipes'/f'{id}.json').read_text('utf8'))
        assert recipe['result']['item']=='tnc:'+id
        assert set(''.join(recipe['pattern']))-{' '}==set(recipe['key'])
        total+=len(m['elements'])
    print(f'PASS: {len(t.IDS)} weapons / {total} detailed blocks; opaque UVs, both hand anchors, matching ghosts, families, recipes and translations')


if __name__=='__main__':main()
