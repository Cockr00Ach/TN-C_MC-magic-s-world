"""Check editable-project fidelity in a disposable asset copy, never rewrite production files."""
from pathlib import Path
from tempfile import TemporaryDirectory
import base64, contextlib, hashlib, io, json, math, shutil
import equipment_blockbench as bb

R = Path(__file__).resolve().parents[1]
A = R/'src/main/resources/assets/tnc'
S = R/'model-source/equipment'

def load(p): return json.loads(p.read_text('utf8'))
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()

def main():
    manifest=load(S/'volume-redesign-manifest.json')
    for name, digest in manifest['preserved_icon_sha256'].items(): assert sha(A/name)==digest,name
    with TemporaryDirectory(prefix='tnc-equipment-edit-') as directory:
        root=Path(directory); assets=root/'assets'; projects=root/'projects'
        for folder in ['models/item','textures/item/wands/held','models/armor','textures/models/armor']:
            shutil.copytree(A/folder,assets/folder,dirs_exist_ok=True)
        bb.AS=assets;bb.OUT=projects
        for record in manifest['wands']:
            id=record['id'];source=S/'wands'/f'{id}.bbmodel';data=load(source)
            held=A/'models/item/wands/held'/f'{id}.json';original=load(held)
            png=A/'textures/item/wands/held'/f'{id}.png'
            assert base64.b64decode(data['textures'][0]['source'].split(',',1)[1])==png.read_bytes(),id
            assert len(data['elements'])==len(original['elements']),id
            wrapper=sha(assets/'models/item'/f'{id}.json')
            with contextlib.redirect_stdout(io.StringIO()):
                bb.import_bb(source);bb.import_bb(source);bb.export_wand(assets/'models/item'/f'{id}.json')
                bb.import_bb(projects/'wands'/f'{id}.bbmodel')
            result=load(assets/'models/item/wands/held'/f'{id}.json')
            assert len(result['elements'])==len(original['elements']),id
            assert result['tnc_grip']==original['tnc_grip'] and result['tnc_tip']==original['tnc_tip'],id
            assert result.get('ambientocclusion')==original.get('ambientocclusion'),id
            assert result['display']==original['display'],id
            for before, after in zip(original['elements'],result['elements']):
                assert before['from']==after['from'] and before['to']==after['to'],id
                for face in before['faces']:
                    assert all(math.isclose(x,y,abs_tol=1e-8) for x,y in zip(before['faces'][face]['uv'],after['faces'][face]['uv'])),id
            assert sha(assets/'models/item'/f'{id}.json')==wrapper,id+' inventory wrapper must remain untouched'
            assert sha(assets/'textures/item/wands/held'/f'{id}.png')==sha(png),id
        for record in manifest['armor']:
            id=record['id'];source=S/'armor'/f'{id}.bbmodel';data=load(source);original=load(A/'models/armor'/f'{id}.json')
            assert base64.b64decode(data['textures'][0]['source'].split(',',1)[1])==(A/'textures/models/armor'/f'{id}.png').read_bytes(),id
            with contextlib.redirect_stdout(io.StringIO()):
                bb.import_bb(source);bb.export_armor(id,load(assets/'models/armor'/f'{id}.json')['parts']);bb.import_bb(projects/'armor'/f'{id}.bbmodel')
            result=load(assets/'models/armor'/f'{id}.json');parts={p['name']:p for p in result['parts']}
            assert result['texture_width']==original['texture_width'] and result['texture_height']==original['texture_height'],id
            assert len(parts)==len(original['parts']),id
            for part in original['parts']:
                actual=parts[part['name']];assert actual['parent']==part['parent'],id
                for key in ['pivot','rotation']:assert all(math.isclose(x,y,abs_tol=1e-8) for x,y in zip(part[key],actual[key])),id
                assert len(actual['boxes'])==len(part['boxes']),id
                for before,after in zip(part['boxes'],actual['boxes']):
                    for key in ['uv','position','size']:assert all(math.isclose(x,y,abs_tol=1e-8) for x,y in zip(before[key],after[key])),id
    print('PASS: 36 wand and 34 wearable Blockbench round-trips; 74 approved icons unchanged.')

if __name__=='__main__':main()
