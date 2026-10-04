"""Export exact current worn geometry and editable wand projects; import author's edits."""
import argparse,base64,json,math,uuid,re
from pathlib import Path
from PIL import Image
R=Path(__file__).resolve().parents[1];AS=R/"src/main/resources/assets/tnc";OUT=R/"model-source/equipment"
def uid(name):return str(uuid.uuid5(uuid.NAMESPACE_URL,"tnc-equipment/"+name))
def write(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+"\n",encoding="utf8")
def tex(p,size):
    return {"id":"0","name":p.name,"path":str(p),"width":size[0],"height":size[1],"uv_width":size[0],"uv_height":size[1],"source":"data:image/png;base64,"+base64.b64encode(p.read_bytes()).decode()}
def armor(style,tier,hat):
    parts=[dict(name=n,parent="root",pivot=[0,0,0],rotation=[0,0,0],boxes=[]) for n in ("head","hat","body","right_arm","left_arm","right_leg","left_leg")]
    def part(n,parent="root",pivot=(0,0,0),rot=(0,0,0)):
        q=next((x for x in parts if x["name"]==n),None)
        if q is None:q=dict(name=n,parent=parent,pivot=list(pivot),rotation=list(rot),boxes=[]);parts.append(q)
        else:q.update(parent=parent,pivot=list(pivot),rotation=list(rot))
        return n
    def box(n,u,v,x,y,z,w,h,d,f=0):next(p for p in parts if p["name"]==n)["boxes"].append(dict(uv=[u,v],position=[x,y,z],size=[w,h,d],inflate=f))
    heavy=style=="bastion";chain=style=="runic";light=style=="wanderer";god=style=="divine"
    if hat:
        if heavy:
            part("brim","head");box("brim",0,0,-7,-9,-7,14,1,14)
            part("crown","head");box("crown",0,20,-4,-13,-4,8,4,8,.25)
            if tier>=3:part("crest","head");box("crest",40,0,-1,-16,-4,2,3,7)
        elif chain:
            part("ring","head");box("ring",0,20,-4.5,-10,-4.5,9,3,9)
            part("top","head");box("top",0,0,-3.5,-12,-3.5,7,2,7)
            if tier>=3:part("gem","head");box("gem",44,0,-1,-11,-5.5,2,2,1)
        elif light:
            for n,uv,pos,size,f in [
                ("hood_top",(0,0),(-4.7,-9,-4.7),(9.4,2,9.4),0),("hood_left",(0,24),(-4.7,-7,-4.2),(1.2,8,8.8),0),
                ("hood_right",(0,24),(3.5,-7,-4.2),(1.2,8,8.8),0),("hood_back",(32,32),(-3.5,-7,3.7),(7,8,1),0),("neck",(0,24),(-4,0,-3),(8,3,6),.3)]:
                part(n,"head");box(n,*uv,*pos,*size,f)
        else:
            for n,uv,pos,size in [("brim",(0,0),(-6.5,-9,-6.5),(13,1,13)),("cone_base",(0,18),(-4,-12,-4),(8,3,8)),("cone_middle",(0,32),(-3,-16,-3),(6,4,6)),("cone_tip",(28,32),(-1.5,-20,-1.5),(3,4,3))]:
                part(n,"head",rot=(0,0,.12) if n=="cone_tip" else (0,0,0));box(n,*uv,*pos,*size)
            if god:part("halo","head");box("halo",32,0,-6,-14,-.5,12,1,1)
    else:
        box("body",16,16,-4,0,-2,8,12,4,.65)
        part("collar","body");box("collar",0,32,-4,-1,-2,8,3,4,1)
        if heavy or chain:part("breastplate","body");box("breastplate",36,16,-3,2,-3,6,7,1,.2)
        if light:part("scarf","body");box("scarf",0,40,-5,1,-3,10,3,6,.1)
        if god:part("relic_seal","body");box("relic_seal",48,0,-2,3,-3,4,4,1)
        for left in (False,True):
            arm="left_arm" if left else "right_arm";leg="left_leg" if left else "right_leg"
            part(arm,pivot=(5 if left else -5,2,0));box(arm,40,16,-1 if left else -3,-2,-2,4,12,4,.55)
            if heavy or tier>=3:
                n=arm+"_shoulder";part(n,arm);box(n,32,36,-1.5 if left else -3.5,-3,-2.5,5,4,5,.25)
            part(leg,pivot=(1.9 if left else -1.9,12,0));box(leg,0,16,-2,0,-2,4,12,4,.5)
            n=leg+"_coat_hem";part(n,leg);box(n,0,44,-2.6,-1,-2.7,5.2,6 if light else 8 if chain else 11,5.4)
            n=leg+"_boot";part(n,leg);box(n,32,48,-2,8,-3,4,4,5,.65)
    return parts
