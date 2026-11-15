"""Generate the jian and scythe collection using the shared pixel forge."""

import math
from pathlib import Path

import craft_details
import pixel_forge
import traditional_weapons as traditional
import weapon_gen as forge


MATERIALS = {
    "ashwood": ("815c3c", "be9463", "e5ba84", "513728", "aa8051", "826548", "b2a79a", "8b7357"),
    "cloudridge": ("526e7e", "a7c5cb", "e3f7ef", "253d37", "547c66", "a18149", "e1f4cc", "779d68"),
    "field": ("67645a", "a09a83", "d2d1b9", "4f3827", "af8257", "755639", "c39b6b", "996448"),
    "iron_crescent": ("485464", "918fa1", "d7e1e2", "30282b", "734440", "854c34", "d7ab79", "9a4c36"),
    "white_bone": ("928166", "c9b89b", "f7efd5", "392a31", "6c4851", "80674b", "f4e0a4", "a88c5e"),
    "eclipse": ("353349", "635379", "e8a6cb", "1c1b29", "634367", "a2774d", "fff1b1", "c44f84"),
}


def part(name, bounds, role="guard"):
    return name, bounds, role, "block"


def jian(id, name, english, palette, blade, ceremonial):
    parts = traditional.fittings()
    parts.append(part("guard", (5.2, -.1, 3.6, 8.8, 1.15, 4.35)))
    if ceremonial:
        for direction in (-1, 1):
            for step in range(5):
                x = 7 + direction * (1.25 + .30 * step)
                z = 3.7 + .18 * abs(step - 2)
                parts.append(part(f"cloud_{direction}_{step}", (x-.2,-.2,z,x+.2,1.25,z+.58)))
        parts.append(part("jade_button", (6.58,-.23,-1.12,7.42,1.23,-.62), "gem"))
    return dict(id=id, zh=name, en=english, palette=palette, family="longsword",
                parts=parts, profile=blade, thick=1.05, step=.70)


def scythe(id, name, english, palette, profile, heel, hook):
    parts = [part("shaft", (6.6, .05, -7.8, 7.4, .95, 13.45), "grip"),
             part("heel_collar", (6.22, -.14, 11.9, 7.78, 1.14, 13.45)),
             part("boot", (6.44, -.05, -8.6, 7.56, 1.05, -7.64)),
             part("heel", heel, "blade")]
    if hook:
        parts.extend((part("counterpick", (7.45, .09, 12.75, 9.6, .89, 13.5), "blade"),
                      part("pick_tip", (9.35,.20,12.85,10.2,.78,13.32), "edge")))
    return dict(id=id, zh=name, en=english, family="scythe", palette=palette,
                parts=parts, sweep=profile, thick=1.1)


def collection():
    return [
        jian("ashwood_jian", "白蜡木练剑", "Ashwood Practice Jian", "ashwood",
             [(4.2,6.0,8.0),(12.2,6.08,7.92),(15.6,6.34,7.66),(16.65,6.82,7.18)], False),
        jian("cloudridge_jian", "云脊剑", "Cloudridge Jian", "cloudridge",
             [(4.2,5.72,8.28),(6.0,5.85,8.15),(12.4,6.02,7.98),(16.7,6.14,7.86),(20.1,6.9,7.1)], True),
        scythe("field_scythe", "木柄禾镰", "Field Scythe", "field",
               [(2.4,10.35,10.6),(3.25,11.75,12.22),(4.3,12.55,13.34),(5.65,12.6,13.95),(7.2,12.55,13.8)],
               (6.75,0,12.45,7.55,1.1,13.83), False),
        scythe("iron_crescent_scythe", "弦月铁镰", "Iron Crescent Scythe", "iron_crescent",
               [(1.10,10.7,10.95),(2.1,12.35,12.95),(3.35,13.65,14.55),(5.2,13.1,15.0),(7.15,12.4,14.6)],
               (6.52,0,12.2,7.75,1.13,14.55), True),
        scythe("white_bone_scythe", "白骨钩镰", "Ivory Hook Scythe", "white_bone",
               [(.75,8.8,9.10),(1.3,11.3,12.22),(2.4,12.8,14.2),(4,13.5,15.28),(5.55,13.05,15.65),(7.18,12.48,15)],
               (6.45,-.05,12.03,7.68,1.17,15.05), True),
        scythe("eclipse_scythe", "蚀月玄镰", "Eclipse Scythe", "eclipse",
               [(-.25,9.25,9.55),(.8,11.45,12.35),(2.05,12.75,14.67),(4,13.32,16.35),(5.68,13.15,16.0),(7.1,12.3,15.35)],
               (6.3,-.11,11.8,7.86,1.19,15.35), True),
    ]


