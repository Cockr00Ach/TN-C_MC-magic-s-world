# -*- coding: utf-8 -*-
"""
check_tavern_palette.py  --  list every block the tavern blueprint needs, and which
                            of them this modpack does not have.

WHY
===
The tavern interior is installed from a compressed-NBT blueprint
(data/tnc/tavern/interior_v1.nbt) whose "Palette" lists block ids. TavernUpgrade.load
refuses to install anything if a single palette entry is not in the block registry:

    [TN-C Tavern] update paused: <missing block id>

That is exactly what happened in game: the blueprint wants conquest:round_barrel and
this pack has no "conquest" namespace. This script finds ALL such mismatches up front
instead of discovering them one restart at a time.

It reads the blockstate files inside every mod jar to decide what exists, so it needs
no running game.

USAGE
=====
    python tools/check_tavern_palette.py --live "<instance path>"
    python tools/check_tavern_palette.py --live "<instance>" --blueprint data/tnc/tavern/interior_v1.nbt

Pure ASCII output.
"""

import argparse
import glob
import gzip
import os
import re
import struct
import sys
import zipfile

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE = 0, 1, 2, 3, 4, 5, 6
TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = 7, 8, 9, 10, 11, 12


class Reader:
    def __init__(self, data):
        self.d = data
        self.i = 0

    def u1(self):
        v = self.d[self.i]
        self.i += 1
        return v

    def i1(self):
        v = struct.unpack_from(">b", self.d, self.i)[0]
        self.i += 1
        return v

    def i2(self):
        v = struct.unpack_from(">h", self.d, self.i)[0]
        self.i += 2
        return v

    def i4(self):
        v = struct.unpack_from(">i", self.d, self.i)[0]
        self.i += 4
        return v

    def i8(self):
        v = struct.unpack_from(">q", self.d, self.i)[0]
        self.i += 8
        return v

    def f4(self):
        v = struct.unpack_from(">f", self.d, self.i)[0]
        self.i += 4
        return v

    def f8(self):
        v = struct.unpack_from(">d", self.d, self.i)[0]
        self.i += 8
        return v

    def s(self):
        n = struct.unpack_from(">H", self.d, self.i)[0]
        self.i += 2
        v = self.d[self.i:self.i + n].decode("utf-8", "replace")
        self.i += n
        return v

    def payload(self, t):
        if t == TAG_BYTE:
            return self.i1()
        if t == TAG_SHORT:
            return self.i2()
        if t == TAG_INT:
            return self.i4()
        if t == TAG_LONG:
            return self.i8()
        if t == TAG_FLOAT:
            return self.f4()
        if t == TAG_DOUBLE:
            return self.f8()
        if t == TAG_BYTE_ARRAY:
            n = self.i4()
            v = self.d[self.i:self.i + n]
            self.i += n
            return v
        if t == TAG_STRING:
            return self.s()
        if t == TAG_LIST:
            et = self.u1()
            n = self.i4()
            return [self.payload(et) for _ in range(n)]
        if t == TAG_COMPOUND:
            out = {}
            while True:
                tt = self.u1()
                if tt == TAG_END:
                    return out
                name = self.s()
                out[name] = self.payload(tt)
        if t == TAG_INT_ARRAY:
            n = self.i4()
            return [self.i4() for _ in range(n)]
        if t == TAG_LONG_ARRAY:
            n = self.i4()
            return [self.i8() for _ in range(n)]
        raise ValueError("unknown tag %d" % t)


def read_nbt(path):
    with open(path, "rb") as f:
        raw = f.read()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    r = Reader(raw)
    t = r.u1()
    name = r.s()
    return name, r.payload(t)


def read_nbt_bytes(raw):
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    r = Reader(raw)
    t = r.u1()
    r.s()
    return r.payload(t)


def block_exists(instance, block_id):
    ns, _, path = block_id.partition(":")
    if ns == "minecraft":
        return True
    want = "assets/%s/blockstates/%s.json" % (ns, path)
    for jar in glob.glob(os.path.join(instance, "mods", "**", "*.jar"), recursive=True):
        try:
            with zipfile.ZipFile(jar) as z:
                if want in z.namelist():
                    return True
        except Exception:
            continue
    # datapacks / openloader
    for root in ("config/openloader/resources", "kubejs"):
        p = os.path.join(instance, root)
        for dp, _d, files in os.walk(p) if os.path.isdir(p) else []:
            if want.split("/")[-1] in files and want.replace("/", os.sep) in os.path.join(dp, want.split("/")[-1]):
                return True
    return False


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--live", required=True)
    ap.add_argument("--blueprint", action="append", default=None,
                    help="blueprint path inside the jar (repeatable)")
    ap.add_argument("--jar", default=os.path.join("build", "libs", "tnc-1.0.0.jar"))
    args = ap.parse_args(argv[1:])

    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    jar = args.jar if os.path.isabs(args.jar) else os.path.join(repo, args.jar)
    if not os.path.isfile(jar):
        print("ERROR: no jar at %s" % jar)
        return 1

    blueprints = args.blueprint or ["data/tnc/tavern/interior_v1.nbt",
                                    "data/tnc/tavern/basement_v1.nbt"]
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for bp in blueprints:
            if bp not in names:
                print("!! %s not in the jar" % bp)
                continue
            root = read_nbt_bytes(z.read(bp))
            palette = root.get("Palette", [])
            ids = []
            for entry in palette:
                name = entry.get("Name") if isinstance(entry, dict) else None
                if name and name not in ids:
                    ids.append(name)
            missing = [b for b in ids if not block_exists(args.live, b)]
            print("== %s ==" % bp)
            print("   Revision=%s Bounds=%s palette=%d blocks (%d distinct)"
                  % (root.get("Revision"), root.get("Bounds"), len(palette), len(ids)))
            print("   missing in this pack: %d" % len(missing))
            for b in missing:
                print("      [X] %s" % b)
            others = [b for b in ids if not b.startswith(("minecraft:", "tnc:"))]
            print("   third-party blocks used: %d" % len(others))
            for b in others:
                print("      %s %s" % ("ok " if b not in missing else "XX ", b))
            print("")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
