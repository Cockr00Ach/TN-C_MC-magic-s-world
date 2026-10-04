"""Author all 23 original pasture animals from editable cuboids.

This is a model asset pipeline, not an image-generation mock-up. Each model
owns its own skeleton, box layout and 128px Minecraft UV atlas. The exact same
boxes produce Java LayerDefinitions, editable JSON, Blockbench JSON and the
offline geometry previews. Re-run this script after changing any silhouette.
"""
from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path
from dataclasses import dataclass, field
from PIL import Image, ImageDraw, ImageFont
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "src/main/java/com/tnc/tnc/life/pasture/client"
TEXTURES = ROOT / "src/main/resources/assets/tnc/textures/entity/pasture"
SOURCE = ROOT / "art/pasture/models"
PREVIEW = ROOT / "work/pasture-model-preview"
SIZE = 128

# Flat, hard-edged material families. Five tones at most per material.
PALETTES = {
    "stone_hide": ("716b62", "555349", "8d8779", "aba48f"),
    "stone": ("989b91", "626e69", "bcc5b1", "d6d5b6"),
    "boar_skin": ("87785f", "625345", "ad9470", "c2aa88"),
    "ember_hide": ("614d48", "3c3538", "836051", "9f7760"),
    "ember_plate": ("aa6547", "704e3c", "d19058", "e8b97a"),
    "warm_glow": ("edba64", "b67e3f", "ffdc95", "ffefd1"),
    "tide_hide": ("527a74", "33554f", "76a299", "9fbcaa"),
    "tide_fin": ("8ac0b8", "537e86", "b4d9c5", "d3ead4"),
    "water_glow": ("89cfcc", "4f9faf", "bce3d2", "e4f5dc"),
    "frost_feather": ("bacbd0", "7c95a1", "d7e1df", "f0eee5"),
    "frost_dark": ("526b85", "33445e", "789aaa", "abd0d2"),
    "frost_glow": ("a4dcd6", "648eaa", "d4f3e6", "f3fce9"),
    "toad_skin": ("929653", "686d3b", "b1b674", "d0cf8e"),
    "honey": ("c7a353", "927342", "e6cd75", "f4e1a0"),
    "hare_fur": ("9c8c9d", "6b647e", "beb1b1", "e4d4c2"),
    "hare_ear": ("be99a5", "8d738c", "dac0c2", "f0dbcd"),
    "star_glow": ("ddc897", "b49b73", "f0e1b4", "fff1d1"),
    "deer_fur": ("8d785b", "5c5245", "b9a57b", "d7c8a0"),
    "deer_mane": ("434f4b", "303b3c", "65736a", "879586"),
    "antler": ("d4b882", "9d8058", "e5d1a0", "f6e7bd"),
    "antler_glow": ("d9d98d", "a1ac74", "eff4b0", "fcf6d4"),
    "snail_body": ("839d94", "556f70", "b2c1ac", "d6d7bb"),
    "snail_shell": ("8b7896", "605870", "b8a4b8", "d8c0c0"),
    "shell_stripe": ("bcaf84", "8d7d66", "dfc7a0", "eee0b8"),
    "glue": ("b9c7ad", "82a99c", "dce4c0", "faf3d1"),
    "heron_feather": ("c1c7b8", "8c9a94", "dfe2cf", "faf4da"),
    "heron_dark": ("455a65", "2a3d4d", "6f8790", "9eb5ae"),
    "heron_bill": ("bca16c", "8a7652", "dec38b", "ecdda9"),
    "mantis_leaf": ("6c8266", "465840", "92a584", "bcc5a1"),
    "mantis_dark": ("536361", "354647", "798f84", "a1b0a1"),
    "lamp_glow": ("a9d586", "719964", "dbec9d", "f3f5c1"),
    "moth_body": ("68647c", "45465d", "9690a6", "bdb3bd"),
    "mirror": ("b8c0c3", "7f949e", "d7e3d8", "eff0dd"),
    "mirror_rim": ("796c8f", "524461", "a997b2", "cbb9c7"),
    "mirror_glow": ("a9d8d4", "6b9fac", "d6eeea", "f5fce8"),
    "tapir_hide": ("7a6a5c", "524c46", "9a8c74", "bbab8c"),
    "tapir_gill": ("a06b52", "6b4c43", "d18c57", "eab66a"),
    "otter_fur": ("68665e", "444d49", "929281", "b9b8a0"),
    "otter_belly": ("a4b9ad", "7b928d", "c5d4b9", "dfe0c6"),
    "tortoise_shell": ("647671", "425654", "8f9b85", "b7bca0"),
    "earth_ring": ("ad9c77", "7b715c", "cfc29a", "e6d9b3"),
    "ray_sail": ("d9d1b4", "a69d83", "ede6ca", "faf3da"),
    "ray_edge": ("6c8983", "49615e", "9ab1a1", "c2caba"),
    "ray_body": ("7e9693", "536b73", "b3c1ac", "d4d6ba"),
    "lizard_skin": ("636979", "424958", "8c92a0", "b1b6b3"),
    "lizard_ridge": ("a49466", "72664c", "d1bf80", "ead8a1"),
    "electric_glow": ("b8d2c0", "809ea6", "d8e8cd", "fbf7da"),
    "whale_skin": ("789aa7", "526c81", "a3bdbe", "c8d6cd"),
    "whale_belly": ("b6c8b8", "87a395", "d4ddc4", "eee8d0"),
    "dew_glow": ("9ce0cd", "619a9b", "d8f4d7", "f4ffdf"),
    "runner_hide": ("867960", "5d554a", "b1a17b", "d0bd94"),
    "runner_bag": ("667168", "454f4a", "929a80", "b8b99b"),
    "bag_seam": ("c0a467", "8b754a", "dfc78d", "f1deb1"),
    "rhino_hide": ("81918a", "596a65", "a4b0a0", "c5c9b3"),
    "rhino_plate": ("677967", "455b51", "92a288", "b8c1a0"),
    "horn": ("cbb999", "95866f", "e3d6b5", "f4e8c8"),
    "raccoon_fur": ("928b77", "646552", "b9b29a", "d8cdb2"),
    "raccoon_mask": ("43525c", "2e3e4d", "68797b", "8f9b93"),
    "paper": ("d4cdb0", "a2997e", "e8e1c3", "f9f0d2"),
    "beaver_fur": ("8b7057", "5e4e41", "b79870", "d7bb8b"),
    "mud_board": ("92836d", "625c50", "bdae8b", "ded0a9"),
    "strider_hide": ("83938b", "526b68", "aabc9f", "d2d5b7"),
    "strider_hoof": ("536d79", "354b59", "809a99", "abc0ac"),
    "leaf_horn": ("acba8b", "7c936d", "d3d7a3", "eae8bc"),
    "marten_fur": ("746d83", "4c4a61", "a09aab", "c7bdc9"),
    "marten_tail": ("baaebd", "8c7c9b", "d6cbd1", "efe0d8"),
    "lamp_wax": ("c2b082", "968263", "e5d7a0", "f7edc0"),
    "eye": ("1c2b34", "14212b", "34414a", "52616b"),
    "eye_light": ("c9dcd5", "8da9ac", "e4ede0", "faf7e5"),
    "nose": ("795c55", "4d4342", "a3806c", "c6a392"),
    "hoof": ("414c4e", "283638", "646f69", "889087"),
    "ivory": ("d9cdb0", "aa9b7f", "eae0c3", "f7efd6"),
}
# Each species owns a readable hue family; avoid washing all coats into sage/beige.
PALETTES.update({
    "stone_hide":("6f7283","414353","a7adbf","d1d7e8"),
    "boar_skin":("996143","5b342a","cd9560","f2cf98"),
    "ember_hide":("412e41","231e2e","794451","b66964"),
    "ember_plate":("bd4b2e","70302b","f08035","ffc15d"),
    "tide_hide":("168c8c","135565","43c4b8","a0edca"),
    "tide_fin":("e8866b","b24461","ffb18a","ffe0b3"),
    "frost_feather":("e4edf7","9bafcd","f6fcff","ffffff"),
    "frost_dark":("314e9a","203061","5889d5","9abded"),
    "toad_skin":("d0ab23","81751c","f1d94c","fff2a0"),
    "honey":("e66c26","a43e22","ffad3e","ffe09a"),
    "hare_fur":("9a58b8","63387d","cb91e3","f2d5f6"),
    "hare_ear":("e792c6","a25595","ffc5e1","fff0ee"),
    "deer_fur":("ae573a","713225","de8960","ffe0b0"),
    "deer_mane":("382e48","211c30","655079","947393"),
    "antler_glow":("ffce43","dc8d2b","fff394","ffffdc"),
    "snail_body":("e4a172","a96155","ffd0a0","fff0cf"),
    "snail_shell":("7144a4","422575","ae75d0","eab6ee"),
    "shell_stripe":("f3c549","c07b27","ffe685","fff9d1"),
    "heron_feather":("f3f0e8","bab8c9","ffffff","ffffff"),
    "heron_dark":("263554","151b30","476399","91a9d7"),
    "heron_bill":("f39139","b95429","ffc775","ffe7ad"),
    "mantis_leaf":("56ae35","2b6c2e","96de48","e6f987"),
    "mantis_dark":("254b3e","152e30","438368","95bf7b"),
    "moth_body":("923758","522746","d96b8c","ffaabd"),
    "mirror":("f1bd58","bd7b31","ffe8a2","fffbe2"),
    "mirror_rim":("7149bc","423073","b17beb","e3b3ff"),
    "tapir_hide":("323142","1c1b27","615465","b0959b"),
    "tapir_gill":("d54a35","8b2828","ff9951","ffd17e"),
    "otter_fur":("704332","422921","ab7150","deb394"),
    "otter_belly":("44bac5","287280","85e4df","d1fff0"),
    "tortoise_shell":("cc8e30","795025","edc657","fff1ab"),
    "earth_ring":("573a75","322647","9c6db5","d3a1d4"),
    "ray_sail":("faf0da","c7bca1","fffcec","ffffff"),
    "ray_edge":("2d8bb7","235275","72c9e1","ccf3ed"),
    "ray_body":("57a8bf","2b617e","9de0e4","e6ffff"),
    "lizard_skin":("315dc2","233b7b","709bf2","b5d7ff"),
    "lizard_ridge":("e1bd36","977e23","fff171","ffffcc"),
    "electric_glow":("ffd758","caaa35","fff29c","ffffff"),
    "whale_skin":("3c7ed2","234974","75b9f1","c2e8ff"),
    "whale_belly":("dfedf5","a3c1d7","f6fcff","ffffff"),
    "runner_hide":("995635","5f3329","cb8557","f1bf89"),
    "runner_bag":("a32f4b","63253b","d26071","f39a94"),
    "rhino_hide":("838da8","4f5874","bbc7d9","e7eff6"),
    "rhino_plate":("555074","34334e","8b85ac","c5c0d8"),
    "raccoon_fur":("dad9d2","91939a","f7f1df","ffffff"),
    "raccoon_mask":("343440","1c1c28","646477","a1a1b4"),
    "beaver_fur":("a86625","66391f","dd9d42","ffd789"),
    "strider_hide":("298a67","23553e","69c693","b9efb2"),
    "strider_hoof":("533a77","322745","9573b7","d3b1da"),
    "marten_fur":("bd75bb","75467f","e9afd8","ffe8f4"),
    "marten_tail":("f2d3e8","c39aba","fff3f9","ffffff"),
})
GLOW = {"warm_glow", "water_glow", "frost_glow", "star_glow", "antler_glow", "lamp_glow", "mirror_glow", "electric_glow", "dew_glow"}

