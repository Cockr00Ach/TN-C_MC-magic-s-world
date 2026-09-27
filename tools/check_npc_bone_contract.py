# -*- coding: utf-8 -*-
"""
check_npc_bone_contract.py  --  guard the animation contract for TN-C NPCs.

WHY THIS EXISTS
===============
Animations drive a model **by bone name**. If a bone gets renamed / removed /
re-parented while editing the model in Blockbench, the animation does not crash --
it just silently stops moving that limb (GeckoLib skips missing bones because our
GeoModel sets crashIfBoneMissing() = false). "Compiles fine, silently does
nothing" is the trap this project keeps hitting, so we check it mechanically.

It also checks things that make a model silently render wrong:
  * texture_width / texture_height must be powers of two (Bedrock UV depends on it)
  * every bone the animations move must exist in the model
  * the limbs the code animates must still be named armRight / armLeft / head

USAGE
=====
    python tools/check_npc_bone_contract.py self
    python tools/check_npc_bone_contract.py self zhuangquerang
    python tools/check_npc_bone_contract.py            # checks the default list

Exit code 0 = contract intact, 1 = something would silently break.

Pure ASCII on purpose (repo rule; Chinese docs live in docs/).
"""

import io
import json
import os
import sys

MODID = "tnc"

# Bones the Java side animates by name (see SelfBedrockNpcEntity /
# ZhuangquerangMaidNpcEntity). Renaming any of these breaks the animations.
REQUIRED_BONES = {
    "self": ["head", "body", "armRight", "armLeft", "legRight", "legLeft"],
    "zhuangquerang": [],
}

# Which skin file each Bedrock model actually uses. Take this from the entity class
# (textureResource()), NOT from a naming convention -- zhuangquerang's 128x128 skin
# is called zhuangquerang.png, so "<npc>_bedrock.png" would be a false alarm.
BEDROCK_SKIN = {
    "self": "self_bedrock.png",
    "cava": "cava_bedrock.png",
    "huai": "huai_bedrock.png",
    "zhuangquerang": "zhuangquerang.png",
}

DEFAULT_NPCS = ["self", "zhuangquerang"]

POW2 = [2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096]


def load_json(path):
    with io.open(path, encoding="utf-8") as f:
        return json.load(f)


def check_geo(repo, npc, problems, notes):
    path = os.path.join(repo, "src", "main", "resources", "assets", MODID,
                        "geo", "entity", npc + ".geo.json")
    if not os.path.isfile(path):
        problems.append("%s: model file missing -> %s" % (npc, path))
        return None
    data = load_json(path)
    geo = data["minecraft:geometry"][0]
    desc = geo["description"]
    tw, th = desc.get("texture_width"), desc.get("texture_height")

    notes.append("%s: %d bones, identifier=%s, texture=%sx%s"
                 % (npc, len(geo["bones"]), desc.get("identifier"), tw, th))

    for label, size in (("texture_width", tw), ("texture_height", th)):
        if size not in POW2:
            problems.append("%s: %s=%s is not a power of two (Bedrock UV will be wrong)"
                            % (npc, label, size))

    names = [b["name"] for b in geo["bones"]]
    dupes = sorted({n for n in names if names.count(n) > 1})
    if dupes:
        problems.append("%s: duplicate bone names -> %s" % (npc, dupes))

    for required in REQUIRED_BONES.get(npc, []):
        if required not in names:
            problems.append("%s: bone '%s' is MISSING (the Java side animates it by name)"
                            % (npc, required))

    # every bone that a cube/parent references must exist
    for bone in geo["bones"]:
        parent = bone.get("parent")
        if parent and parent not in names:
            problems.append("%s: bone '%s' has parent '%s' which does not exist"
                            % (npc, bone["name"], parent))
    return set(names)


def check_texture(repo, npc, geo_names, problems, notes):
    skin = BEDROCK_SKIN.get(npc, npc + "_bedrock.png")
    path = os.path.join(repo, "src", "main", "resources", "assets", MODID,
                        "textures", "entity", skin)
    if not os.path.isfile(path):
        problems.append("%s: bedrock skin missing -> %s" % (npc, skin))
        return
    try:
        from PIL import Image
    except ImportError:
        notes.append("%s: (Pillow not installed, skipped skin size check)" % npc)
        return
    with Image.open(path) as im:
        w, h = im.size
    notes.append("%s: skin %dx%d" % (npc, w, h))
    if w not in POW2 or h not in POW2:
        problems.append("%s: skin is %dx%d -- Bedrock needs powers of two" % (npc, w, h))


def check_animations(repo, npc, geo_names, problems, notes):
    path = os.path.join(repo, "src", "main", "resources", "assets", MODID,
                        "animations", "entity", npc + ".animation.json")
    if not os.path.isfile(path):
        notes.append("%s: (no animation file yet)" % npc)
        return
    data = load_json(path)
    for anim_name, anim in data.get("animations", {}).items():
        bones = list(anim.get("bones", {}).keys())
        notes.append("%s: animation '%s' -> bones %s" % (npc, anim_name, ",".join(bones)))
        if not bones:
            problems.append("%s: animation '%s' moves no bones at all" % (npc, anim_name))
        if geo_names is None:
            continue
        missing = [b for b in bones if b not in geo_names]
        if missing:
            problems.append("%s: animation '%s' moves bone(s) %s which are NOT in the model"
                            " (silently does nothing)" % (npc, anim_name, missing))
        if anim.get("loop") is not False and anim_name != "idle":
            problems.append("%s: animation '%s' has loop != false (thenPlayXTimes needs false)"
                            % (npc, anim_name))


def main(argv):
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    npcs = argv[1:] or DEFAULT_NPCS
    problems = []
    notes = []

    for npc in npcs:
        geo_names = check_geo(repo, npc, problems, notes)
        check_texture(repo, npc, geo_names, problems, notes)
        check_animations(repo, npc, geo_names, problems, notes)
        notes.append("")

    for line in notes:
        print(line)
    if problems:
        print("PROBLEMS (%d):" % len(problems))
        for p in problems:
            print("  [X] " + p)
        return 1
    print("bone contract OK for: %s" % ", ".join(npcs))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