def export_armor(id,parts):
    groups={};abspos={};elements=[]
    # Original parent order is not necessarily the bone traversal order.
    pending=list(parts)
    while pending:
        progress=False
        for p in pending[:]:
            if p["parent"]!="root" and p["parent"] not in groups:continue
            parent=abspos.get(p["parent"],[0,0,0]);absolute=[a+b for a,b in zip(parent,p["pivot"])];abspos[p["name"]]=absolute
            origin=[absolute[0],24-absolute[1],absolute[2]]
            g=dict(name=p["name"],origin=origin,rotation=[-math.degrees(p["rotation"][0]),math.degrees(p["rotation"][1]),-math.degrees(p["rotation"][2])],uuid=uid(id+"/"+p["name"]),children=[])
            groups[p["name"]]=g
            if p["parent"]!="root":groups[p["parent"]]["children"].append(g)
            for i,b in enumerate(p["boxes"]):
                x,y,z=[a+c for a,c in zip(absolute,b["position"])];w,h,d=b["size"]
                e=dict(name=p["name"]+"_"+str(i),uuid=uid(id+"/"+p["name"]+"/"+str(i)),type="cube",box_uv=True,uv_offset=b["uv"],inflate=b["inflate"],origin=origin,**{"from":[x,24-y-h,z],"to":[x+w,24-y,z+d]},faces={f:{"texture":0,"uv":[0,0,0,0]} for f in ("north","south","east","west","up","down")})
                elements.append(e);g["children"].append(e["uuid"])
            pending.remove(p);progress=True
        assert progress,"cyclic bones"
    texture=AS/"textures/models/armor"/(id+".png")
    d=dict(meta={"format_version":"4.10","model_format":"modded_entity","box_uv":True},name=id,resolution={"width":64,"height":64},elements=elements,outliner=[groups[p["name"]] for p in parts if p["parent"]=="root"],textures=[tex(texture,(64,64))])
    write(OUT/"armor"/(id+".bbmodel"),d);write(AS/"models/armor"/(id+".json"),{"parts":parts,"texture_width":64,"texture_height":64})
def export_wand(p):
    original=json.loads(p.read_text(encoding="utf8"));location=original["textures"]["layer0"].split(":")[-1]
    texture=AS/"textures"/(location+".png");im=Image.open(texture).convert("RGBA");w,h=im.size;elements=[]
    for y in range(h):
        x=0
        while x<w:
            if im.getpixel((x,y))[3]==0:x+=1;continue
            start=x
            while x<w and im.getpixel((x,y))[3]>0:x+=1
            elements.append(dict(name="pixel_"+str(y)+"_"+str(start),uuid=uid(p.stem+"/"+str(y)+"/"+str(start)),type="cube",box_uv=False,**{"from":[start*16/w,16-(y+1)*16/h,7.5],"to":[x*16/w,16-y*16/h,8.5]},faces={f:{"texture":0,"uv":[start,y,x,y+1]} for f in ("north","south","east","west","up","down")}))
    write(OUT/"wands"/(p.stem+".bbmodel"),dict(meta={"format_version":"4.10","model_format":"java_block","box_uv":False},name=p.stem,resolution={"width":w,"height":h},elements=elements,outliner=[e["uuid"] for e in elements],textures=[tex(texture,(w,h))],display=original.get("display",{})))
