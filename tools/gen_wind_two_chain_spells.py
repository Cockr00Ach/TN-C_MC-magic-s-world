"""Generate the two replacement wind chains; retire only five obsolete summon overrides."""
from pathlib import Path
import json,shutil
root=Path(__file__).resolve().parents[1]
old=['wind_orb','wind_orb_swarm','wind_spirit','triple_wind_spirit','wind_spirit_lord']
names=[['风球','大风球','压缩风球','风压核心','升空风暴'],['风之三剑','十剑齐发','破甲风矛','天降风兵','风神兵临']]
# Authoritative new spell definitions live in the jar. Retire all pack overrides of the old chain.
pack=next(x for x in (root/'modpack').iterdir() if x.is_dir() and x.name!='archive')
for name in old:
 p=pack/'kubejs/data/tnc/spells'/f'{name}.json'
 if p.exists():p.unlink()
for c,chain in enumerate(['ball','weapon']):
 for t in range(1,6):
  name=f'wind_{chain}_{t}'
  data={'school':'AIR','group':'wind_'+chain,'range':96 if chain=='weapon' else 48,'learn':{'tier':t},'cast':{'duration':.6+t*.12,'animation':'spell_engine:one_handed_area_charge','sound':{'id':'spell_engine:generic_wind_charging'},'particles':[{'particle_id':'minecraft:cloud','shape':'CIRCLE','origin':'FEET','count':5,'min_speed':.03,'max_speed':.12}]},'release':{'target':{'type':'SELF'},'animation':'spell_engine:one_handed_area_release','sound':{'id':'minecraft:entity.player.attack.sweep'}},'impact':[{'action':{'type':'STATUS_EFFECT','status_effect':{'effect_id':f'tnc:wind_cast_{c}_{t}','duration':.05,'amplifier':0,'apply_mode':'SET','show_particles':False}}}],'cost':{'exhaust':0,'cooldown_duration':[4,6,10,14,20][t-1]}}
  p=root/'src/main/resources/data/tnc/spells'/f'{name}.json';p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
  icon=old[min(t-1,1)] if c==0 else ['gale','great_gale','vast_gale','giant_gale','wind_god_gale'][t-1]
  dest=root/'src/main/resources/assets/tnc/textures/spell'/f'{name}.png';shutil.copyfile(dest.parent/(icon+'.png'),dest)
p=root/'src/main/resources/data/tnc/spell_pools/tnc_wind.json';ids=['wind_field','wind_speed','greater_wind_speed','super_wind_speed','wind_god_descent','gale','great_gale','vast_gale','giant_gale','wind_god_gale']+[f'wind_{c}_{t}' for c in ['ball','weapon'] for t in range(1,6)];p.write_text(json.dumps({'spell_ids':['tnc:'+n for n in ids]},indent=2)+'\n')
# Only add our language keys; existing unrelated translations remain intact.
for lang in ['zh_cn','en_us']:
 p=root/f'src/main/resources/assets/tnc/lang/{lang}.json';data=json.loads(p.read_text(encoding='utf-8-sig'))
 for c,chain in enumerate(['ball','weapon']):
  for t in range(1,6):
   n=f'wind_{chain}_{t}';data[f'spell.tnc.{n}.name']=names[c][t-1] if lang=='zh_cn' else f'Wind {chain.title()} {t}'
   desc=([f'释放风球吸引敌人并持续造成伤害。', '更大的风球，吸引范围与伤害提高。','命中后吸引敌人，持续压缩风球并引发爆炸。','更大的压缩风球与更强的爆炸。','压缩时持续升空，将敌人吸至高空后爆炸。'] if c==0 else ['幻化三柄放大两倍的风翼圣剑，环绕目标多圈突刺后升空砸下。','幻化十柄放大三倍的圣剑，环绕目标攻击后砸下。','幻化五柄放大五倍的长矛，环绕目标突刺后砸下，施加十秒易伤。','二十剑与十矛放大五倍，围绕目标攻击多圈后升空砸下。','三剑两矛依次从脚下法阵升空，再陆续砸下；模型为普通兵器六十倍。'])[t-1]
   data[f'spell.tnc.{n}.description']=desc if lang=='zh_cn' else (["Launch a pulling wind orb.","Larger, stronger pulling orb.","Compress caught enemies, then detonate.","A larger compressing wind core.","Lift caught enemies while compressing, then detonate."][t-1] if c==0 else ["3 double-size blades orbit and lunge, then plunge.","10 triple-size blades orbit and lunge, then plunge.","5 fivefold spears orbit and plunge, applying vulnerability.","20 swords and 10 spears at fivefold scale orbit before plunging.","3 swords and 2 spears at sixtyfold scale rise from a seal, then plunge sequentially."][t-1])
 p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Generated replacement wind spells, icons, labels and pool.')
