"""Generate the war-god armour refinement, bounded echo mesh and modelled wind wave."""
import copy
import json
import math
from PIL import Image, ImageDraw
import gen_canglan_warlord as base


def main():
    base.sculpt()
    base.CUBES["shield"] = []
    # Layered swept vanes, chest chevrons and a coronet retain the authored skeleton.
    for side in (-1, 1):
        for i in range(4):
            base.b("arm_" + str(side), (side * (8.1 + i * .55), 24.2 + i * .5, .2 + i * .3),
                   (1.9, .35, 3.5 - i * .45), "edge", (0, side * 20, side * (12 + i * 8)))
        for i in range(3):
            base.b("chest", (side * (1.2 + i * 1.1), 23.0 - i * .65, -3.0),
                   (2.0, .22, .28), "edge", (0, 0, side * 24))
        base.b("helmet", (side * 2.1, 34.8, -1.7), (.45, 3.5, .6), "gale", (-15, 0, side * 25))
    bones = base.build_geo()
    base.write_geo(bones)
    base.write_animations()
    # Continuous materials avoid mapping a whole tiled rivet pattern onto every plate.
    # Geometry supplies plate seams; the atlas supplies restrained steel, cloth and gold.
    image = Image.new("RGBA", (256,256))
    import random
    rng=random.Random(20261022)
    colors={"steel":(42,65,80),"mist":(61,111,132),"edge":(177,153,89),"gale":(120,225,231)}
    for material,(ox,oy) in base.Q.items():
        rgb=colors[material]
        for y in range(128):
            for x in range(128):
                bevel=5*math.sin(x/128*math.pi)+3*math.cos(y/128*math.pi)
                grain=rng.randrange(-2,3)
                image.putpixel((ox+x,oy+y),tuple(max(0,min(255,int(v+bevel+grain))) for v in rgb)+(255,))
    draw=ImageDraw.Draw(image)
    # Sparse brushed highlights and wind channels, never repeated cell borders.
    for material in ("steel","edge"):
        ox,oy=base.Q[material]
        for y in (29,83):
            color=(65,98,111,255) if material=="steel" else (210,189,119,255)
            draw.arc([ox+15,oy+y-13,ox+109,oy+y+15],195,330,fill=color,width=1)
    image.save(base.TEX)
    animations = json.loads(base.ANIM.read_text(encoding="utf-8"))
    clips = animations["animations"]
    # The source animator used authored Euler angles. GeckoLib negates X/Y
    # and mirrors model X: invert X/Z to preserve those intended attack paths.
    for animation in clips.values():
        for track in animation["bones"].values():
            for value in track.get("rotation",{}).values():
                value[0]=-value[0];value[2]=-value[2]
    # Start from proven weapon-safe poses. Root rotation carries both the body and blade.
    cyclone = copy.deepcopy(clips["sweep"])
    cyclone["animation_length"] = 2.4
    cyclone["bones"]["root"] = {"rotation": {"0.0":[0,0,0],"0.45":[0,0,0],"1.3":[0,720,0],"2.0":[0,1080,0],"2.4":[0,1080,0]}}
    clips["cyclone"] = cyclone
    cut = copy.deepcopy(clips["cleave_left"])
    cut["animation_length"] = 1.5
    clips["gale_cut"] = cut
    sky = copy.deepcopy(clips["overhead"])
    sky["animation_length"] = 3.1
    for track in sky["bones"].values():
        for key, values in list(track.items()):
            if isinstance(values, dict):
                track[key] = {str(round(float(t) * 3.1 / 1.4, 3)): v for t, v in values.items()}
    # Hold the blade toward the sky during formation; wrists keep the shared hand grip.
    clips["sky_arsenal"] = sky
    glide = copy.deepcopy(clips["idle"])
    glide["animation_length"] = .3
    glide.pop("loop", None)
    # A short held stance, with the feet lifted while the server drives a six-tick wind step.
    for track in glide["bones"].values():
        for key, value in list(track.items()):
            if isinstance(value, dict):
                first = value[min(value, key=float)]
                track[key] = {"0": first, ".3": first}
    glide["bones"]["root"] = {"position":{"0":[0,0,0],".12":[0,6,0],".3":[0,0,0]}}
    clips["wind_step"] = glide
    # Conventional grip: bend the forearm forward and keep the blade above the
    # knuckles. The actual handle centre is seated by the renderer, not its blade root.
    for name, animation in clips.items():
        length = animation["animation_length"]
        fore = animation["bones"].setdefault("fore_1", {}).setdefault("rotation", {})
        fore["0.000"] = [-55, 0, 0]
        fore[f"{length:.3f}"] = [-55, 0, 0]
        wrist = animation["bones"].get("weapon_1", {}).get("rotation", {})
        for t, value in wrist.items():
            if name in ("idle", "walk") or (abs(value[0] - 26) < .1 and abs(value[2] - 34) < .1):
                wrist[t] = [110, 0, -8]
        if name in ("idle", "walk", "wind_step"):
            fore.update({t: [-55, 0, 0] for t in list(fore)})
        previous = None
        for t in sorted(wrist, key=float):
            value = wrist[t]
            if previous is not None:
                while value[0] - previous > 180:
                    value[0] -= 360
                while value[0] - previous < -180:
                    value[0] += 360
            previous = value[0]
    walk = clips["walk"]["bones"]
    for side in (-1, 1):
        for name, amplitude in (("thigh_", 24), ("shin_", -16), ("foot_", -9)):
            walk[name + str(side)] = {"rotation":{str(t):[side*amplitude*math.cos(t*math.pi),0,side*2] for t in (0,.5,1,1.5,2)}}
    walk["arm_-1"] = {"rotation":{str(t):[-18*math.cos(t*math.pi),0,12] for t in (0,.5,1,1.5,2)}}

    def stance(name, rotations, offset=(0,0,0)):
        data = copy.deepcopy(clips["idle"])
        data["animation_length"] = 1
        data["loop"] = True
        for bone, track in data["bones"].items():
            for channel, keys in list(track.items()):
                value = keys[min(keys,key=float)]
                track[channel] = {"0":value,"1":value}
        for bone, value in rotations.items():
            data["bones"].setdefault(bone,{})["rotation"]={"0":value,"1":value}
        data["bones"]["root"] = {"position":{"0":list(offset),"1":list(offset)}}
        clips[name] = data
    stance("echo_guard",{"chest":[8,-18,0],"arm_-1":[15,-12,-30],"thigh_1":[18,0,6],"thigh_-1":[-15,0,-8],"shin_1":[-18,0,0],"weapon_1":[110,0,-8]})
    stance("echo_lunge",{"pelvis":[0,-25,0],"chest":[12,-15,0],"thigh_1":[-40,0,10],"shin_1":[48,0,0],"thigh_-1":[25,0,-12],"arm_-1":[-20,0,-35],"weapon_1":[105,0,-8]},(0,-3,0))
    stance("echo_recover",{"chest":[6,30,0],"arm_1":[18,0,12],"fore_1":[-65,0,0],"arm_-1":[30,0,-25],"thigh_1":[15,0,8],"thigh_-1":[-18,0,-10],"weapon_1":[105,0,-8]})
    stance("phantom_idle",{"chest":[8,-12,0],"arm_-1":[18,0,-22],"thigh_1":[12,0,9],"thigh_-1":[-10,0,-9],"weapon_1":[110,0,-8]})
    for name, length, pose in (("spawn",2.6,"idle"),("phase_shift",2.6,"phantom_idle"),("shield_bash",1.1,"echo_guard")):
        data=copy.deepcopy(clips[pose]);data.pop("loop",None);data["animation_length"]=length
        for track in data["bones"].values():
            for channel, keys in list(track.items()):
                value=keys[min(keys,key=float)];track[channel]={"0":value,str(length):value}
        if name=="spawn":data["bones"]["root"]={"position":{"0":[0,-16,0],str(length):[0,0,0]}}
        if name=="phase_shift":data["bones"]["root"]={"position":{"0":[0,0,0],"1.3":[0,4,0],str(length):[0,0,0]}}
        if name=="shield_bash":data["bones"]["weapon_1"]={"rotation":{"0":[110,0,-8],str(length):[110,0,-8]}}
        clips[name]=data
    corrections=base.ROOT/"model-source/canglan_warlord/palm_grip_keys.json"
    if corrections.exists():
        for name, keys in json.loads(corrections.read_text(encoding="utf-8")).items():
            clips[name]["bones"].setdefault("weapon_1",{})["rotation"]=keys
    # The old overhead windup put the fist itself inside the helmet; steering
    # the blade alone cannot fix that. Open the raised shoulder to the outside.
    clips["overhead"]["bones"]["arm_1"]["rotation"]["0.550"]=[152,0,24]
    clips["sky_arsenal"]["bones"]["arm_1"]["rotation"]["1.218"]=[152,0,24]
    # GeckoLib treats "0" and "0.000" as the same starting keyframe.
    for animation in clips.values():
        for track in animation["bones"].values():
            for channel, keys in list(track.items()):
                if isinstance(keys,dict):
                    track[channel]={f"{float(t):.3f}":v for t,v in keys.items()}
    base.ANIM.write_text(json.dumps(animations, ensure_ascii=False, indent=2)+"\n",encoding="utf-8")
    # Echoes preserve every animation bone, but only the silhouette plates: <100 boxes each.
    echo = json.loads(base.GEO.read_text(encoding="utf-8"))
    for bone in echo["minecraft:geometry"][0]["bones"]:
        cubes = bone.get("cubes", [])
        bone["cubes"] = sorted(cubes, key=lambda c: math.prod(c["size"]), reverse=True)[:2]
    echo["minecraft:geometry"][0]["description"]["identifier"] = "geometry.tnc.canglan_echo"
    (base.ASSETS/"geo/entity/canglan_echo.geo.json").write_text(json.dumps(echo,indent=2)+"\n",encoding="utf-8")
    elements=[]
    for layer in range(3):
        for i in range(18):
            a=(i/17-.5)*2.15
            x=8+math.sin(a)*(17-layer*1.7)
            z=6+(1-math.cos(a))*9+layer*.8
            y=1+layer*2+3*math.cos(a)**2
            elements.append({"from":[round(x-1.15,3),round(y,3),round(z,3)],
                             "to":[round(x+1.15,3),round(y+.7,3),round(z+.5,3)],
                             "faces":{face:{"texture":"#wind","uv":[0,layer*4,16,layer*4+4]} for face in ["up","down","north","south","east","west"]}})
    model={"textures":{"wind":"tnc:spell_projectile/canglan_gale_wave","particle":"tnc:spell_projectile/canglan_gale_wave"},"elements":elements}
    (base.ASSETS/"models/projectile/canglan_gale_wave.json").write_text(json.dumps(model,indent=2)+"\n",encoding="utf-8")
    tex=Image.new("RGBA",(32,32));p=tex.load()
    for y in range(32):
        for x in range(32):
            shine=(x*7+y*3)%17
            p[x,y]=(130+shine,215+shine,220+shine,160 if y%8<5 else 80)
    tex.save(base.ASSETS/"textures/spell_projectile/canglan_gale_wave.png")
    print("Shield removed; conventional palm grip, walk and three echo poses generated")


if __name__ == "__main__":
    main()
