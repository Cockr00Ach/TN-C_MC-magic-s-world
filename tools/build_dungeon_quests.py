"""Add one independent chapter. Never regenerate any existing authored chapter or group."""
from pathlib import Path
import json,hashlib
ROOT=Path(__file__).resolve().parents[1];PACK=ROOT/'modpack/元素觉醒1.4.3-魔改版-20260915'
def ident(key):return f'{int(hashlib.sha256(("tnc-dungeon-v1:"+key).encode()).hexdigest()[:16],16)&0x7fffffffffffffff:016X}'
def text(p,j):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(j,ensure_ascii=False,indent=2)+'\n','utf8')
quests=[]
def quest(key,title,icon,x,y,desc,task,deps=(),reward=None):
    q=dict(id=ident(key),title=title,icon=icon,x=x,y=y,description=desc,dependencies=[ident(k) for k in deps],hide_dependency_lines=False,tasks=[dict(id=ident(key+':task'),**task)])
    if reward:q['rewards']=[dict(id=ident(key+':reward'),type='item',item=reward,count=1)]
    quests.append(q)
quest('guide','&b&l门后的三十层','tnc:dungeon_handbook',0,-3,['先在酒馆原生委托栏潜行右键，领取勘察手册和四座野外界门的寻找指引。','每个主题30层，每5层保存进度；正常退出后可继续原挑战。','进入每层八间随机相连的房间，清场开门。找到下层界碑可提前下层，岔路藏着宝匣和奇遇。'],dict(type='checkmark'),reward='tnc:dungeon_handbook')
quest('entry','&3穿过野外界门','minecraft:ender_eye',0,0,['野外门是传送阵，进入后到独立地下城的安全大厅。','队长在大厅选择已解锁的五层段。队友可在同一大门或各自入口大厅接受邀请：/dungeon invite 玩家；/dungeon accept。','单人每五层段一次倒地自救；多人只能队友救，全队同时倒地才失败。','失败保留装备、背包和经验，仅损失本段未结算遗币20%。银行存款不受影响。'],dict(type='advancement',advancement='tnc:dungeon/entered'),['guide'])
for i,(theme,name) in enumerate([('desert','黄沙遗庭'),('crypt','锁钟地牢'),('tide','沉潮回廊'),('astral','星眠幻庭')]):
    for floor in range(5,31,5):
        key=f'{theme}_{floor}';prev='entry' if floor==5 else f'{theme}_{floor-5}'
        quest(key,f'&e{name} · 第{floor}层',f'tnc:dungeon_gate_{theme}',(i-1.5)*5,(floor//5-1)*3+4,[f'完成{name}第{floor}层的守关试炼。','当前是可游玩的基础守关者，专属Boss模型和机制后续设计。','守关完成会结算遗币，解锁下一个五层段。回酒馆委托栏潜行右键交勘察记录领取铜币。','宝匣里的主题材料和收藏可以带回家；本段祝福只持续到当前五层段结束。'],dict(type='advancement',advancement=f'tnc:dungeon/{key}'),[prev])
for key in ['entered']+[f'{t}_{f}' for t in ['desert','crypt','tide','astral'] for f in range(5,31,5)]:text(ROOT/f'src/main/resources/data/tnc/advancements/dungeon/{key}.json',dict(criteria=dict(done=dict(trigger='minecraft:impossible'))))
chapter=dict(id=ident('chapter'),filename='tnc_dungeons',title='&3&l门后的三十层',icon='tnc:dungeon_handbook',group='46D9BF1B84D709E1',order_index=12,default_hide_dependency_lines=False,default_quest_shape='hexagon',images=[],quests=quests)
text(PACK/'config/ftbquests/quests/chapters/tnc_dungeons.snbt',chapter)
text(ROOT/'questbook/ftbquests/chapters/tnc_dungeons.snbt',chapter)
print('Added one chapter:',len(quests),'quests; existing chapter files unchanged')
if __name__=='__main__':pass