@dataclass
class Animal:
    id: str
    name: str
    animation: str
    shadow: float
    parts: list[dict] = field(default_factory=list)

    def part(self, name, pivot=(0, 0, 0), parent="root", rot=(0, 0, 0)):
        self.parts.append(dict(name=name, parent=parent, pivot=list(pivot), rotation=list(rot), boxes=[]))
        return self

    def box(self, part, xyz, size, material):
        assert material in PALETTES and all(isinstance(v, int) and v > 0 for v in size)
        next(p for p in self.parts if p["name"] == part)["boxes"].append(dict(position=list(xyz), size=list(size), material=material))
        return self

    def eyes(self, x, y, z, parent="head", material="eye"):
        for side in (-1, 1):
            self.box(parent, (side*x-0.5, y, z), (1, 1, 1), material)
        return self

    def four_legs(self, pivots, length, width, material, feet="hoof"):
        for name, pivot in zip(("leg_fl", "leg_fr", "leg_bl", "leg_br"), pivots):
            self.part(name, pivot).box(name, (-width/2, 0, -width/2), (width, length, width), material)
            if feet:
                self.box(name, (-width/2, length-2, -width/2-0.5), (width, 2, width+1), feet)
        return self


def build_models():
    a = []
    m = Animal("stonebarrow_boar", "石垒豪豕", "boar", .65)
    m.part("body", (0, 14, 1)).box("body", (-7,-4,-9), (14,8,18), "stone_hide")
    m.part("head", (0,14,-8)).box("head", (-5,-4,-5), (10,7,7), "boar_skin").box("head", (-4,-1,-8), (8,4,4), "nose")
    m.eyes(4.5,-2,-5.5)
    for side in (-1,1):
        m.box("head", (side*4-1,-6,-2),(2,3,3),"boar_skin")
        m.box("head", (side*3-1,2,-8),(2,3,2),"ivory")
        m.box("body", (side*6-1,-6,-5),(3,4,5),"stone")
        m.box("body", (side*6-1,-5,1),(3,3,5),"stone")
    m.box("body", (-4,-6,-6),(8,2,5),"stone").box("body",(-3,-5,1),(6,2,4),"stone")
    m.four_legs([(-5,18,-5),(5,18,-5),(-5,18,7),(5,18,7)],6,3,"boar_skin")
    m.part("tail",(0,-1,9),"body",(-.4,0,0)).box("tail",(-1,0,0),(2,2,4),"stone_hide")
    a.append(m)

    m = Animal("emberback_hog", "暮炭野豕", "hog", .57)
    m.part("body",(0,14,2)).box("body",(-5,-4,-10),(10,8,21),"ember_hide")
    m.part("head",(0,13,-8)).box("head",(-3,-3,-5),(6,6,7),"ember_hide").box("head",(-2,-1,-12),(4,3,8),"ember_plate").box("head",(-2,1,-13),(4,2,2),"nose")
    m.eyes(2.8,-1,-5.5)
    for side in (-1,1):
        m.part("vent_"+("l" if side<0 else "r"),(side*3,-3,-2),"body",(0,0,side*.35)).box("vent_"+("l" if side<0 else "r"),(-2,-4,-5),(4,5,12),"ember_plate")
        m.box("head",(side*3-1,-5,0),(2,3,4),"ember_plate")
        m.box("head",(side*2-1,2,-9),(1,2,4),"ivory")
    m.part("resource_heat",(0,-4,1),"body").box("resource_heat",(-2,-1,-5),(4,1,10),"warm_glow")
    m.four_legs([(-3.7,18,-5),(3.7,18,-5),(-3.7,18,9),(3.7,18,9)],6,2,"ember_hide")
    m.part("tail",(0,0,11),"body").box("tail",(-1,0,0),(2,2,6),"ember_plate")
    a.append(m)

    m = Animal("tideback_newt", "潮背鳄螈", "newt", .55)
    m.part("body",(0,20,0)).box("body",(-5,-3,-9),(10,5,20),"tide_hide")
    m.part("head",(0,19,-10)).box("head",(-6,-2,-6),(12,4,8),"tide_hide").box("head",(-5,1,-6),(10,1,7),"tide_fin")
    m.eyes(5.5,-2,-4.5)
    for i,z in enumerate((-6,0,6)):
        m.part("resource_sac"+str(i),(0,-3,z),"body").box("resource_sac"+str(i),(-3,-2,-2),(6,3,4),"water_glow")
        for side in (-1,1):
            name=("leg_f" if i==0 else "leg_m" if i==1 else "leg_b")+("l" if side<0 else "r")
            m.part(name,(side*4.5,21,z),("root"),(0,side*.25,side*-.32)).box(name,(min(0,side*4),0,-1),(5,2,3),"tide_hide")
            m.box(name,(-6 if side<0 else 4,1,-2),(3,1,5),"tide_fin")
    m.part("tail",(0,0,11),"body").box("tail",(-3,-2,0),(6,4,9),"tide_hide")
    m.part("tail_tip",(0,0,8),"tail").box("tail_tip",(-2,-2,0),(4,3,8),"tide_fin").box("tail_tip",(-1,-4,1),(2,6,7),"tide_fin")
    a.append(m)

    m = Animal("froststride_fowl", "银霜步禽", "fowl", .38)
    m.part("body",(0,11,1),rot=(.15,0,0)).box("body",(-4,-4,-5),(8,9,10),"frost_feather")
    m.part("head",(0,7,-4)).box("head",(-3,-3,-3),(6,6,6),"frost_feather").box("head",(-2,0,-7),(4,2,5),"frost_dark")
    m.eyes(2.7,-1,-2.5)
    m.box("body",(-2,-3,-6),(4,4,1),"frost_glow").box("body",(-1,-4,-6),(2,1,1),"frost_glow").box("body",(-1,1,-6),(2,1,1),"frost_glow")
    for side in (-1,1):
        wing="wing_l" if side<0 else "wing_r"
        m.part(wing,(side*4,-2,0),"body",(.2,side*.1,side*.08)).box(wing,(-1,-1,-3),(2,7,7),"frost_dark")
        m.box(wing,(-1,5,-2),(2,2,5),"frost_feather")
        leg="leg_fl" if side<0 else "leg_fr"
        m.part(leg,(side*2.3,15,2)).box(leg,(-1,0,-1),(2,7,2),"frost_dark").box(leg,(-1,7,-4),(2,2,6),"frost_dark")
    m.part("tail",(0,-1,5),"body",(-.38,0,0)).box("tail",(-3,0,0),(6,2,4),"frost_dark").box("tail",(-2,-1,3),(4,2,4),"frost_feather").box("tail",(-1,-2,6),(2,2,3),"frost_dark")
    a.append(m)

    m = Animal("apiary_toad", "蜂囊腴蛙", "toad", .46)
    m.part("body",(0,20,1)).box("body",(-6,-4,-5),(12,6,11),"toad_skin")
    m.part("head",(0,18,-5)).box("head",(-5,-2,-3),(10,4,5),"toad_skin")
    for side in (-1,1):
        m.box("head",(side*3-1,-4,-1),(3,3,3),"toad_skin")
        m.box("head",(side*3-.5,-3,-2),(2,2,1),"eye")
        sac="resource_sac_l" if side<0 else "resource_sac_r"
        m.part(sac,(side*5,-1,0),"body").box(sac,(-2,-2,-3),(4,4,7),"honey")
        leg="leg_fl" if side<0 else "leg_fr"
        m.part(leg,(side*4,21,-4),rot=(0,0,side*.24)).box(leg,(-1,0,-1),(2,2,4),"toad_skin").box(leg,(-2,2,-2),(4,1,4),"honey")
        leg="leg_bl" if side<0 else "leg_br"
        m.part(leg,(side*5,20,4),rot=(0,side*-.32,0)).box(leg,(-2,0,-2),(4,3,6),"toad_skin").box(leg,(-2,3,1),(4,1,5),"honey")
    m.part("throat",(0,1,-2),"head").box("throat",(-3,0,-1),(6,2,3),"honey")
    a.append(m)

    m = Animal("starfelt_hare", "星茸耳兔", "hare", .34)
    m.part("body",(0,18,3)).box("body",(-3,-4,-3),(6,7,8),"hare_fur").box("body",(-4,-6,0),(8,7,6),"hare_fur")
    m.part("head",(0,16,-2)).box("head",(-3,-3,-4),(6,6,5),"hare_fur").box("head",(-1,0,-5),(2,2,2),"nose")
    m.eyes(2.7,-1,-4)
    for side in (-1,1):
        ear="ear_l" if side<0 else "ear_r"
        m.part(ear,(side*1.6,-3,-1),"head",(side*-.1,0,side*.12)).box(ear,(-1,-8,-1),(2,8,2),"hare_fur").box(ear,(-.5,-7,-1.5),(1,6,1),"hare_ear")
        for y in (-6,-4,-2): m.box(ear,(-.5,y,-2),(1,1,1),"star_glow")
        leg="leg_fl" if side<0 else "leg_fr"
        m.part(leg,(side*2,20,-1)).box(leg,(-1,0,-1),(2,3,2),"hare_fur").box(leg,(-1,3,-3),(2,1,4),"hare_ear")
        leg="leg_bl" if side<0 else "leg_br"
        m.part(leg,(side*3,18,5)).box(leg,(-2,0,-2),(4,5,5),"hare_fur").box(leg,(-2,5,-4),(4,1,6),"hare_ear")
    m.part("tail",(0,-2,5),"body",(-.35,0,0)).box("tail",(-1,-1,0),(2,3,3),"star_glow")
    a.append(m)

    m = Animal("dusk_lantern_deer", "暮灯鹿", "deer", .59)
    m.part("body",(0,9,2)).box("body",(-4,-3,-7),(8,7,15),"deer_fur")
    m.part("neck",(0,-1,-6),"body",(-.25,0,0)).box("neck",(-2,-7,-2),(4,8,5),"deer_mane")
    m.part("head",(0,2,-6)).box("head",(-3,-2,-5),(6,5,7),"deer_fur").box("head",(-2,1,-8),(4,2,4),"deer_mane")
    m.eyes(2.7,0,-5)
    for side in (-1,1):
        m.box("head",(side*3-2,-2,0),(4,1,3),"deer_mane")
        horn="horn_l" if side<0 else "horn_r"
        m.part(horn,(side*2,-2,-1),"head",(0,0,side*.18)).box(horn,(-1,-6,-1),(2,6,2),"antler")
        m.box(horn,(-1,-8,-1),(2,2,2),"antler_glow").box(horn,(min(0,side*3),-5,-1),(4,1,2),"antler")
        m.box(horn,(side*3-1,-7,-1),(2,3,2),"antler_glow").box(horn,(-1,-6,0),(2,1,4),"antler")
        m.box(horn,(-1,-8,3),(2,3,2),"antler_glow")
    m.four_legs([(-2.6,13,-3),(2.6,13,-3),(-2.6,13,8),(2.6,13,8)],11,2,"deer_fur")
    m.part("tail",(0,0,8),"body",(-.3,0,0)).box("tail",(-1,0,0),(2,3,4),"deer_mane")
    a.append(m)

    m = Animal("pattern_shell_snail", "纹壳蜗", "snail", .43)
    m.part("body",(0,22,1)).box("body",(-4,-1,-8),(8,3,16),"snail_body").box("body",(-3,0,7),(6,2,4),"glue")
    m.part("head",(0,21,-7)).box("head",(-3,-2,-3),(6,4,4),"snail_body")
    m.part("shell",(1,-2,2),"body",(0,.12,0)).box("shell",(-5,-7,-5),(10,8,10),"snail_shell")
    m.box("shell",(-4,-10,-4),(8,3,8),"shell_stripe").box("shell",(-2,-12,-2),(5,2,5),"snail_shell")
    m.box("shell",(-1,-13,-1),(3,1,3),"shell_stripe").box("shell",(-5,-5,-6),(10,2,1),"shell_stripe")
    m.box("shell",(-3,-8,-6),(2,3,1),"shell_stripe").box("shell",(-1,-8,-6),(4,1,1),"shell_stripe").box("shell",(2,-7,-6),(1,3,1),"shell_stripe")
    for side in (-1,1):
        stalk="antenna_l" if side<0 else "antenna_r"
        m.part(stalk,(side*2,-1,-2),"head",(-.35,0,side*.2)).box(stalk,(-.5,-4,-.5),(1,4,1),"snail_body").box(stalk,(-1,-5,-1),(2,2,2),"eye")
    m.part("resource_glue",(0,1,-3),"body").box("resource_glue",(-3,0,-1),(6,1,5),"glue")
    a.append(m)

    m = Animal("post_heron", "邮羽鹭", "heron", .41)
    m.part("body",(0,11,1)).box("body",(-3,-3,-4),(6,7,9),"heron_feather")
    m.part("neck",(0,-1,-3),"body").box("neck",(-1,-7,-1),(2,7,3),"heron_feather")
    m.part("head",(0,2,-2)).box("head",(-2,-2,-3),(4,4,5),"heron_feather").box("head",(-1,0,-11),(2,1,9),"heron_bill")
    m.box("head",(-1,-3,0),(2,1,4),"heron_dark").eyes(1.7,-1,-2.5)
    m.part("resource_postbag",(0,2,-4),"body").box("resource_postbag",(-2,-1,-2),(4,4,3),"heron_dark").box("resource_postbag",(-1,0,-2.6),(2,2,1),"paper")
    for side in (-1,1):
        wing="wing_l" if side<0 else "wing_r"
        m.part(wing,(side*3,-2,0),"body",(0,0,side*.13)).box(wing,(-1,0,-2),(2,7,7),"heron_dark")
        m.part(wing+"_tip",(0,6,2),wing,rot=(.2,0,0)).box(wing+"_tip",(-1,0,-3),(2,5,6),"heron_feather")
        leg="leg_fl" if side<0 else "leg_fr"
        m.part(leg,(side*1.7,15,2)).box(leg,(-.5,0,-.5),(1,8,1),"heron_bill").box(leg,(-1,8,-3),(2,1,5),"heron_bill")
    m.part("tail",(0,-1,5),"body",(-.35,0,0)).box("tail",(-2,0,0),(4,1,6),"heron_dark")
    a.append(m)

    m = Animal("watch_mantis", "巡灯螳螂", "mantis", .36)
    m.part("body",(0,15,0),rot=(.18,0,0)).box("body",(-2,-4,-3),(4,8,5),"mantis_dark").box("body",(-3,1,1),(6,6,9),"mantis_leaf")
    m.part("head",(0,8,-2)).box("head",(-4,-2,-2),(8,4,4),"mantis_leaf").eyes(3.5,-1,-2.5)
    m.part("resource_lamp",(0,-3,-3),"body").box("resource_lamp",(-1,-1,-1),(2,4,2),"lamp_glow")
    for side in (-1,1):
        antenna="antenna_l" if side<0 else "antenna_r"
        m.part(antenna,(side*2,-2,0),"head",(-.3,0,side*.3)).box(antenna,(-.5,-5,-.5),(1,5,1),"mantis_dark").box(antenna,(-.5,-6,-1.5),(1,2,2),"lamp_glow")
        arm="arm_l" if side<0 else "arm_r"
        m.part(arm,(side*2,10,-2),rot=(-.6,0,side*.45)).box(arm,(-1,0,-1),(2,5,2),"mantis_leaf")
        m.part(arm+"_sickle",(0,5,0),arm,rot=(-1.7,0,0)).box(arm+"_sickle",(-.5,0,-1),(1,5,2),"mantis_dark").box(arm+"_sickle",(-.5,4,-2),(1,2,3),"ivory")
        for i,z in enumerate((0,6)):
            leg=("leg_f" if i==0 else "leg_b")+("l" if side<0 else "r")
            m.part(leg,(side*2.5,17,z),rot=(0,side*.35,side*-.7)).box(leg,(-.5,0,-.5),(1,5,1),"mantis_leaf")
            m.part(leg+"_foot",(0,5,0),leg,rot=(0,0,side*.7)).box(leg+"_foot",(-.5,0,-.5),(1,4,1),"mantis_dark")
    m.part("wing_l",(-2,2,3),"body",(0,0,-.2)).box("wing_l",(-1,0,0),(2,1,8),"mantis_leaf")
    m.part("wing_r",(2,2,3),"body",(0,0,.2)).box("wing_r",(-1,0,0),(2,1,8),"mantis_leaf")
    a.append(m)

    m = Animal("mirrorwing_moth", "镜瓣夜蛾", "moth", .38)
    m.part("body",(0,17,0)).box("body",(-2,-2,-4),(4,4,9),"moth_body").box("body",(-2,-1,4),(4,3,3),"mirror_rim")
    m.part("head",(0,16,-4)).box("head",(-2,-2,-2),(4,4,4),"moth_body").eyes(1.7,-1,-2.5,"head","eye_light")
    for side in (-1,1):
        antenna="antenna_l" if side<0 else "antenna_r"
        m.part(antenna,(side,-2,-1),"head",(-.25,0,side*.3)).box(antenna,(-.5,-4,-.5),(1,4,1),"mirror_rim").box(antenna,(-1,-5,-1),(2,1,2),"mirror_glow")
        for idx,z in enumerate((-3,2)):
            wing=("wing_front_" if idx==0 else "wing_back_")+("l" if side<0 else "r")
            m.part(wing,(side*1.5,0,z),"body",(0,side*.12,side*-.45))
            m.box(wing,(-9 if side<0 else 0,-1,-3),(9,1,7 if idx==0 else 6),"mirror_rim")
            m.box(wing,(-8 if side<0 else 1,-1.5,-2),(7,1,5 if idx==0 else 4),"mirror")
            m.box(wing,(-5 if side<0 else 3,-2,-1),(2,1,3 if idx==0 else 2),"mirror_glow")
        for i,z in enumerate((-2,0,3)):
            leg=("leg_f" if i==0 else "leg_m" if i==1 else "leg_b")+("l" if side<0 else "r")
            m.part(leg,(side,19,z)).box(leg,(-.5,0,-.5),(1,4,1),"moth_body")
    a.append(m)

    m = Animal("forgegill_tapir", "炉鳃山貘", "tapir", .72)
    m.part("body",(0,12,2)).box("body",(-7,-5,-10),(14,10,20),"tapir_hide")
    m.part("head",(0,12,-8)).box("head",(-4,-4,-6),(8,7,8),"tapir_hide").box("head",(-3,0,-9),(6,4,4),"tapir_gill")
    m.box("head",(-3,3,-10),(6,2,3),"nose").eyes(3.6,-2,-5.5)
    for side in (-1,1):
        m.box("head",(side*3-1,-6,0),(2,3,3),"tapir_gill")
        for i in range(4):
            g="gill_"+("l" if side<0 else "r")+str(i)
            m.part(g,(side*6,-2,-7+i*2),"body",(0,side*.22,side*-.15)).box(g,(-1,-2,-.5),(2,6,1),"tapir_gill")
            m.box(g,(-1.5,-1,-.5),(3,4,1),"warm_glow")
    m.four_legs([(-4.8,17,-4),(4.8,17,-4),(-4.8,17,9),(4.8,17,9)],7,3,"tapir_hide")
    m.part("tail",(0,0,10),"body",(-.2,0,0)).box("tail",(-1,-1,0),(2,2,4),"tapir_hide").box("tail",(-2,-2,3),(4,4,1),"tapir_gill")
    a.append(m)

    m = Animal("mistbelly_otter", "雾腹水獭", "otter", .46)
    m.part("body",(0,20,1)).box("body",(-4,-3,-8),(8,6,17),"otter_fur").box("body",(-3,2,-6),(6,1,12),"otter_belly")
    m.part("head",(0,18,-8)).box("head",(-4,-3,-4),(8,6,6),"otter_fur").box("head",(-3,0,-6),(6,3,3),"otter_belly").box("head",(-1,0,-7),(2,1,1),"nose").eyes(3.6,-1,-4)
    for side in (-1,1):
        m.box("head",(side*3-1,-4,0),(2,2,2),"otter_fur").box("head",(-6 if side<0 else 3,0,-4),(3,1,1),"ivory")
        sac="resource_sac_l" if side<0 else "resource_sac_r"
        m.part(sac,(side*3,1,0),"body").box(sac,(-1,-2,-5),(3,3,10),"water_glow")
        for i,z in enumerate((-5,7)):
            leg=("leg_f" if i==0 else "leg_b")+("l" if side<0 else "r")
            m.part(leg,(side*3,21,z),rot=(0,0,side*-.5)).box(leg,(-1,0,-1),(2,2,4),"otter_fur").box(leg,(-2,2,-2),(4,1,5),"otter_belly")
    m.part("tail",(0,0,9),"body",(.1,0,0)).box("tail",(-2,-1,0),(4,2,5),"otter_fur")
    m.part("tail_tip",(0,0,4),"tail").box("tail_tip",(-4,-.5,0),(8,1,7),"otter_belly")
    a.append(m)

    m = Animal("ringstone_tortoise", "环砾龟", "tortoise", .65)
    m.part("body",(0,20,1)).box("body",(-7,-3,-8),(14,5,16),"tortoise_shell")
    m.part("shell",(0,-2,0),"body").box("shell",(-6,-4,-7),(12,4,14),"tortoise_shell").box("shell",(-4,-6,-5),(8,2,10),"stone")
    m.part("head",(0,20,-7)).box("head",(-3,-2,-4),(6,4,5),"stone").eyes(2.7,-1,-4)
    for idx,(x,y,z) in enumerate(((-5,-4,-4),(5,-5,1),(-4,-3,6),(2,-6,-5))):
        n="ring_"+str(idx)
        m.part(n,(x,y,z),"body",(.15*idx,.2*idx,.2*(idx-1)))
        m.box(n,(-2,-3,-1),(1,6,2),"earth_ring").box(n,(2,-3,-1),(1,6,2),"earth_ring")
        m.box(n,(-1,-3,-1),(3,1,2),"earth_ring").box(n,(-1,2,-1),(3,1,2),"earth_ring")
    m.four_legs([(-5,22,-4),(5,22,-4),(-5,22,6),(5,22,6)],2,3,"stone",None)
    m.part("tail",(0,0,8),"body").box("tail",(-1,0,0),(2,2,4),"stone")
    m.part("resource_pebbles",(0,-6,1),"body").box("resource_pebbles",(-2,-1,-2),(4,1,4),"earth_ring")
    a.append(m)

    m = Animal("papersail_ray", "纸帆魟", "ray", .56)
    m.part("body",(0,17,0)).box("body",(-3,-2,-7),(6,4,15),"ray_body")
    m.part("head",(0,16,-7)).box("head",(-3,-1,-3),(6,3,4),"ray_body").eyes(2.7,-1,-2.5)
    for side in (-1,1):
        wing="wing_l" if side<0 else "wing_r"
        m.part(wing,(side*2,0,0),"body",(0,0,side*-.1)).box(wing,(-6 if side<0 else 0,0,-6),(6,1,12),"ray_sail")
        m.box(wing,(-6 if side<0 else 5,-.5,-6),(1,1,12),"ray_edge")
        m.part(wing+"_mid",(side*6,0,0),wing,rot=(0,0,side*.15)).box(wing+"_mid",(-5 if side<0 else 0,0,-4),(5,1,9),"ray_sail")
        m.box(wing+"_mid",(-5 if side<0 else 4,-.5,-4),(1,1,9),"ray_edge")
        m.part(wing+"_tip",(side*5,0,1),wing+"_mid",rot=(0,0,side*.25)).box(wing+"_tip",(-3 if side<0 else 0,0,-2),(3,1,5),"ray_sail")
        for i,z in enumerate((-4,5)):
            leg=("leg_f" if i==0 else "leg_b")+("l" if side<0 else "r")
            m.part(leg,(side*2,19,z)).box(leg,(-.5,0,-.5),(1,4,1),"ray_edge").box(leg,(-1,4,-1),(2,1,3),"ray_edge")
    m.part("tail",(0,0,8),"body",(-.15,0,0)).box("tail",(-1,-.5,0),(2,1,7),"ray_edge")
    m.part("tail_tip",(0,0,6),"tail",(-.6,0,0)).box("tail_tip",(-.5,-.5,0),(1,1,7),"ray_edge")
    m.part("resource_air",(0,-2,1),"body").box("resource_air",(-2,-2,-2),(4,2,5),"ray_sail")
    a.append(m)

    m = Animal("wirecall_lizard", "鸣线蜥", "lizard", .43)
    m.part("body",(0,21,0)).box("body",(-3,-2,-7),(6,4,15),"lizard_skin")
    m.part("head",(0,20,-7)).box("head",(-3,-2,-4),(6,4,5),"lizard_skin").box("head",(-2,0,-6),(4,2,3),"lizard_ridge").eyes(2.7,-1,-4)
    for side in (-1,1):
        for i,z in enumerate((-5,-1,3,7)):
            n="ridge_"+("l" if side<0 else "r")+str(i)
            m.part(n,(side*2,-2,z),"body",(0,0,side*.25)).box(n,(-.5,-3,-1),(1,4,2),"lizard_ridge")
            m.box(n,(-.5,-3,-1.5),(1,2,1),"electric_glow")
        for i,z in enumerate((-4,6)):
            leg=("leg_f" if i==0 else "leg_b")+("l" if side<0 else "r")
            m.part(leg,(side*2.5,22,z),rot=(0,side*.3,side*-.4)).box(leg,(-1,0,-1),(2,1,4),"lizard_skin").box(leg,(-2,1,-2),(4,1,4),"lizard_ridge")
    m.part("tail",(0,0,8),"body").box("tail",(-1,-1,0),(2,2,6),"lizard_skin")
    m.part("tail_tip",(0,0,5),"tail",(0,.85,0)).box("tail_tip",(-1,-1,0),(2,2,5),"lizard_skin")
    m.part("tail_end",(0,0,4),"tail_tip",(0,1.3,0)).box("tail_end",(-1,-1,0),(2,2,4),"lizard_ridge")
    for i in range(3): m.box("tail_end",(-2,-1,1+i),(4,1,1),"electric_glow")
    a.append(m)

    m = Animal("dewbound_whale", "凝露浮鲸", "whale", .71)
    m.part("body",(0,12,0)).box("body",(-9,-6,-11),(18,12,22),"whale_skin")
    m.box("body",(-7,5,-9),(14,2,18),"whale_belly").box("body",(-10,-3,-8),(1,6,16),"whale_belly").box("body",(9,-3,-8),(1,6,16),"whale_belly")
    m.part("head",(0,11,-10)).box("head",(-7,-4,-4),(14,9,6),"whale_skin").box("head",(-5,3,-5),(10,2,4),"whale_belly").eyes(6.5,0,-3.5)
    m.box("head",(-3,3,-5.5),(6,1,1),"nose")
    for side in (-1,1):
        for i,z in enumerate((-5,5)):
            fin=("fin_front_" if i==0 else "fin_back_")+("l" if side<0 else "r")
            m.part(fin,(side*8,2,z),"body",(0,side*.1,side*.5)).box(fin,(-5 if side<0 else 0,-1,-2),(5,2,5),"whale_skin")
            m.box(fin,(-5 if side<0 else 4,-1,-1),(1,2,3),"dew_glow")
    for i,z in enumerate((-6,-2,2,6)):
        n="pip_"+str(i)
        m.part(n,(0,-6,z),"body").box(n,(-3,-3,-1),(6,3,3),"dew_glow")
    m.part("tail",(0,0,11),"body").box("tail",(-3,-2,0),(6,4,4),"whale_skin")
    m.part("tail_tip",(0,0,3),"tail").box("tail_tip",(-7,-1,0),(14,2,5),"whale_belly")
    a.append(m)

    m = Animal("satchelback_runner", "囊背负兽", "runner", .62)
    m.part("body",(0,12,2),rot=(-.12,0,0)).box("body",(-5,-4,-8),(10,8,17),"runner_hide")
    m.part("head",(0,14,-7)).box("head",(-3,-3,-5),(6,6,7),"runner_hide").box("head",(-2,0,-7),(4,3,3),"nose").eyes(2.7,-1,-4.5)
    for side in (-1,1):
        m.box("head",(side*3-1,-5,-1),(2,3,3),"runner_bag")
        bag="resource_bag_l" if side<0 else "resource_bag_r"
        m.part(bag,(side*5,-2,1),"body").box(bag,(-3,-1,-5),(6,7,10),"runner_bag")
        m.box(bag,(-3,-2,-5),(6,2,10),"bag_seam").box(bag,(-2,1,-5.5),(4,2,1),"bag_seam")
        m.part("bag_lid_"+("l" if side<0 else "r"),(0,-2,4),bag).box("bag_lid_"+("l" if side<0 else "r"),(-3,-1,-9),(6,1,9),"runner_hide")
    m.four_legs([(-3.5,17,-4),(3.5,17,-4),(-3.5,15,8),(3.5,15,8)],7,3,"runner_hide")
    for n in ("leg_bl","leg_br"): m.box(n,(-1.5,6,-1.5),(3,3,3),"hoof")
    m.part("tail",(0,0,9),"body",(-.35,0,0)).box("tail",(-2,-1,0),(4,2,6),"runner_bag")
    a.append(m)

    m = Animal("bowlhorn_rhino", "碗角犀", "rhino", .81)
    m.part("body",(0,12,2)).box("body",(-8,-5,-10),(16,10,21),"rhino_hide")
    m.part("head",(0,13,-9)).box("head",(-6,-4,-8),(12,8,10),"rhino_hide").box("head",(-5,1,-10),(10,4,4),"rhino_plate")
    m.eyes(5.5,-1,-5.5)
    m.part("bowl_horn",(0,-3,-7),"head",(-.25,0,0))
    m.box("bowl_horn",(-2,-3,-2),(4,4,4),"horn").box("bowl_horn",(-4,-5,-3),(2,3,6),"horn").box("bowl_horn",(2,-5,-3),(2,3,6),"horn")
    m.box("bowl_horn",(-2,-5,-4),(4,3,2),"horn").box("bowl_horn",(-2,-5,2),(4,3,2),"horn")
    for side in (-1,1):
        m.box("head",(side*5-1,-6,-1),(3,3,3),"rhino_plate")
        plate="plate_l" if side<0 else "plate_r"
        m.part(plate,(side*4,-5,0),"body",(0,0,side*.25)).box(plate,(-3,-2,-5),(6,2,11),"rhino_plate")
    m.four_legs([(-5,17,-4),(5,17,-4),(-5,17,10),(5,17,10)],7,4,"rhino_hide")
    m.part("tail",(0,0,11),"body",(-.2,0,0)).box("tail",(-1,0,0),(2,2,5),"rhino_plate")
    a.append(m)

    m = Animal("pageforage_raccoon", "觅页浣兽", "raccoon", .37)
    m.part("body",(0,18,1)).box("body",(-3,-4,-4),(6,7,9),"raccoon_fur")
    m.part("head",(0,15,-3)).box("head",(-4,-3,-4),(8,6,6),"raccoon_fur").box("head",(-4,-1,-4.5),(8,2,1),"raccoon_mask")
    m.box("head",(-2,0,-6),(4,2,3),"raccoon_fur").box("head",(-1,0,-7),(2,1,1),"nose").eyes(2.5,-.8,-5,"head","eye_light")
    for side in (-1,1):
        m.box("head",(side*3-1,-5,-1),(2,3,2),"raccoon_mask")
        arm="arm_l" if side<0 else "arm_r"
        m.part(arm,(side*3,17,-1),rot=(-.15,0,side*-.2)).box(arm,(-1,0,-1),(2,6,2),"raccoon_fur").box(arm,(-1,5,-2),(2,2,3),"raccoon_mask")
        leg="leg_bl" if side<0 else "leg_br"
        m.part(leg,(side*2,21,4)).box(leg,(-1,0,-1),(2,3,3),"raccoon_mask")
    m.part("resource_paperbag",(0,0,-4),"body").box("resource_paperbag",(-2,0,-2),(4,4,2),"paper").box("resource_paperbag",(-1,1,-2.5),(2,2,1),"raccoon_mask")
    m.part("tail",(0,-1,5),"body",(-.4,0,0))
    for i in range(5):
        m.box("tail",(-1-i*.5,-1,i*2),(2+i,2,2),"raccoon_mask" if i%2==0 else "raccoon_fur")
    a.append(m)

    m = Animal("patternbuild_beaver", "锦纹筑狸", "beaver", .47)
    m.part("body",(0,17,2)).box("body",(-5,-4,-5),(10,8,12),"beaver_fur")
    m.part("head",(0,15,-3)).box("head",(-4,-3,-5),(8,6,7),"beaver_fur").box("head",(-3,0,-7),(6,3,3),"mud_board")
    m.box("head",(-2,2,-7.5),(4,2,1),"ivory").box("head",(-1,0,-8),(2,1,1),"nose").eyes(3.6,-1,-4.5)
    for side in (-1,1):
        m.box("head",(side*3-1,-4,-1),(2,2,3),"beaver_fur")
        panel="panel_l" if side<0 else "panel_r"
        m.part(panel,(side*5,-1,0),"body",(0,0,side*.12)).box(panel,(-1,-3,-3),(2,6,7),"mud_board")
        m.box(panel,(-1.5,-1,-2),(3,1,5),"earth_ring")
        arm="arm_l" if side<0 else "arm_r"
        m.part(arm,(side*4,18,-2)).box(arm,(-1,0,-1),(2,4,3),"beaver_fur").box(arm,(-1,3,-2),(2,2,3),"mud_board")
        leg="leg_bl" if side<0 else "leg_br"
        m.part(leg,(side*3,21,6)).box(leg,(-2,0,-1),(4,3,5),"beaver_fur")
    m.part("tail",(0,1,7),"body",(-.05,0,0)).box("tail",(-4,-.5,0),(8,1,10),"mud_board").box("tail",(-3,-1,2),(6,1,6),"earth_ring")
    a.append(m)

    m = Animal("springhoof_strider", "跃泉蹄兽", "strider", .59)
    m.part("body",(0,9,1),rot=(-.14,0,0)).box("body",(-4,-3,-5),(8,6,12),"strider_hide")
    m.part("neck",(0,-1,-4),"body",(-.2,0,0)).box("neck",(-2,-5,-2),(4,6,4),"strider_hide")
    m.part("head",(0,4,-4)).box("head",(-3,-2,-4),(6,5,6),"strider_hide").box("head",(-2,0,-6),(4,3,3),"strider_hoof").eyes(2.7,-1,-4)
    for side in (-1,1):
        horn="horn_l" if side<0 else "horn_r"
        m.part(horn,(side*2,-2,0),"head",(-.65,0,side*.15)).box(horn,(-1,-6,-.5),(2,6,1),"leaf_horn")
        m.box(horn,(-1,-6,-1.5),(2,2,2),"leaf_horn")
        for i,z in enumerate((-2,6)):
            leg=("leg_f" if i==0 else "leg_b")+("l" if side<0 else "r")
            m.part(leg,(side*2.7,12,z),rot=(.12 if i==0 else -.12,0,0)).box(leg,(-1,0,-1),(2,8,2),"strider_hide")
            # Three stepped horizontal bands read as an actual spring hoof.
            for j in range(3): m.box(leg,(-1.5,8+j,-1.5),(3,1,4),"strider_hoof" if j!=1 else "water_glow")
            for toe in (-1,0,1): m.box(leg,(toe-.5,11,-2),(1,1,4),"strider_hoof")
    m.part("tail",(0,-1,7),"body",(-.3,0,0)).box("tail",(-2,-1,0),(4,2,5),"strider_hide")
    m.part("resource_saddle",(0,-3,0),"body").box("resource_saddle",(-4,-1,-2),(8,1,5),"mud_board")
    a.append(m)

    m = Animal("pillowlight_marten", "枕光貂", "marten", .37)
    m.part("body",(0,20,0)).box("body",(-3,-2,-8),(6,4,17),"marten_fur")
    m.part("head",(0,18,-8)).box("head",(-3,-2,-4),(6,4,6),"marten_fur").box("head",(-2,0,-6),(4,2,3),"marten_tail").box("head",(-1,0,-7),(2,1,1),"nose").eyes(2.7,-1,-4)
    for side in (-1,1): m.box("head",(side*2-1,-3,0),(2,2,3),"marten_tail")
    m.four_legs([(-2,22,-5),(2,22,-5),(-2,22,6),(2,22,6)],2,2,"marten_fur",None)
    m.part("resource_lamp",(0,-2,1),"body").box("resource_lamp",(-2,-3,-2),(4,3,5),"lamp_wax")
    for i in range(4):
        m.part("pip_"+str(i),(0,-2,1),"body").box("pip_"+str(i),(-1,-2,-1+i),(2,1,1),"lamp_glow")
    m.part("tail",(0,0,9),"body",(-.2,0,0)).box("tail",(-2,-1,0),(4,2,4),"marten_fur")
    m.part("tail_tip",(0,0,3),"tail").box("tail_tip",(-4,-2,0),(8,3,8),"marten_tail").box("tail_tip",(-3,-2.5,1),(6,1,6),"lamp_wax")
    a.append(m)
    assert len(a) == 23 and len({x.id for x in a}) == 23
    return a