def import_bb(p):
    d=json.loads(p.read_text(encoding="utf8"));id=d["name"];elements={e["uuid"]:e for e in d["elements"]}
    if d["meta"]["model_format"]!="modded_entity":
        old=json.loads((AS/"models/item"/(id+".json")).read_text(encoding="utf8"))
        location=old.get("textures",{}).get("layer0",old.get("textures",{}).get("all","tnc:item/wands/"+id))
        result={"textures":{"all":location,"particle":location},"display":d.get("display",{}),"elements":[]}
        for e in elements.values():
            cube={"from":e["from"],"to":e["to"],"faces":{f:{"uv":[v*16/d["resolution"]["width"] if i%2==0 else v*16/d["resolution"]["height"] for i,v in enumerate(face["uv"])],"texture":"#all"} for f,face in e["faces"].items() if face.get("texture") is not None}}
            if e.get("rotation") and any(e["rotation"]):
                axis=[i for i,v in enumerate(e["rotation"]) if v]
                assert len(axis)==1,"Java物品单个方块只允许一个旋转轴"
                i=axis[0];assert e["rotation"][i] in (-45,-22.5,0,22.5,45),"Java物品旋转必须为0、±22.5、±45度"
                cube["rotation"]={"axis":"xyz"[i],"angle":e["rotation"][i],"origin":e.get("origin",[8,8,8])}
            result["elements"].append(cube)
        write(AS/"models/item"/(id+".json"),result)
    else:
        parts=[]
        def walk(group,parent="root",parent_origin=(0,24,0)):
            assert isinstance(group,dict),"装备最外层需保留骨骼分组"
            origin=group.get("origin",[0,24,0]);rot=group.get("rotation",[0,0,0])
            part=dict(name=group["name"],parent=parent,pivot=[origin[0]-parent_origin[0],parent_origin[1]-origin[1],origin[2]-parent_origin[2]],rotation=[-math.radians(rot[0]),math.radians(rot[1]),-math.radians(rot[2])],boxes=[]);parts.append(part)
            for child in group["children"]:
                if isinstance(child,dict):walk(child,group["name"],origin);continue
                e=elements[child];lo=e["from"];hi=e["to"]
                assert not any(e.get("rotation",[0,0,0])),"装备请旋转骨骼分组，不要单独旋转方块"
                part["boxes"].append(dict(uv=e.get("uv_offset",[0,0]),position=[lo[0]-origin[0],origin[1]-hi[1],lo[2]-origin[2]],size=[hi[i]-lo[i] for i in range(3)],inflate=e.get("inflate",0)))
        for group in d["outliner"]:walk(group)
        assert {"head","hat","body","right_arm","left_arm","right_leg","left_leg"}<=set(p["name"] for p in parts),"请保留原版7个主骨骼名称"
        write(AS/"models/armor"/(id+".json"),{"parts":parts,"texture_width":d["resolution"]["width"],"texture_height":d["resolution"]["height"]})
    # Imported model textures are explicit; do not silently overwrite other items.
    print("Imported",id)
def main():
    a=argparse.ArgumentParser();a.add_argument("--import-model",type=Path);args=a.parse_args()
    if args.import_model:return import_bb(args.import_model)
    for style in ("bastion","astral","runic","wanderer"):
        for tier in range(1,5):
            for hat in (False,True):export_armor(style+("_hat_" if hat else "_outfit_")+str(tier),armor(style,tier,hat))
    for hat in (False,True):export_armor("divine"+("_hat_" if hat else "_outfit_")+"5",armor("divine",5,hat))
    count=0
    for p in (AS/"models/item").glob("*wand*.json"):
        if not re.fullmatch(r"(magic_wand|(fire|water|earth|wind|light|lightning|dark)_wand_[1-5])",p.stem):continue
        d=json.loads(p.read_text(encoding="utf8"))
        if "layer0" in d.get("textures",{}):export_wand(p);count+=1
    print("Exported 34 exact wearable models and",count,"wand projects with embedded textures.")
if __name__=="__main__":main()
