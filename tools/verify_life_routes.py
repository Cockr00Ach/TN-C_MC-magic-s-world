"""Check authored chapters, dependencies, action IDs and atlas references."""
from pathlib import Path
import json,hashlib
R=Path(__file__).resolve().parents[1];S=R/'src/main/resources';Q=R/'questbook/ftbquests'
FILES=['tnc_field_02_botany.snbt','tnc_field_03_pasture.snbt','tnc_field_04_workshop.snbt','tnc_field_05_seasons.snbt']
def check_signed_ids(value,key=''):
 if isinstance(value,dict):
  for field,child in value.items():check_signed_ids(child,field)
 elif isinstance(value,list):
  for child in value:check_signed_ids(child,key)
 elif isinstance(value,str) and key in ('id','linked_quest','dependencies'):
  assert len(value)==16 and 0<int(value,16)<=0x7FFFFFFFFFFFFFFF, f'FTB signed-hex overflow at {key}: {value}'

def run(check_mirror=False):
 ids=set();quests=set();deps=[];links=[];tasks=0;chapters=[]
 for name in FILES:
  c=json.loads((Q/'chapters'/name).read_text(encoding='utf8'))
  check_signed_ids(c)
  assert c['group']=='544E434C49464531';chapters.append(c)
  for q in c['quests']:
   assert q['id'] not in ids;ids.add(q['id']);quests.add(q['id']);deps+=q.get('dependencies',[])
   for t in q['tasks']:
    assert t['id'] not in ids;ids.add(t['id']);tasks+=1
    if t['type']=='advancement':assert (S/'data/tnc/advancements'/ (t['advancement'].split(':')[1]+'.json')).is_file(),t
    if t['type']=='item' and t['item'].startswith('tnc:'):assert (S/'assets/tnc/models/item'/(t['item'].split(':')[1]+'.json')).is_file(),t
   icon=q.get('icon','')
   if icon.startswith('tnc:'):assert (S/'assets/tnc/models/item'/(icon.split(':')[1]+'.json')).is_file(),icon
  for link in c.get('quest_links',[]):assert link['id'] not in ids;ids.add(link['id']);links.append(link['linked_quest'])
 assert set(deps+links)<=quests
 manifest=json.loads((S/'data/tnc/life_routes/manifest.json').read_text(encoding='utf8'))
 catalogue=json.loads((S/'data/tnc/life_routes/catalogue.json').read_text(encoding='utf8'))
 assert len(catalogue['magic'])==38 and len(catalogue['animals'])==26
 assert len(manifest['machines'])==28
 if check_mirror:
  for name in FILES:
   mirror=R/'modpack/元素觉醒1.4.3-魔改版-20260915/config/ftbquests/quests/chapters'/name
   assert mirror.read_bytes()==(Q/'chapters'/name).read_bytes()
 for texture in ['block/magic_soil_atlas','block/route_plants','block/route_goods','guide/seasons-banner']:
  assert (S/'assets/tnc/textures'/(texture+'.png')).is_file()
 result={'chapters':4,'quests':len(quests),'tasks':tasks,'season_links':len(links),'traditional_entries':len(catalogue['traditional']),'magic_plants':38,'beasts':26,'network_blocks':28,'ftb_signed_hex_compatible':True,'failures':[]}
 (R/'work').mkdir(exist_ok=True);(R/'work/life-routes-data-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8');print(result)
if __name__=='__main__':
 import sys
 run(check_mirror='--mirror' in sys.argv)
