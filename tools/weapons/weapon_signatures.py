from pathlib import Path
import json,copy
r=Path(__file__).resolve().parents[2];res=r/'src/main/resources/assets/tnc'
arts=json.loads((res/'warrior/ultimates.json').read_text(encoding='utf-8')); old=json.loads((res/'warrior/traditional_martial_arts.json').read_text(encoding='utf-8')); ult=json.loads((res/'animations/entity/warrior_ultimates.json').read_text(encoding='utf-8'))['animations'];out={}
families={}
for f,tag in [('GREATSWORD','greatswords'),('HAMMER','hammers'),('SPEAR','spears'),('BOW','longbows')]:
 for id in json.loads((r/f'src/main/resources/data/tnc/tags/items/{tag}.json').read_text(encoding='utf-8'))['values']:
  if isinstance(id,str):families[id.removeprefix('tnc:')]=f
maps={'LONGSWORD':['SWORD_BREAKER','SWORD_STANCE','SWORD_COUNTER','SWORD_SPIN','SWORD_RETREAT','SWORD_SWALLOW'],'GREATSWORD':['GS_SMASH','GS_STANCE','GS_WALL','GS_SWEEP','GS_RIFT','GS_ADVANCE'],'HAMMER':['HM_QUAKE','HM_STANCE','HM_CUT','HM_CRUSH','HM_RUIN','HM_DRIVE'],'SPEAR':['SPEAR_BREAK','SPEAR_GUARD','SPEAR_RETURN','SPEAR_DRAGON','SPEAR_FLOW'],'BOW':['BOW_AIM','BOW_PIERCE','BOW_STRAFE','BOW_DASH_SHOT','BOW_BACK_SHOT','BOW_VOLLEY']}
for art in arts:
 id=art['id']
 if id in old or id in ['doom_scythe','longsword','wind_relic_spear']:continue
 source=ult[id+'_ultimate']['bones'];family=families.get(id,'LONGSWORD')
 for slot,anim in enumerate(maps[family]+['SLASH_LEFT','SLASH_RIGHT','SLASH_HORIZONTAL']):
  # Existing defensive clips remain unchanged; offensive clips use individual weapon strokes.
  if slot in [1,2]:continue
  strokes=art['strokes'];phase=strokes[(slot if slot<6 else slot-6)%5];hit=phase['tick'];bones={}
  for bone,data in source.items():
   frames=data['rotation'];v0=[0,0,0];v1=frames[str(round((hit-4)/20,3))];v2=frames[str(round(hit/20,3))];v3=frames[str(round((hit+3)/20,3))]
   bones[bone]={'rotation':{'0':v0,'0.25':v1,'0.48':v2,'0.66':v3,'1':v0}}
  out[id+'_signature_'+anim]={'animation_length':1,'bones':bones}
(res/'animations/entity/warrior_signatures.json').write_text(json.dumps({'format_version':'1.8.0','animations':out},ensure_ascii=False,indent=2),encoding='utf-8')