def scythe_blade(weapon, detailed):
    profile = weapon["sweep"]
    step = .25 if detailed else .50
    parts = []
    previous = None
    for i in range(math.ceil((profile[-1][0] - profile[0][0]) / step)):
        x = profile[0][0] + i * step
        x_end = min(x + step, profile[-1][0])
        at = (x + x_end) / 2
        inside = forge._lerp_profile(profile, at, 0)
        outside = forge._lerp_profile(profile, at, 1)
        if previous is not None:
            # Join steep sections of the hooked tip across each pixel step.
            inside = min(inside, previous[1] - .10)
            outside = max(outside, previous[0] + .10)
        previous = (inside, outside)
        if not detailed:
            parts.append(part(f"silhouette_{i}", (x,.13,inside,x_end,.97,outside), "blade"))
            continue
        for j in range(math.ceil((outside - inside) / step)):
            z = inside + j * step
            rim = j == 0
            bevel = .24 if rim else .07 if j == 1 else 0
            parts.append(part(f"blade_{i}_{j}", (x,bevel,z,x_end,1.1-bevel,min(outside,z+step)),
                              "edge" if rim else "blade"))
        if outside - inside > 1 and (weapon["palette"] != "field" or i % 4 < 2):
            for y in (-.035, 1.055):
                parts.append(part(f"spine_{i}_{y}", (x,y,outside-.66,x_end,y+.08,outside-.32), "guard"))
    return [entry for entry in parts if all(round(a, 2) < round(b, 2)
            for a, b in zip(entry[1][:3], entry[1][3:]))]


def detail(weapon):
    parts = craft_details.refine(weapon, pixel_forge.parts(weapon, forge._lerp_profile))
    if "sweep" in weapon:
        parts.extend(scythe_blade(weapon, True))
        for row in range(14):
            z = 11.95 + row * .20
            for y in (-.08, .94):
                x = 6.6 + abs(row%6-3)*.20
                role = "gem" if row % 5 == 0 and weapon["palette"] != "field" else "guard"
                parts.append(part(f"socket_engraving_{row}_{y}", (x,y,z,x+.18,y+.13,z+.18), role))
    elif weapon["palette"] == "cloudridge":
        for row in range(49):
            z = 5.25 + row * .25
            spread = abs(row%9-4)*.085
            for x in (6.86-spread, 7.04+spread):
                for y in (-.04, 1.00):
                    parts.append(part(f"jade_ridge_{row}_{x}_{y}", (x,y,z,x+.13,y+.09,z+.25),
                                      "gem" if row % 9 in (0,1,2,7,8) else "blade"))
    return parts


def render(weapon, parts):
    shaped = dict(weapon, _pixel_parts=parts)
    texture, rects = forge.build_texture(shaped)
    shaped["_rects"] = rects
    model = forge.build_model(shaped, rects)
    # Fit long curved blades into a single inventory slot.
    minimum = [min(p[1][i] for p in parts) for i in (0, 2)]
    maximum = [max(p[1][i] for p in parts) for i in (3, 5)]
    scale = min(.69, 13.5 / max(b-a for a,b in zip(minimum,maximum)))
    model["display"]["gui"].update(scale=[scale]*3,
        translation=[round((8-(a+b)/2)*scale,4) for a,b in zip(minimum,maximum)]+[0])
    return shaped, texture, model


def main():
    forge.PALETTES.update({name: traditional.palette(*colors) for name,colors in MATERIALS.items()})
    rows = []
    for weapon in collection():
        id = weapon["id"]
        shaped, texture, model = render(weapon, detail(weapon))
        coarse = forge.expand_profile_legacy(weapon) + (scythe_blade(weapon, False) if "sweep" in weapon else [])
        _, _, ghost = render(weapon, coarse)
        ghost["display"] = model["display"]
        assert all(e["faces"] for e in model["elements"] + ghost["elements"])
        assert ghost["tnc_grip"] == model["tnc_grip"] and len(ghost["elements"]) < 85
        forge.write_model(id, model)
        forge.write_model("warrior_ghost/"+id, ghost)
        texture.save(Path(forge.TEX)/(id+".png"))
        preview = forge.render_preview(shaped, texture, size=540, yaw_deg=23, pitch_deg=-63)
        rows.append((preview, weapon["zh"]+" · "+weapon["en"], id))
        print(f"{id}: {len(model['elements'])} elements, {len(ghost['elements'])} ghost elements")
    forge.contact_sheet(rows, str(Path(forge.PREVIEW)/"progression-blades-20261115.png"),
                        cols=3, cell=485, title="战士武器 · 练剑、云脊剑与进阶镰刀")


if __name__ == "__main__":
    main()
