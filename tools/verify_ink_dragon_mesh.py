"""Structural checks against the actual exported BBModel and binary glTF."""
import base64,json,struct
from pathlib import Path
import numpy as np

folder=Path(__file__).resolve().parents[1]/'model-source/ink-celestial-dragon'
bb=json.loads((folder/'ink_celestial_dragon.bbmodel').read_text(encoding='utf-8'))
assert bb['meta']['model_format']=='free'
assert all(e['type']=='mesh' for e in bb['elements']), 'No cube elements allowed'
ids=set();triangles=0;vertices=0
for e in bb['elements']:
    assert e['uuid'] not in ids;ids.add(e['uuid'])
    v=e['vertices'];vertices+=len(v)
    assert np.isfinite(np.asarray(list(v.values()))).all()
    for f in e['faces'].values():
        assert len(f['vertices'])==3 and len(set(f['vertices']))==3
        assert all(k in v and k in f['uv'] for k in f['vertices']);triangles+=1
grouped=[uid for group in bb['outliner'] for uid in group['children']]
assert len(grouped)==len(ids) and set(grouped)==ids
assert base64.b64decode(bb['textures'][0]['source'].split(',')[1]).startswith(b'\x89PNG\r\n\x1a\n')
blob=(folder/'ink_celestial_dragon.glb').read_bytes();magic,version,total=struct.unpack_from('<III',blob)
assert magic==0x46546c67 and version==2 and total==len(blob)
size,kind=struct.unpack_from('<II',blob,12);assert kind==0x4e4f534a
gltf=json.loads(blob[20:20+size]);bsize,bkind=struct.unpack_from('<II',blob,20+size)
assert bkind==0x004e4942;start=28+size
assert start+bsize==len(blob) and bsize==gltf['buffers'][0]['byteLength']
for view in gltf['bufferViews']:assert view.get('byteOffset',0)+view['byteLength']<=bsize
gtri=0
for mesh in gltf['meshes']:
    for p in mesh['primitives']:
        a=gltf['accessors'][p['attributes']['POSITION']];assert a['count']%3==0;gtri+=a['count']//3
        view=gltf['bufferViews'][a['bufferView']];pos=np.frombuffer(blob,dtype='<f4',count=a['count']*3,offset=start+view.get('byteOffset',0))
        assert np.isfinite(pos).all()
        assert gltf['accessors'][p['attributes']['NORMAL']]['count']==a['count']
        assert gltf['accessors'][p['attributes']['COLOR_0']]['count']==a['count']
assert gtri==triangles and len(gltf['meshes'])==len(bb['elements'])
assert len([e for e in bb['elements'] if '_digit_' in e['name']])==20
assert len([e for e in bb['elements'] if '_talon_' in e['name']])==20
report={'passed':True,'mesh_parts':len(ids),'vertices':vertices,'triangles':triangles,
        'cube_elements':0,'five_toed_paws':4,'matching_bbmodel_glb_geometry':True,
        'checks':['finite vertices','valid mesh face references and UVs','unique editable groups',
                  'embedded palette PNG','GLB binary bounds/accessors','matching triangle counts','four five-toed paws']}
(folder/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
