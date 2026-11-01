"""Overlay the verified ink package onto the latest installed mod, preserving all other entries."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest().upper()

def selected(name):
    return name.startswith(('com/tnc/tnc/world/ink/','assets/tnc/shaders/core/ink_dragon.','assets/tnc/shaders/program/ink_grayscale.')) or name in (
        'tnc.ink.mixins.json','assets/tnc/shaders/post/ink_grayscale.json',
        'assets/tnc/textures/mob_effect/ink_dragon_dread.png',
        'assets/tnc/textures/environment/ink_shanshui_atlas.png',
        'assets/tnc/textures/entity/ink_winged_tiger.png',
        'assets/tnc/textures/entity/ink_vortex_seal.png',
        'assets/tnc/meshes/ink_celestial_dragon.mesh',
        'assets/tnc/geo/entity/ink_winged_tiger.geo.json','assets/tnc/animations/entity/ink_winged_tiger.animation.json',
        'assets/tnc/lang/zh_cn.json','assets/tnc/lang/en_us.json')

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('base');parser.add_argument('update');parser.add_argument('output');parser.add_argument('report')
    parser.add_argument('--three-forms',action='store_true',help='Include painting gate, recipe, translations and the dungeon projectile registry collision fix')
    parser.add_argument('--boss-audio',action='store_true',help='Include normalized songs, cries and merge only ink sound events')
    args=parser.parse_args()
    extras={'assets/tnc/models/item/ink_portal.json','data/tnc/recipes/ink_portal.json',
            'com/tnc/tnc/dungeon/DungeonContent.class'} if args.three_forms else set()
    if args.boss_audio:extras.update({'assets/tnc/sounds.json','assets/tnc/ink_boss_audio.json','assets/tnc/ink_audio_credits.json'})
    if args.three_forms:extras.add('META-INF/MANIFEST.MF')
    def included(name):return selected(name) or name in extras or args.boss_audio and name.startswith(('assets/tnc/sounds/music/ink/','assets/tnc/sounds/ink/'))
    base_hash=digest(args.base)
    with ZipFile(args.base) as base,ZipFile(args.update) as update:
        assert not any(n.startswith('META-INF/') and n.endswith(('.SF','.RSA','.DSA')) for n in base.namelist()),'Signed base needs a different release workflow'
        patches={n:update.read(n) for n in update.namelist() if included(n) and not n.endswith('/')}
        if args.three_forms:
            # Preserve every installed manifest attribute; append only our narrowly scoped mixin config.
            original=base.read('META-INF/MANIFEST.MF').decode('utf-8').replace('\r\n','\n')
            import re
            original=re.sub(r'\n ', '', original)
            lines=original.splitlines();found=False
            for index,line in enumerate(lines):
                if line.startswith('MixinConfigs:'):
                    names=[v.strip() for v in line.split(':',1)[1].split(',') if v.strip()]
                    if 'tnc.ink.mixins.json' not in names:names.append('tnc.ink.mixins.json')
                    lines[index]='MixinConfigs: '+','.join(names);found=True
            if not found:lines.insert(1,'MixinConfigs: tnc.ink.mixins.json')
            # Manifest physical lines must be folded, continuation lines begin with a single space.
            encoded=[]
            for line in lines:
                data=line.encode('utf-8')
                while len(data)>70:
                    cut=70
                    while cut>0 and data[cut]&0xC0==0x80:cut-=1
                    encoded.append(data[:cut]);data=b' '+data[cut:]
                encoded.append(data)
            patches['META-INF/MANIFEST.MF']=b'\r\n'.join(encoded).rstrip(b'\r\n')+b'\r\n\r\n'
        if args.three_forms:
            assert all(n in patches for n in extras),'Incomplete three-form release'
            for name in ('com/tnc/tnc/world/ink/InkEncounter.class','com/tnc/tnc/world/ink/InkPortalEntity.class',
                         'com/tnc/tnc/world/ink/client/InkEncounterCamera.class'):
                assert name in patches,f'Missing three-form implementation: {name}'
            # Do not introduce a new unrelated registry into an older installed base.
            if 'com/tnc/tnc/dungeon/DungeonContent.class' not in base.namelist():
                patches.pop('com/tnc/tnc/dungeon/DungeonContent.class')
        for language in ('zh_cn','en_us'):
            name=f'assets/tnc/lang/{language}.json'
            if name in patches:
                original=json.loads(base.read(name))
                merged=dict(original)
                changed={'entity.tnc.ink_winged_tiger','entity.tnc.ink_celestial_dragon','effect.tnc.ink_dragon_dread'}
                if args.three_forms:changed.update({'entity.tnc.ink_portal','item.tnc.ink_portal'})
                names=json.loads(patches[name])
                if args.boss_audio:changed.update(k for k in names if k.startswith('subtitles.tnc.ink.'))
                for key in changed:merged[key]=names[key]
                assert all(merged[k]==v for k,v in original.items() if k not in changed)
                patches[name]=(json.dumps(merged,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
        if args.boss_audio:
            name='assets/tnc/sounds.json';original=json.loads(base.read(name));new=json.loads(patches[name]);merged=dict(original)
            additions={k:v for k,v in new.items() if k.startswith(('music.ink.','ink.crane.','ink.tiger.','ink.dragon.'))}
            assert len(additions)==6,'Three songs and three cast events required'
            merged.update(additions);assert all(merged[k]==v for k,v in original.items() if k not in additions)
            patches[name]=(json.dumps(merged,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
        assert 'assets/tnc/textures/environment/ink_shanshui_atlas.png' in patches
        assert 'com/tnc/tnc/world/ink/client/InkPaintingRenderer.class' in patches
        written=set()
        with ZipFile(args.output,'w',compression=ZIP_DEFLATED) as out:
            for entry in base.infolist():
                if entry.filename in written:continue
                out.writestr(copy.copy(entry),patches.get(entry.filename,base.read(entry.filename)))
                written.add(entry.filename)
            for name,data in patches.items():
                if name not in written:out.writestr(name,data)
        unchanged=0
        with ZipFile(args.output) as merged:
            assert merged.testzip() is None
            for name in base.namelist():
                if not included(name):
                    assert base.read(name)==merged.read(name),f'Unrelated entry changed: {name}'
                    unchanged+=1
            for name,data in patches.items():assert merged.read(name)==data
    assert digest(args.base)==base_hash,'Installed base changed during merge; retry against latest'
    report={'base':str(Path(args.base).resolve()),'baseSHA256':base_hash,'outputSHA256':digest(args.output),
            'inkEntriesUpdated':len(patches),'unrelatedEntriesByteIdentical':unchanged,
            'scope':'Three-form ink encounter, painting gate and dungeon projectile ID collision fix; other entries preserved' if args.three_forms else 'Ink entity package and art assets; language files only update tiger and celestial-dragon names'}
    Path(args.report).write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
