"""Generate the shipped bronze models, textures, recipes and faithful offline preview."""
import json
from bronze_art_detail import ROOT,RES,ASSET,write,weapon as build,weapon_atlas as atlas,weapon_preview as preview
NAMES={'greatsword':'古铜大剑','spear':'古铜长枪','dao':'古铜大刀','dagger':'古铜匕首'}

def main():
    atlas()
    patterns={'greatsword':['CCC','CIC','LSL'],'spear':[' IC',' SC','SL '],'dao':[' CC',' IC',' SL'],'dagger':[' C ',' I ',' SL']}
    for kind in NAMES:
        write(ASSET/f'models/item/bronze_{kind}.json',build(kind))
        write(RES/f'data/tnc/recipes/bronze_{kind}.json',{'type':'minecraft:crafting_shaped','category':'equipment','pattern':patterns[kind],'key':{k:{'item':'minecraft:'+v} for k,v in {'C':'copper_ingot','I':'iron_ingot','S':'stick','L':'leather'}.items()},'result':{'item':f'tnc:bronze_{kind}'}})
        write(RES/f'data/tnc/advancements/recipes/bronze_{kind}.json',{'parent':'minecraft:recipes/root','criteria':{'copper':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:copper_ingot']}]}}},'rewards':{'recipes':[f'tnc:bronze_{kind}']}})
    write(RES/'data/tnc/recipes/bronze_upgrade.json',{'type':'tnc:bronze_upgrade','category':'equipment'})
    for lang in ('zh_cn','en_us'):
        path=ASSET/f'lang/{lang}.json';data=json.loads(path.read_text(encoding='utf-8-sig'))
        for kind,name in NAMES.items():data[f'item.tnc.bronze_{kind}']=name if lang=='zh_cn' else 'Ancient Bronze '+kind.title()
        keys={'bronze_upgrade':('铜铸强化：%s/10（基础攻击 +%s%%）','Copper tempering: %s/10 (+%s%% base damage)'),
            'bronze_combo':('战士连招：沿用 Q / E / Z、解锁与冷却规则','Warrior combos: existing Q / E / Z inputs, unlocks and cooldowns'),
            'bronze_throw':('按住右键蓄力，松开投掷；1.5 秒满蓄，自动回手','Hold use to charge; release to throw. Full charge: 1.5s. Returns automatically.'),
            'bronze_returning':('匕首回收中…','Dagger returning…')}
        for key,value in keys.items():data['tooltip.tnc.'+key]=value[0 if lang=='zh_cn' else 1]
        write(path,data)
    for tag,kinds in {'greatswords':['greatsword'],'spears':['spear'],'longswords':['dao','dagger'],'infusible_weapons':list(NAMES)}.items():
        path=RES/f'data/tnc/tags/items/{tag}.json';data=json.loads(path.read_text(encoding='utf-8')) if path.exists() else {'replace':False,'values':[]}
        for kind in kinds:
            value=f'tnc:bronze_{kind}'
            if value not in data['values']:data['values'].append(value)
        write(path,data)
    preview()

if __name__=='__main__':main()
