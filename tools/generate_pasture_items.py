"""Hard-pixel husbandry goods, seven real workshop models and two 3D mana bottles."""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/"src/main/resources/assets/tnc"
OUT=ROOT/"work/pasture-model-preview"
SPEC=json.loads((ROOT/"work/pasture-product-spec.json").read_text(encoding="utf-8-sig"))

def write(relative,data):
    p=ASSETS/relative;p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding="utf-8")

def icon(name):
    im=Image.new("RGBA",(16,16));d=ImageDraw.Draw(im)
    def rect(box,c):d.rectangle(box,fill=c)
    def poly(points,c):d.polygon(points,fill=c)
    def line(points,c,w=1):d.line(points,fill=c,width=w)
    if name.endswith("meat"):
        palette={"stonebarrow_meat":("#805653","#c68b79","#e1bca0"),"emberback_meat":("#6c4843","#a86553","#d5a87a"),"tideback_meat":("#63746e","#99ada0","#d3d6b0"),"froststride_meat":("#795e61","#b98c8d","#e1bdab"),"apiary_meat":("#937547","#b9a65f","#e1cd8c"),"starfelt_meat":("#74616f","#b497a6","#dbbec4")}
        edge,base,fat=palette[name]
        shapes={"stonebarrow_meat":[(2,5),(5,2),(12,3),(14,8),(11,13),(4,13),(1,9)],"emberback_meat":[(2,3),(11,2),(14,5),(12,10),(9,13),(2,12)],"tideback_meat":[(1,7),(5,3),(13,4),(14,8),(10,12),(3,11)],"froststride_meat":[(3,4),(8,2),(12,5),(10,10),(6,12),(2,9)],"apiary_meat":[(2,6),(4,3),(12,4),(14,9),(11,13),(4,12)],"starfelt_meat":[(3,4),(9,3),(13,7),(11,11),(4,12),(1,8)]}
        poly(shapes[name],edge);poly([(4,5),(10,4),(12,7),(9,11),(4,10)],base)
        line([(4,6),(7,7),(8,9),(11,9)],fat,2)
        if name in ("froststride_meat","starfelt_meat"):rect((10,11,13,12),"#ddd0af");rect((12,10,14,13),"#ece0c2")
    elif name.endswith("fat"):
        poly([(3,4),(8,2),(13,5),(12,11),(7,14),(2,10)],"#9c8560")
        poly([(4,5),(8,4),(11,6),(10,10),(6,11),(3,9)],"#e4ce98" if name!="warm_fat" else "#d1aa6e")
        line([(5,5),(6,8),(9,9)],"#f1e1b0",2)
    elif name in ("stone_bone","frost_bone"):
        line([(3,12),(12,3)],"#858878" if name=="stone_bone" else "#6992a1",4)
        line([(3,12),(12,3)],"#d4d1b0" if name=="stone_bone" else "#b7d9d5",2)
        for x,y in ((2,11),(3,12),(11,2),(12,3)):rect((x-1,y-1,x+1,y+1),"#eee4c3")
    elif name in ("froststride_egg","fertile_pasture_egg"):
        poly([(6,2),(9,2),(12,6),(13,11),(10,14),(5,14),(2,11),(3,6)],"#7b8e94")
        poly([(6,3),(9,3),(11,7),(11,11),(9,12),(5,12),(4,9),(5,5)],"#dbe0cf")
        for x,y in ((5,7),(9,10),(8,5)):rect((x,y,x+1,y+1),"#9aafb3")
        if name.startswith("fertile"):rect((7,8,8,10),"#bc965e");rect((6,9,9,9),"#d5ba7b")
    elif name in ("honeydew","spring_concentrate","warm_breath","empty_breath_jar","wind_bottle"):
        body={"honeydew":"#d2ab5d","spring_concentrate":"#91c9bf","warm_breath":"#e5b56c","empty_breath_jar":"#b8c7bf","wind_bottle":"#d5e1c5"}[name]
        rect((5,1,10,3),"#736857");rect((6,3,9,4),"#d1c4a6");poly([(4,5),(11,5),(13,8),(12,14),(3,14),(2,8)],"#54757b")
        poly([(5,6),(10,6),(11,8),(10,12),(5,12),(4,8)],body);rect((3,12,12,14),"#a98256")
        line([(5,7),(5,10)],"#e8edd7");line([(8,6),(9,8),(7,10)],"#f4e2b3" if name=="warm_breath" else "#bce6cd")
        if name=="wind_bottle":line([(5,9),(10,9),(8,7),(6,11)],"#719b90")
    elif name in ("shell_glue","nest_glue","hoof_glue"):
        base={"shell_glue":"#adc6ac","nest_glue":"#b6a886","hoof_glue":"#84baba"}[name]
        poly([(2,10),(5,8),(6,3),(9,2),(10,7),(13,10),(12,13),(3,13)],"#657a71")
        poly([(4,10),(6,8),(7,4),(8,4),(9,8),(11,10),(10,12),(4,12)],base);rect((7,5,8,7),"#e1e5bf")
    elif name in ("soft_down","flight_feather","watch_wing","air_plume"):
        base={"soft_down":"#bcaebc","flight_feather":"#c6c9b5","watch_wing":"#9bad88","air_plume":"#e4dec2"}[name]
        poly([(3,13),(5,7),(9,2),(13,2),(12,7),(8,11)],"#677c78")
        poly([(5,10),(7,6),(10,3),(12,3),(10,7),(7,10)],base)
        line([(3,14),(11,4)],"#dfd5b2")
        if name=="watch_wing":line([(5,9),(10,7),(9,4)],"#dbe7a5");rect((9,5,10,6),"#eef0bd")
        if name=="soft_down":line([(4,9),(7,8),(5,5)],base,2)
    elif name in ("mirror_scale","focus_lens","warning_lens","storm_crystal"):
        edge="#827297" if name in ("mirror_scale","focus_lens") else "#8b8e64"
        poly([(5,2),(10,2),(14,7),(11,13),(5,14),(1,8)],edge)
        poly([(6,4),(10,4),(12,7),(10,11),(6,12),(3,8)],"#a9d0c4" if name!="warning_lens" else "#c3d89f")
        line([(6,5),(9,5),(10,7)],"#e6f1d6",2)
        if name=="storm_crystal":line([(9,2),(6,7),(10,7),(5,13)],"#efe0a1",2)
        if name.endswith("lens"):rect((5,1,10,2),"#bda06d");rect((5,13,10,14),"#bda06d")
    elif name in ("loam_pebble","horn_powder"):
        poly([(2,6),(6,2),(11,3),(14,9),(10,13),(4,13),(1,10)],"#7b7766")
        poly([(4,7),(7,4),(11,5),(11,9),(8,11),(4,10)],"#c1b391")
        if name=="horn_powder":
            for x,y in ((3,12),(13,5),(8,13),(12,12)):rect((x,y,x+1,y+1),"#e0d0a8")
    elif name=="lantern_antler":
        line([(4,13),(6,9),(7,6),(8,2)],"#b19a68",3);line([(6,8),(3,5),(3,2)],"#b19a68",2);line([(7,7),(11,5),(12,2)],"#b19a68",2)
        for x,y in ((7,2),(2,2),(11,2)):rect((x,y,x+1,y+2),"#dce99d")
    elif name in ("runner_hide","forage_paper_hide","water_membrane"):
        edge="#8c7760" if name=="runner_hide" else "#9e967d" if name=="forage_paper_hide" else "#749e98"
        poly([(2,3),(6,4),(10,2),(14,4),(12,8),(13,13),(9,12),(5,14),(2,12),(4,8)],edge)
        poly([(5,5),(10,4),(12,5),(10,9),(10,11),(6,12),(4,10),(6,8)],"#c4b48f" if name!="water_membrane" else "#b1d0bc")
        line([(5,7),(10,7)],"#e1d7ac")
    elif name=="lamp_wax":
        poly([(5,2),(10,2),(12,6),(11,13),(4,13),(3,6)],"#9b8a67");rect((5,4,9,11),"#e2cd95");rect((6,2,8,4),"#f3e3b0");rect((7,1,7,2),"#685d4e")
    elif name in SPEC["dishes"]:
        if name=="frostwarm_skewer":
            line([(2,14),(13,2)],"#8a6a45",2)
            for x,y,c in ((4,10,"#bd8e7e"),(7,7,"#c8c8a9"),(10,4,"#c58857")):rect((x-1,y-1,x+2,y+2),c)
        elif name=="starfelt_travel_roll":
            poly([(1,7),(5,3),(10,4),(15,9),(11,13),(5,12)],"#9d865e");poly([(3,7),(6,5),(10,6),(13,9),(10,11),(6,10)],"#e0c797");line([(7,5),(8,7),(9,10)],"#7a9a83",2);rect((3,7,5,9),"#ae7c8a")
        else:
            rect((2,8,13,10),"#564e45");poly([(2,10),(13,10),(11,14),(4,14)],"#9c8d70");rect((4,8,11,9),"#d1c09b")
            liquids={"stonebarrow_hotpot":"#af9365","emberback_stew":"#b47d54","tideback_chowder":"#7ea391","honeydew_casserole":"#c4b877"}
            poly([(3,6),(5,4),(11,4),(13,6),(12,8),(3,8)],liquids[name]);rect((5,5,7,6),"#d6b98a");rect((9,6,11,7),"#7d9871");line([(6,1),(5,2),(6,3)],"#c8cbbb")
    elif name=="soft_lantern":
        rect((6,1,9,2),"#646a60");line([(5,2),(4,5),(11,5),(10,2)],"#ac9163",2);rect((3,6,12,7),"#91744b");rect((4,8,11,12),"#65796f");rect((6,8,9,12),"#e4d59d");rect((3,13,12,14),"#b29a67")
    elif name in ("waterproof_seed_wrap","quiet_felt","warm_feed"):
        edge="#506f77" if name=="waterproof_seed_wrap" else "#77747c" if name=="quiet_felt" else "#9b815c"
        rect((2,4,13,12),edge);rect((3,5,12,11),"#b0c1a3" if name!="warm_feed" else "#c7b076");line([(4,6),(11,10)],"#698f89",2);rect((2,5,4,11),"#6e8c82")
        if name=="warm_feed":line([(8,4),(8,12)],"#b98351",2)
    elif name=="calming_bell":
        rect((7,1,8,3),"#897a59");poly([(5,4),(10,4),(12,11),(3,11)],"#aa8c56");rect((5,5,9,9),"#d5ba78");rect((3,11,12,12),"#6e7870");rect((7,13,8,14),"#c7a772")
    elif name=="climbing_cord":
        for pts in ([(4,3),(10,3),(12,6),(10,9),(4,9),(2,6),(4,3)],[(4,7),(10,7),(12,10),(10,13),(4,13),(2,10),(4,7)]):line(pts,"#ae9a72",2)
        line([(7,2),(7,14)],"#637a75",2)
    elif name=="beast_saddle":
        poly([(2,4),(5,2),(10,2),(14,5),(12,10),(10,12),(5,12),(2,9)],"#6b604d");rect((4,4,11,9),"#a8996e");rect((5,3,10,5),"#c7b784");line([(2,8),(1,12),(4,14),(5,11)],"#809086",2);line([(12,8),(14,12),(11,14),(10,11)],"#809086",2)
    elif name=="pasture_scraper":
        line([(3,13),(10,6)],"#8e7653",3);poly([(8,4),(11,1),(15,5),(12,8)],"#627d7a");poly([(10,4),(11,3),(13,5),(12,6)],"#b3c0a0")
    elif name=="pasture_book":
        poly([(2,3),(11,1),(14,4),(14,13),(5,15),(2,12)],"#566d66");poly([(4,4),(11,3),(12,5),(12,12),(5,13),(4,11)],"#9ab096");line([(3,4),(3,11),(5,13)],"#c5b47e",2);rect((7,6,10,7),"#e3d8af");rect((8,8,9,10),"#d7c59a")
    elif name=="pasture_staff":
        line([(3,14),(9,5),(11,1)],"#837053",2);line([(8,5),(6,2),(7,1)],"#a9b38b",2);rect((9,3,12,5),"#739a91");rect((10,2,11,3),"#d5dcaa")
    elif name=="pasture_cage":
        rect((2,4,13,14),"#787365");rect((4,6,11,12),"#3d5357");rect((5,2,10,3),"#a79a73");line([(3,5),(12,5),(12,13),(3,13),(3,5)],"#baa67c",1)
        for x in (5,8,11):rect((x,6,x,12),"#aaa686")
        rect((6,8,9,10),"#6d9690")
    else:raise ValueError("Missing original sprite for "+name)
    return im