def color(hex_value):
    return tuple(bytes.fromhex(hex_value)) + (255,)


def pack_uv(model):
    """Shelf pack material/dimension islands; identical surfaces may reuse UV."""
    keys = {}
    for p in model.parts:
        for b in p["boxes"]:
            w,h,d = b["size"]
            k=(b["material"],w,h,d)
            keys[k]=(2*(w+d),h+d)
    # Largest first avoids tiny islands stranding a large torso.
    shelves=[]
    uv={}
    for k,(w,h) in sorted(keys.items(),key=lambda t:(-t[1][1],-t[1][0],t[0])):
        chosen=None
        for shelf in shelves:
            if h<=shelf[2] and shelf[1]+w+1<=SIZE:
                chosen=shelf
                break
        if chosen is None:
            y=1 if not shelves else shelves[-1][0]+shelves[-1][2]+1
            assert y+h+1<=SIZE, f"{model.id} UV does not fit: {k}"
            chosen=[y,1,h]
            shelves.append(chosen)
        uv[k]=(chosen[1],chosen[0]); chosen[1]+=w+1
    for p in model.parts:
        for b in p["boxes"]:
            b["uv"]=list(uv[(b["material"],*b["size"])])
    return uv


def cube_uv_faces(u,v,w,h,d):
    return {
        "west": (u,v+d,d,h), "north": (u+d,v+d,w,h),
        "east": (u+d+w,v+d,d,h), "south": (u+d+w+d,v+d,w,h),
        "up": (u+d,v,w,d), "down": (u+d+w,v,w,d),
    }


