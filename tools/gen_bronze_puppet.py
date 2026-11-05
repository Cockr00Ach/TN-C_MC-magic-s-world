"""Generate the inseparable twinblades and bronze guardian; retain legacy item IDs."""
import json
from bronze_art_detail import ROOT,RES,ASSET,write,weapon,puppet_body,puppet_preview

def make_halberd():
    write(ASSET/'models/item/bronze_halberd.json',weapon('halberd'))
    write(RES/'data/tnc/recipes/bronze_halberd.json',{'type':'tnc:bronze_fusion','category':'equipment'})
    for name in ('longswords','infusible_weapons'):
        p=RES/f'data/tnc/tags/items/{name}.json';data=json.loads(p.read_text(encoding='utf-8'))
        if 'tnc:bronze_halberd' not in data['values']:data['values'].append('tnc:bronze_halberd')
        write(p,data)
    p=RES/'data/tnc/tags/items/spears.json';data=json.loads(p.read_text(encoding='utf-8'))
    data['values']=[value for value in data['values'] if value!='tnc:bronze_halberd'];write(p,data)

def make_body():return puppet_body()
def preview(parts):return puppet_preview()

def update_language():
    for lang in ('zh_cn','en_us'):
        p=ASSET/f'lang/{lang}.json';data=json.loads(p.read_text(encoding='utf-8'))
        entries={'item.tnc.bronze_halberd':('青铜双刃','Bronze Twinblades'),'entity.tnc.bronze_puppet':('青铜人偶','Bronze Puppet'),
            'tooltip.tnc.bronze_twin':('成套双持：副手有物品时无法使用','Paired weapon: requires an empty offhand'),
            'message.tnc.bronze_twin_offhand':('青铜双刃需要副手为空，且必须放在主手','Bronze Twinblades require an empty offhand and the main-hand slot'),
            'tooltip.tnc.bronze_summon':('潜行＋右键：召唤青铜人偶（最多10分钟）','Sneak + use: summon bronze puppet (up to 10 minutes)'),
            'message.tnc.puppet_exists':('你的青铜人偶仍然存在','Your bronze puppet is still present'),
            'message.tnc.puppet_cooldown':('人偶召唤冷却：剩余 %s 秒','Puppet summon cooldown: %s seconds'),
            'message.tnc.puppet_no_space':('周围没有足够空间召唤人偶','Not enough space to summon the puppet'),
            'message.tnc.puppet_summoned':('青铜人偶已召唤，最多存在10分钟','Bronze puppet summoned for up to 10 minutes')}
        for key,value in entries.items():data[key]=value[0 if lang=='zh_cn' else 1]
        write(p,data)

def main():
    make_halberd();parts=make_body();update_language()
    preview(parts)

if __name__=='__main__':main()
