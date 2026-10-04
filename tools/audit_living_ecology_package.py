"""Verify packaged ecology resources and collision-free added guide identities."""
from pathlib import Path
from zipfile import ZipFile
import json,re,hashlib

R=Path(__file__).resolve().parents[1]
live=Path('D:/垃圾桶/PCL 正式版 2.9.3/.minecraft/versions/元素觉醒1.4.3-魔改版-20260915')
chapters=live/'config/ftbquests/quests/chapters'
identity=re.compile(r'[\"]?id[\"]?\s*:\s*\"([0-9A-Fa-f]{16})\"')
old_ids=set()
for p in chapters.glob('*.snbt'):old_ids.update(identity.findall(p.read_text(encoding='utf-8-sig')))
new_ids=set();counts={}
for p in sorted((R/'questbook/ftbquests/chapters').glob('tnc_field_0[234]*.snbt')):
    content=p.read_text(encoding='utf-8');d=json.loads(content)
    ids=identity.findall(content)
    assert len(set(ids))==len(ids),f'Duplicate identity within {p.name}'
    assert not set(ids)&old_ids,f'New chapter identity collides with live content: {p.name}'
    assert not set(ids)&new_ids,f'New chapter identity collides with another new chapter: {p.name}'
    new_ids.update(ids);counts[p.name]=len(d['quests'])
    assert all(not q.get('dependencies') and not q.get('rewards') for q in d['quests']), 'Optional guides may not force or reward story progression'
jar=R/'build/libs/tnc-1.0.0.jar'
with ZipFile(jar) as z:
    names=set(z.namelist());parsed=0
    for n in names:
        if n.endswith('.json') and (n.startswith('assets/tnc/') or n.startswith('data/tnc/')):
            json.loads(z.read(n));parsed+=1
    for n in [
        'com/tnc/tnc/life/pasture/PastureAnimal.class',
        'com/tnc/tnc/life/pasture/client/PastureAnimalRenderer.class',
        'com/tnc/tnc/life/pasture/ManaBottleItem.class',
        'com/tnc/tnc/life/pasture/PastureBottleLedger.class',
        'com/tnc/tnc/life/botanical/BotanicalSprite.class',
        'com/tnc/tnc/life/wonders/SkyVineRootEntity.class',
        'com/tnc/tnc/life/wonders/LightBloomData.class',
        'data/tnc/functions/ecology_plant_kit.mcfunction',
        'data/tnc/functions/ecology_animal_kit.mcfunction',
        'data/tnc/functions/ecology_workshop_kit.mcfunction',
    ]:assert n in names,f'Packaged resource missing: {n}'
    assert len([n for n in names if n.startswith('assets/tnc/textures/entity/pasture/') and n.endswith('.png')])==46
report={'sha256':hashlib.sha256(jar.read_bytes()).hexdigest().upper(),'parsed_tnc_json':parsed,'new_chapter_nodes':counts,'new_identity_count':len(new_ids),'existing_id_collision':False,'animal_entity_textures':46}
(R/'work/living-ecology-package-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
