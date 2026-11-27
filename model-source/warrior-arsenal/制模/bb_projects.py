"""Write editable, embedded Blockbench projects from the resolved hand geometry."""
import base64,io,uuid
from collections import OrderedDict
import numpy as np
from roster import *

def guid(s):return str(uuid.uuid5(uuid.NAMESPACE_URL,'tnc:weapon_polish/'+s))
def project(ident,label,group):
    m=model('tnc:item/'+ident,True); cubes=m['elements'];out=[];maps={};textures=[]
    for e in cubes:
        for f in e['faces'].values():
            if f.get('texture'):
                ref=tex_ref(f['texture'],m['textures'])
                if ref not in maps:
                    im=texture(ref,True);bio=io.BytesIO();im.save(bio,'PNG');n=len(maps);maps[ref]=n
                    textures.append(dict(name=ref.split('/')[-1]+'.png',folder='/'.join(ref.split(':')[1].split('/')[:-1]),
                        id=str(n),namespace='tnc',uv_width=16,uv_height=16,mode='bitmap',internal=True,uuid=guid(ref),
                        source='data:image/png;base64,'+base64.b64encode(bio.getvalue()).decode('ascii')))
    side_names=['north','east','south','west','up','down']
    for i,e in enumerate(cubes):
        r=e.get('rotation',{});rot=[0,0,0]
        if r:rot['xyz'.index(r['axis'])]=r['angle']
        faces={}
        for s in side_names:
            f=e['faces'].get(s)
            if not f or 'texture' not in f:faces[s]={'uv':[0,0,0,0],'texture':None};continue
            draw=f.copy();draw['texture']=maps[tex_ref(draw['texture'],m['textures'])]
            if ident in ('bronze_halberd','bronze_twin_main','bronze_twin_off') and s not in ('up','down'):
                # The twin's runtime reader maps side-face vertices opposite to the vanilla codec.
                a,b,c,d=draw['uv'];draw['uv']=[c,d,a,b]
            faces[s]=draw
        out.append(dict(type='cube',uuid=guid(ident+'/'+str(i)),name=e.get('name','部件 '+str(i+1)),box_uv=False,
            shade=e.get('shade',True),autouv=0,origin=r.get('origin',list((np.array(e['from'])+e['to'])/2)),
            rotation=rot,rescale=r.get('rescale',False),faces=faces,**{'from':e['from'],'to':e['to']}))
    display=m.get('display',{});tree=OrderedDict()
    for e in out:
        key=e['name'].replace('_',' ').rsplit(' ',1)[0].strip()
        tree.setdefault(key,[]).append(e['uuid'])
    # Keep visual parts with an authored repeated name together; single components stay directly editable.
    groups=[];outline=[]
    for k,ids in tree.items():
        if len(ids)<3:outline.extend(ids);continue
        u=guid(ident+'/group/'+k);groups.append(dict(name=k,uuid=u,origin=[8,8,8],rotation=[0,0,0]));outline.append(dict(uuid=u,isOpen=False,children=ids))
    bb=dict(meta={'format_version':'5.0','model_format':'java_block','box_uv':False},name=label,java_block_version='1.9.0',resolution={'width':16,'height':16},
        ambientocclusion=m.get('ambientocclusion',True),front_gui_light=m.get('gui_light')=='front',display=display,textures=textures,
        elements=out,groups=groups,outliner=outline,unhandled_root_fields={k:v for k,v in m.items() if k.startswith('tnc_')})
    if m.get('overrides'):bb['overrides']=m['overrides']
    if m.get('parent')=='builtin/entity':bb['parent']=m['parent']
    path=ROOT/'Blockbench工程'/group/(label+'.bbmodel')
    write_json(path,bb);return path

def projects():
    results=[]
    for tag,ident in all_ids():
        label=file_name(ident);group=FAMILIES[tag];results.append(project(ident,label,group))
        if tag=='longbows':
            for i in range(3):results.append(project(ident+'_pulling_'+str(i),label+' · 拉弦'+str(i+1),group+'/拉弦'))
        if ident=='bronze_halberd':
            for key,name in [('bronze_twin_main','主刃'),('bronze_twin_off','副刃')]:
                results.append(project(key,label+' · '+name,group+'/双持'))
    return results
if __name__=='__main__':print(len(projects()),'editable projects')