def element(bounds,texture="wood",uv=(0,0,16,16)):
    lo,hi=bounds
    return {"from":lo,"to":hi,"faces":{face:{"uv":list(uv),"texture":"#"+texture}for face in ("north","south","east","west","up","down")}}

def materials():
    palettes={"wood":("887756","65583f","ac9b6d"),"wood_dark":("625e48","494b3d","8c8866"),"straw":("bcab73","948351","ddca8d"),"copper":("b48358","815d44","d5ab76"),"rune":("61958a","3f6f69","a8ccb0"),"stone":("8e9990","657870","bcc1a7"),"glass":("9dbfb9","6b989c","d7e6cb"),"mana":("83c8ba","509693","bce7cc")}
    for name,pal in palettes.items():
        im=Image.new("RGBA",(16,16));pix=im.load()
        for y in range(16):
            for x in range(16):
                c=pal[0]
                if name=="glass" and x not in (0,1,14,15) and y not in (0,1,14,15):
                    pix[x,y]=(0,0,0,0) if (x-y)%13 else (*bytes.fromhex(pal[2]),255);continue
                if name.startswith("wood"):
                    c=pal[1] if y%5==0 else pal[2] if (x+y*3)%19==0 else pal[0]
                else:c=pal[1] if (x//2+y//2*3)%11==0 else pal[2] if (x//2*3+y//2)%13==0 else pal[0]
                pix[x,y]=(*bytes.fromhex(c),255)
        p=ASSETS/"textures/block"/("pasture_"+name+".png");p.parent.mkdir(parents=True,exist_ok=True);im.save(p)

def blocks():
    all_models={}
    E=lambda lo,hi,mat="wood":element((lo,hi),mat)
    models={
      "pasture_trough":[E([2,1,2],[14,3,14],"wood_dark"),E([1,3,1],[3,7,15]),E([13,3,1],[15,7,15]),E([3,3,1],[13,7,3]),E([3,3,13],[13,7,15])],
      "habitat_marker":[E([7,0,7],[9,14,9],"wood_dark"),E([5,9,6],[13,14,7]),E([7,10,5.8],[11,13,6],"rune"),E([6,0,6],[10,2,10],"stone")],
      "pasture_tray":[E([1,0,1],[15,2,15],"wood_dark"),E([1,2,1],[2,5,15]),E([14,2,1],[15,5,15]),E([2,2,1],[14,5,2]),E([2,2,14],[14,5,15]),E([7.5,2,2],[8.5,4,14]),E([2,2,7.5],[14,4,8.5])],
      "egg_rack":[E([1,0,1],[15,2,15],"wood_dark"),E([1,2,1],[2,6,15]),E([14,2,1],[15,6,15]),E([2,2,1],[14,5,2]),E([2,2,14],[14,5,15]),E([7,2,2],[9,4,14]),E([2,2,7],[14,4,9])],
      "dew_rack":[E([2,0,2],[4,8,4]),E([12,0,2],[14,8,4]),E([2,0,12],[4,8,14]),E([12,0,12],[14,8,14]),E([2,6,2],[14,8,4]),E([2,6,12],[14,8,14]),E([3,1,3],[13,3,13],"wood_dark"),E([4,3,4],[7,4,7],"glass"),E([9,3,4],[12,4,7],"glass"),E([4,3,9],[7,4,12],"glass"),E([9,3,9],[12,4,12],"glass")],
      "charging_perch":[E([2,0,2],[14,3,14],"stone"),E([4,3,4],[12,5,12],"copper"),E([7,5,7],[9,13,9],"wood_dark"),E([2,12,7],[14,14,9],"copper"),E([3,14,6],[5,15,10],"rune"),E([11,14,6],[13,15,10],"rune"),E([6,3,3.8],[10,5,4],"rune")],
      "mana_bottle_base":[E([3,0,3],[13,2,13],"stone"),E([4,2,4],[6,4,12],"copper"),E([10,2,4],[12,4,12],"copper"),E([6,2,4],[10,4,6],"copper"),E([6,2,10],[10,4,12],"copper"),E([6,2,6],[10,3,10],"rune")],
    }
    for x in (2,12):
        for z in (2,12):models["pasture_trough"].append(E([x,0,z],[x+2,3,z+2]))
    for x in (3,9):
        for z in (3,9):models["egg_rack"].append(E([x,2,z],[x+4,3,z+4],"straw"))
    for name,boxes in models.items():
        model={"textures":{m:"tnc:block/pasture_"+m for m in ("wood","wood_dark","straw","copper","rune","stone","glass","mana")},"elements":boxes,"display":{"gui":{"rotation":[30,225,0],"translation":[0,0,0],"scale":[.8,.8,.8]},"ground":{"translation":[0,3,0],"scale":[.5,.5,.5]},"fixed":{"rotation":[0,180,0],"scale":[.65,.65,.65]}}}
        write(Path("models/block")/(name+".json"),model)
        write(Path("blockstates")/(name+".json"),{"variants":{"":{"model":"tnc:block/"+name}}})
        write(Path("models/item")/(name+".json"),{"parent":"tnc:block/"+name})
        all_models[name]=model
    write(Path("models/block/pasture_glow.json"),{"textures":{"particle":"minecraft:block/air"},"elements":[]})
    write(Path("blockstates/pasture_glow.json"),{"variants":{"":{"model":"tnc:block/pasture_glow"}}})
    return all_models

def bottles():
    E=lambda lo,hi,mat="glass":element((lo,hi),mat)
    for name in SPEC["bottles"]:
        for filled in range(5):
            boxes=[E([5,2,5],[6,11,11]),E([10,2,5],[11,11,11]),E([6,2,5],[10,11,6]),E([6,2,10],[10,11,11]),E([5,1,5],[11,2,11],"copper"),E([6,11,6],[10,13,10],"glass"),E([5.5,12,5.5],[10.5,13,10.5],"copper"),E([6,13,6],[10,14,10],"wood_dark")]
            if name.startswith("refined"):
                boxes.extend([E([4.5,3,6],[5,10,10],"copper"),E([11,3,6],[11.5,10,10],"copper")])
            if filled:boxes.append(E([6,2,6],[10,2+filled*2,10],"mana"))
            # Four independent front graduations are physical cuboids.
            for i in range(4):boxes.append(E([7,3+i*2,4.7],[9,4+i*2,5],"mana" if i<filled else "copper"))
            model={"textures":{m:"tnc:block/pasture_"+m for m in ("glass","copper","wood_dark","mana")},"elements":boxes,"gui_light":"front","display":{"gui":{"rotation":[18,32,0],"translation":[0,0,0],"scale":[1,1,1]},"thirdperson_righthand":{"rotation":[0,90,0],"translation":[0,2,1],"scale":[.65,.65,.65]},"thirdperson_lefthand":{"rotation":[0,90,0],"translation":[0,2,1],"scale":[.65,.65,.65]},"firstperson_righthand":{"rotation":[0,20,0],"translation":[0,1,0],"scale":[.65,.65,.65]},"firstperson_lefthand":{"rotation":[0,20,0],"translation":[0,1,0],"scale":[.65,.65,.65]},"ground":{"translation":[0,2,0],"scale":[.5,.5,.5]}}}
            if filled==0:model["overrides"]=[{"predicate":{"tnc:mana_fill":i/4},"model":"tnc:item/"+name+"_"+str(i)}for i in range(1,5)]
            write(Path("models/item")/(name+(""if filled==0 else "_"+str(filled))+".json"),model)

def main():
    names=list(dict.fromkeys(SPEC["materials"]+list(SPEC["dishes"])+SPEC["utilities"]+["pasture_book","pasture_staff","pasture_cage","fertile_pasture_egg"]))
    atlas=Image.new("RGB",(640,80*((len(names)+7)//8)+70),(233,230,216));draw=ImageDraw.Draw(atlas)
    font=ImageFont.truetype("C:/Windows/Fonts/consola.ttf",9)
    draw.text((12,12),"Pasture inventory sprites - actual 16px assets",fill=(45,64,57))
    for i,name in enumerate(names):
        im=icon(name);p=ASSETS/"textures/item"/(name+".png");p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
        write(Path("models/item")/(name+".json"),{"parent":"minecraft:item/generated","textures":{"layer0":"tnc:item/"+name}})
        x=(i%8)*80;y=(i//8)*80+50;atlas.paste(im.resize((48,48),Image.Resampling.NEAREST),(x+16,y),im.resize((48,48),Image.Resampling.NEAREST));draw.text((x+2,y+48),name[:14],font=font,fill=(61,76,68))
    materials();models=blocks();bottles()
    for model in sorted((ROOT/"art/pasture/models").glob("*.json")):
        species=json.loads(model.read_text(encoding="utf-8"))["id"]
        write(Path("models/item")/(species+"_spawn_egg.json"),{"parent":"minecraft:item/template_spawn_egg"})
    OUT.mkdir(parents=True,exist_ok=True);atlas.save(OUT/"husbandry_items.png")
    (ROOT/"art/pasture/facility-models.json").write_text(json.dumps(models,indent=2),encoding="utf-8")
    print(json.dumps({"sprites":len(names),"facilities":len(models),"bottle_models":10,"spawn_egg_models":23}))

if __name__=="__main__":main()
