"""Append one independent fieldwork chapter; never rewrite existing chapters/IDs."""
from pathlib import Path
import json,hashlib
ROOT=Path(__file__).resolve().parents[1]
def ident(s):return f"{int(hashlib.sha256(('tnc:sky_academy:'+s).encode()).hexdigest()[:16],16)&0x7fffffffffffffff:016X}"
lessons=[
 ('arrival','云上初见','minecraft:flowering_azalea','抵达天空岛后观看可跳过的短镜头。随后自由逛镇，向Self和艾琳了解服务。电影播放不能替代初次命名。'),
 ('stargaze','今夜，看看世界的另一面','minecraft:spyglass','夜晚前往北方石山找观星人瑟兰。对话后先看普通夜景，再亲自按F8，观察魔脉从草木、湖水汇入星空。白天可以先备好远行物资，夜里回来；不强制全服跳时。'),
 ('wind','地下城之前：无形的路','minecraft:bell','沿观星山径去听风鞍部找岚。看一阵风穿过三座悬铃的刻纹。轮到你时打开魔力视野，留意风先后经过哪里，再按这个次序右键触碰风铃。听漏了可以等下一阵风。'),
 ('farm','田庄里的两种呼吸','tnc:dew_collector','到岛西南的露汀牧圃找园丁莉禾。观察风帆花和跃泉蹄兽，在花旁放置集露盏，再用牧务杖选中蹄兽并绑定集息器。让两个收集器都收到魔力。'),
 ('workshop','第一盏属于你的魔力灯','tnc:mana_lantern','沿南门出城，到灯渠找诺恩。沿储池、晶纤和灯观察实物回路，亲手补上缺口，等魔力传到灯中。完成后继续《魔导纪行》。')]
chapter={'id':ident('chapter'),'filename':'tnc_sky_academy','title':'&b云溪纪行 · 居民实地课堂','icon':'minecraft:spyglass','group':'544E434C49464531','order_index':-10,'default_hide_dependency_lines':True,'quests':[],'images':[]}
for i,(key,title,icon,desc) in enumerate(lessons):
 chapter['quests'].append({'id':ident(key),'title':title,'icon':icon,'x':0.,'y':float(i*3),'size':1.6,'description':[desc,'使用 /skylesson 查看下一站导师与实时坐标。右键导师可以重听；跳过动画不会自动完成实操。','每人独立保存进度；镜湖本轮仅建设景观，尚未加入钓鱼系统。'],'dependencies':[] if i==0 else [ident(lessons[i-1][0])],'tasks':[{'id':ident(key+'/task'),'type':'advancement','advancement':'tnc:onboarding/academy_'+key}],'rewards':[]})
 if key == 'arrival':
  chapter['quests'][-1]['description'].insert(0,'新建世界从独立云溪界的天空岛抵达花坡开始。外出冒险时使用岛上原有传送门前往主世界；主世界对应传送门可返回。岛下玄潮魔海与农田使用的流动魔力不同。')
 path=ROOT/'src/main/resources/data/tnc/advancements/onboarding'/('academy_'+key+'.json');path.parent.mkdir(parents=True,exist_ok=True)
 path.write_text(json.dumps({'criteria':{'performed':{'trigger':'minecraft:impossible'}}},indent=2)+'\n','utf-8')
for folder in [ROOT/'questbook/ftbquests/chapters',ROOT/'modpack/元素觉醒1.4.3-魔改版-20260915/config/ftbquests/quests/chapters']:
 folder.mkdir(parents=True,exist_ok=True);(folder/'tnc_sky_academy.snbt').write_text(json.dumps(chapter,ensure_ascii=False,indent=2)+'\n','utf-8')
print('Added 5 actual-event quests; existing chapters unchanged')
