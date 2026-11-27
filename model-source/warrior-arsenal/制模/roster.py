"""Resolve production and pack models, including aliases and Forge perspective wrappers."""
from pathlib import Path
from functools import lru_cache
import json,os
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
PACK=ROOT/'资源包'
REPO=Path(os.environ.get('TNC_WEAPON_REPO',Path.home()/'.codex/worktrees/skyhaven-team-sync/TN-C_MC-magic-s-world'))
RES=REPO/'src/main/resources'
FAMILIES={'longswords':'长剑','greatswords':'重剑','hammers':'战锤','spears':'长枪','longbows':'战弓'}
ZH=json.loads((RES/'assets/tnc/lang/zh_cn.json').read_bytes())

def all_ids():
    for tag in FAMILIES:
        for rid in json.loads((RES/'data/tnc/tags/items'/(tag+'.json')).read_bytes())['values']:
            if isinstance(rid,str) and rid.startswith('tnc:'):yield tag,rid[4:]

def loc(ref,area,suffix):
    ns,path=(ref.split(':',1) if ':' in ref else ('minecraft',ref))
    return Path('assets')/ns/area/(path+suffix)

def source(ref,area,suffix,patched):
    rel=loc(ref,area,suffix)
    if patched and (PACK/rel).is_file():return PACK/rel
    return RES/rel

@lru_cache(maxsize=320)
def model(ref,patched=True,context="firstperson_righthand"):
    if ref.startswith(('minecraft:','builtin/')) or ref in ('item/handheld','item/generated'):return {'parent':ref}
    return resolve(json.loads(source(ref,'models','.json',patched).read_bytes()),patched,context)

def resolve(data,patched,context):
    out={}
    parent=data.get('parent','')
    if parent.startswith('tnc:'):out=dict(model(parent,patched,context))
    if data.get('loader')=='forge:separate_transforms':
        child=data.get('perspectives',{}).get(context,data['base'])
        out={**out,**(model(child,patched,context) if isinstance(child,str) else resolve(child,patched,context))}
    prev_textures=out.get('textures',{});prev_display=out.get('display',{})
    out.update(data)
    out['textures']={**prev_textures,**data.get('textures',{})}
    out['display']={**prev_display,**data.get('display',{})}
    return out

def tex_ref(name,textures):
    seen=set()
    while name.startswith('#'):
        assert name not in seen, name
        seen.add(name);name=textures[name[1:]]
    return name

@lru_cache(maxsize=220)
def texture(ref,patched):
    with Image.open(source(ref,'textures','.png',patched)) as im:return im.convert('RGBA')

def file_name(ident):return ZH.get('item.tnc.'+ident,ident)

def write_json(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':')),encoding='utf8')
