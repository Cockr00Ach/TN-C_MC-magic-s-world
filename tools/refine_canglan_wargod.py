"""Generate the war-god armour refinement, bounded echo mesh and modelled wind wave."""
import copy
import json
import math
from PIL import Image, ImageDraw
import gen_canglan_warlord as base


def main():
    base.sculpt()
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
    base.paint_texture()
    # Fine hammered-steel edge inlays and curving wind engravings in the same UV quadrants.
    image = Image.open(base.TEX).convert("RGBA")
    draw = ImageDraw.Draw(image)
    for x0, y0 in ((0, 0), (0, 128)):
        for y in range(8, 124, 24):
            draw.line([(x0 + 5, y0 + y), (x0 + 122, y0 + y)], fill=(194, 183, 125, 255))
            for x in range(12, 118, 24):
                draw.arc([x0+x-6,y0+y-6,x0+x+10,y0+y+8], 35, 260, fill=(178,239,230,255),width=1)
    image.save(base.TEX)
    animations = json.loads(base.ANIM.read_text(encoding="utf-8"))
    clips = animations["animations"]
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
    # The old resting wrist pointed the blade behind the body. Keep the point
    # forward and down, with a common resting grip at every action boundary.
    for name, animation in clips.items():
        wrist = animation["bones"].get("weapon_1", {}).get("rotation", {})
        for t, value in wrist.items():
            if name in ("idle", "walk") or (abs(value[0] + 26) < .1 and abs(value[2] + 34) < .1):
                wrist[t] = [-150, 0, -20]
        previous = None
        for t in sorted(wrist, key=float):
            value = wrist[t]
            if previous is not None:
                while value[0] - previous > 180:
                    value[0] -= 360
                while value[0] - previous < -180:
                    value[0] += 360
            previous = value[0]
    for name in ("sweep", "cyclone"):
        clips[name]["bones"]["weapon_1"]["rotation"]["0.150"] = [-170, 0, -10]
        clips[name]["bones"]["weapon_1"]["rotation"]["0.200"] = [-200, 0, 0]
        clips[name]["bones"]["weapon_1"]["rotation"]["0.250"] = [-200, 0, 0]
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
    print("War-god plates, 17 clips, reduced echo and native wave model generated")


if __name__ == "__main__":
    main()