def paint_texture(model,uv):
    skin=Image.new("RGBA",(SIZE,SIZE),(0,0,0,0))
    glow=Image.new("RGBA",(SIZE,SIZE),(0,0,0,0))
    pix=skin.load(); emissive=glow.load()
    for (material,w,h,d),(u,v) in uv.items():
        palette=list(map(color,PALETTES[material]))
        seed=int.from_bytes(hashlib.sha256((model.id+material).encode()).digest()[:4],"big")
        for face,(x0,y0,fw,fh) in cube_uv_faces(u,v,w,h,d).items():
            for yy in range(fh):
                for xx in range(fw):
                    # Two-pixel clusters, restrained mottling and real face shading.
                    noise=((xx//2)*19+(yy//2)*31+seed)%31
                    idx=0
                    if noise<3: idx=1
                    elif noise>26: idx=2
                    if face=="up" and noise>17: idx=2
                    if face=="down" and noise<20: idx=1
                    if material in GLOW and (xx+yy+seed)%9==0: idx=3
                    if material in {"snail_shell","shell_stripe"} and ((xx//2+yy//2+seed)%6)==0: idx=2
                    if material=="mirror" and (xx==fw//2 or yy==fh//2): idx=3
                    if material in {"stone_hide","deer_fur","hare_fur","whale_skin"} and (xx//2*7+yy//2*11+seed)%23<3: idx=3
                    if material in {"tide_hide","ember_hide","lizard_skin","strider_hide"} and (xx+yy//3)%7<2: idx=1
                    if material in {"heron_feather","frost_feather","ray_edge"} and yy>=fh*2//3: idx=1
                    if material=="snail_shell" and (xx+yy*2)%9<2: idx=3
                    pix[x0+xx,y0+yy]=palette[idx]
                    if material in GLOW: emissive[x0+xx,y0+yy]=palette[idx]
    TEXTURES.mkdir(parents=True,exist_ok=True)
    skin.save(TEXTURES/f"{model.id}.png")
    glow.save(TEXTURES/f"{model.id}_glow.png")
    return skin


def number(v):
    return str(int(v))+".0F" if v==int(v) else f"{v:.5f}F"


def generate_java(models):
    CLIENT.mkdir(parents=True,exist_ok=True)
    out=["package com.tnc.tnc.life.pasture.client;", "", "import com.tnc.tnc.TNMod;", "import net.minecraft.client.model.geom.ModelLayerLocation;", "import net.minecraft.client.model.geom.PartPose;", "import net.minecraft.client.model.geom.builders.*;", "import net.minecraft.resources.ResourceLocation;", "import java.util.LinkedHashMap;", "import java.util.Map;", "", "/** Generated from tools/generate_pasture_models.py. Every species owns its skeleton. */", "public final class PastureGeometry {", "    private PastureGeometry() {}", "    public static final Map<String, ModelLayerLocation> LAYERS = new LinkedHashMap<>();", "    static {"]
    for m in models: out.append(f'        LAYERS.put("{m.id}", new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "{m.id}"), "main"));')
    out += ["    }", "    public static LayerDefinition layer(String species) {", "        return switch (species) {"]
    for m in models: out.append(f'            case "{m.id}" -> {m.id}();')
    out += ['            default -> throw new IllegalArgumentException("Unknown pasture model: " + species);', "        };", "    }"]
    out += ["    public static String[][] bones(String species) {", "        return switch (species) {"]
    for m in models:
        pairs = ", ".join('{"'+p["name"]+'", "'+p["parent"]+'"}' for p in m.parts)
        out.append(f'            case "{m.id}" -> new String[][]{{{pairs}}};')
    out += ['            default -> throw new IllegalArgumentException("Unknown pasture skeleton: " + species);', "        };", "    }"]
    for m in models:
        out += [f"    private static LayerDefinition {m.id}() {{", "        MeshDefinition mesh = new MeshDefinition();", "        PartDefinition root = mesh.getRoot();"]
        for p in m.parts:
            var=p["name"]
            builder="CubeListBuilder.create()"
            for b in p["boxes"]:
                u,v=b["uv"]
                builder += f"\n                .texOffs({u}, {v}).addBox({', '.join(number(z) for z in b['position']+b['size'])})"
            pose="PartPose.offsetAndRotation("+", ".join(number(z) for z in p["pivot"]+p["rotation"])+")"
            out += [f'        PartDefinition {var} = {p["parent"]}.addOrReplaceChild("{var}", {builder}, {pose});']
        out += [f"        return LayerDefinition.create(mesh, {SIZE}, {SIZE});", "    }"]
    out += ["}",""]
    (CLIENT/"PastureGeometry.java").write_text("\n".join(out),encoding="utf-8")


def matrix(pivot,rotation):
    x,y,z=rotation
    cx,sx=math.cos(x),math.sin(x);cy,sy=math.cos(y),math.sin(y);cz,sz=math.cos(z),math.sin(z)
    rx=np.array([[1,0,0,0],[0,cx,-sx,0],[0,sx,cx,0],[0,0,0,1]],float)
    ry=np.array([[cy,0,sy,0],[0,1,0,0],[-sy,0,cy,0],[0,0,0,1]],float)
    rz=np.array([[cz,-sz,0,0],[sz,cz,0,0],[0,0,1,0],[0,0,0,1]],float)
    trans=np.eye(4);trans[:3,3]=pivot
    return trans@rz@ry@rx


def model_faces(model):
    transforms={"root":np.eye(4)}
    faces=[]
    for p in model.parts:
        transforms[p["name"]]=transforms[p["parent"]]@matrix(p["pivot"],p["rotation"])
        for b in p["boxes"]:
            x,y,z=b["position"];w,h,d=b["size"]
            vertices=np.array([[x,y,z,1],[x+w,y,z,1],[x+w,y+h,z,1],[x,y+h,z,1],[x,y,z+d,1],[x+w,y,z+d,1],[x+w,y+h,z+d,1],[x,y+h,z+d,1]])
            vs=(transforms[p["name"]]@vertices.T).T[:,:3]
            # Standard Minecraft box layout; UV face names have different world axes.
            uvs=cube_uv_faces(*b["uv"],w,h,d)
            for name,indices in (("north",(0,1,2,3)),("south",(5,4,7,6)),("west",(4,0,3,7)),("east",(1,5,6,2)),("up",(4,5,1,0)),("down",(3,2,6,7))):
                faces.append((vs[list(indices)],uvs[name],b["material"]))
    return faces


def render_preview(model,skin,side=420,yaw=-.67):
    """Rasterise actual UV textured cuboid faces, with depth-buffered triangles."""
    im=np.zeros((side,side,4),dtype=np.uint8);im[:]=[237,232,217,255]
    depth=np.full((side,side),-1e10)
    cy,sy=math.cos(yaw),math.sin(yaw)
    forward=np.array([sy,-.42,cy]);forward/=np.linalg.norm(forward)
    right=np.cross(np.array([0,1,0]),forward);right/=np.linalg.norm(right)
    up=np.cross(forward,right)
    fs=model_faces(model)
    allv=np.concatenate([f[0] for f in fs])
    pr=np.stack((allv@right,allv@up),axis=1)
    lo=pr.min(axis=0);hi=pr.max(axis=0);scale=min((side-54)/(hi[0]-lo[0]),(side-64)/(hi[1]-lo[1]))
    centre=(hi+lo)*.5
    tex=np.asarray(skin)
    # Soft, pixel-authored ground shadow. It is not part of the animal asset.
    bg=Image.fromarray(im)
    dr=ImageDraw.Draw(bg)
    dr.ellipse((side*.25,side*.79,side*.77,side*.88),fill=(215,211,196,255))
    im=np.asarray(bg).copy()
    for vertices,uv,material in fs:
        projected=np.stack((vertices@right,vertices@up),axis=1)
        screen=(projected-centre)*scale+np.array([side/2,side/2-2])
        zs=vertices@forward
        x,y,w,h=uv
        tv=np.array([[x,y],[x+w-.01,y],[x+w-.01,y+h-.01],[x,y+h-.01]])
        normal=np.cross(vertices[1]-vertices[0],vertices[2]-vertices[0]);normal/=max(1e-9,np.linalg.norm(normal))
        light=.77+.23*abs(normal@np.array([.3,-.8,-.52]))
        for inds in ((0,1,2),(0,2,3)):
            pts=screen[list(inds)];uvs=tv[list(inds)];zv=zs[list(inds)]
            lx,ly=np.maximum(np.floor(pts.min(axis=0)).astype(int),0);rx,ry=np.minimum(np.ceil(pts.max(axis=0)).astype(int),side-1)
            if lx>rx or ly>ry:continue
            gridx,gridy=np.meshgrid(np.arange(lx,rx+1)+.5,np.arange(ly,ry+1)+.5)
            a,b,c=pts
            denominator=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
            if abs(denominator)<1e-8:continue
            wa=((b[1]-c[1])*(gridx-c[0])+(c[0]-b[0])*(gridy-c[1]))/denominator
            wb=((c[1]-a[1])*(gridx-c[0])+(a[0]-c[0])*(gridy-c[1]))/denominator
            wc=1-wa-wb
            z=wa*zv[0]+wb*zv[1]+wc*zv[2]
            visible=(wa>=-1e-6)&(wb>=-1e-6)&(wc>=-1e-6)&(z>depth[ly:ry+1,lx:rx+1])
            if not visible.any():continue
            tx=np.clip((wa*uvs[0,0]+wb*uvs[1,0]+wc*uvs[2,0]).astype(int),0,SIZE-1)
            ty=np.clip((wa*uvs[0,1]+wb*uvs[1,1]+wc*uvs[2,1]).astype(int),0,SIZE-1)
            colors=tex[ty,tx].copy();colors[:,:,:3]=(colors[:,:,:3].astype(float)*light).astype(np.uint8)
            visible &= colors[:,:,3]>0
            im[ly:ry+1,lx:rx+1][visible]=colors[visible]
            depth[ly:ry+1,lx:rx+1][visible]=z[visible]
    return Image.fromarray(im)


def blockbench(model):
    """Export local-space pivots and UV cuboids as a native, editable model."""
    # Generic Blockbench supports cuboids in world rest position. Bone pivots
    # remain hierarchical; world offsets avoid importing child boxes at origin.
    elements=[];groups={};outliner=[]
    pivots={"root":np.zeros(3)}
    for p in model.parts:
        # Blockbench group rotation recursively transforms descendants. Use the
        # unrotated global rest positions, then convert MC +Y down into BB +Y up.
        pivot=pivots[p["parent"]]+np.array(p["pivot"]);pivots[p["name"]]=pivot
        bbpivot=[float(pivot[0]),float(24-pivot[1]),float(pivot[2])]
        xrot,yrot,zrot=p["rotation"]
        group={"name":p["name"],"origin":bbpivot,"rotation":[math.degrees(-xrot),math.degrees(yrot),math.degrees(-zrot)],"uuid":hashlib.md5((model.id+p["name"]).encode()).hexdigest(),"children":[]}
        groups[p["name"]]=group
        (outliner if p["parent"]=="root" else groups[p["parent"]]["children"]).append(group)
        for idx,b in enumerate(p["boxes"]):
            xyz=np.array(b["position"])+pivot;size=np.array(b["size"])
            uid=hashlib.md5((model.id+p["name"]+str(idx)).encode()).hexdigest()
            faces={name:{"uv":[int(x),int(y),int(x+w),int(y+h)],"texture":0}for name,(x,y,w,h)in cube_uv_faces(*b["uv"],*size).items()}
            elements.append({"name":b["material"],"uuid":uid,"type":"cube","from":[float(xyz[0]),float(24-xyz[1]-size[1]),float(xyz[2])],"to":[float(xyz[0]+size[0]),float(24-xyz[1]),float(xyz[2]+size[2])],"origin":bbpivot,"faces":faces})
            group["children"].append(uid)
    return {"meta":{"format_version":"4.10","model_format":"free","box_uv":False},"name":model.id,"resolution":{"width":SIZE,"height":SIZE},"elements":elements,"outliner":outliner,"textures":[{"name":model.id+".png","path":str(TEXTURES/(model.id+".png")),"id":"0","width":SIZE,"height":SIZE,"uv_width":SIZE,"uv_height":SIZE}],"display":{}}


def main():
    models=build_models();SOURCE.mkdir(parents=True,exist_ok=True);PREVIEW.mkdir(parents=True,exist_ok=True)
    results=[]
    for model in models:
        uv=pack_uv(model);skin=paint_texture(model,uv)
        data={"id":model.id,"name":model.name,"animation":model.animation,"shadow":model.shadow,"texture_size":[SIZE,SIZE],"coordinate_system":"Minecraft ModelPart: +Y down, forward -Z, ground y=24","parts":model.parts}
        (SOURCE/(model.id+".json")).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding="utf-8")
        (SOURCE/(model.id+".bbmodel")).write_text(json.dumps(blockbench(model),ensure_ascii=False,indent=2),encoding="utf-8")
        preview=render_preview(model,skin)
        preview.save(PREVIEW/(model.id+".png"))
        alternate=render_preview(model,skin,yaw=2.4)
        alternate.save(PREVIEW/(model.id+"_back.png"))
        results.append({"species":model.id,"parts":len(model.parts),"boxes":sum(len(p["boxes"])for p in model.parts),"uv_islands":len(uv),"texture_size":SIZE,"uv_valid":True,"unique_geometry_sha256":hashlib.sha256(json.dumps(model.parts,sort_keys=True).encode()).hexdigest()})
    generate_java(models)
    # Original 3D render atlas; deliberately labelled to avoid claiming client QA.
    fontpath=Path("C:/Windows/Fonts/msyh.ttc")
    font=ImageFont.truetype(str(fontpath),24) if fontpath.exists() else ImageFont.load_default()
    titlefont=ImageFont.truetype(str(fontpath),34) if fontpath.exists() else font
    thumb=300;cellh=350;cols=4;rows=math.ceil(len(models)/cols)
    sheet=Image.new("RGB",(cols*thumb,rows*cellh+100),(235,231,219));dr=ImageDraw.Draw(sheet)
    dr.text((24,14),"RouchNao · 23种独立异兽模型",font=titlefont,fill=(47,63,62))
    dr.text((24,62),"实际 cuboid 数据离线渲染 · 非游戏截图",font=font,fill=(91,100,91))
    for i,model in enumerate(models):
        x=(i%cols)*thumb;y=(i//cols)*cellh+100
        pic=Image.open(PREVIEW/(model.id+".png")).convert("RGB").resize((thumb,thumb),Image.Resampling.NEAREST)
        sheet.paste(pic,(x,y));dr.text((x+12,y+thumb+8),model.name,font=font,fill=(48,62,60))
    sheet.save(PREVIEW/"all_23_species.png")
    (PREVIEW/"validation.json").write_text(json.dumps({"species_count":len(results),"geometry_count":len(set(r["unique_geometry_sha256"]for r in results)),"total_boxes":sum(r["boxes"]for r in results),"models":results},indent=2),encoding="utf-8")
    print(json.dumps({"species":len(models),"total_boxes":sum(r["boxes"]for r in results),"atlas":str(PREVIEW/"all_23_species.png")},ensure_ascii=False))


if __name__=="__main__":main()
