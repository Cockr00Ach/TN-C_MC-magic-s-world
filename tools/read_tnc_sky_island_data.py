#!/usr/bin/env python
"""
read_tnc_sky_island_data.py -- dump the TN-C sky-island checkpoint from a save.

The saved data file is plain gzipped NBT, so the values can be read without
launching the game.  Useful when quest objectives need REAL coordinates: the
sky island is generated per save, so its centre / portal / arrival positions
differ in every world and cannot be hardcoded in a quest JSON.

Usage:
    python tools/read_tnc_sky_island_data.py "<save folder>"
    python tools/read_tnc_sky_island_data.py "<save folder>" --file tnc_npc_placements.dat

ASCII only: this console is cp936 and CJK would come out as mojibake.
"""

import argparse
import gzip
import os
import struct
import sys

TAG_NAMES = {
    0: "End", 1: "Byte", 2: "Short", 3: "Int", 4: "Long", 5: "Float",
    6: "Double", 7: "ByteArray", 8: "String", 9: "List", 10: "Compound",
    11: "IntArray", 12: "LongArray",
}


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

    def string(self):
        n = struct.unpack_from(">H", self.d, self.i)[0]
        self.i += 2
        s = self.d[self.i:self.i + n].decode("utf-8", "replace")
        self.i += n
        return s

    def payload(self, tag):
        if tag == 1:
            return self.i1()
        if tag == 2:
            return self.i2()
        if tag == 3:
            return self.i4()
        if tag == 4:
            return self.i8()
        if tag == 5:
            return self.f4()
        if tag == 6:
            return self.f8()
        if tag == 7:                      # ByteArray
            n = self.i4()
            v = list(self.d[self.i:self.i + n])
            self.i += n
            return v
        if tag == 8:
            return self.string()
        if tag == 9:                      # List
            inner = self.u1()
            n = self.i4()
            return [self.payload(inner) for _ in range(n)]
        if tag == 10:                     # Compound
            out = {}
            while True:
                t = self.u1()
                if t == 0:
                    break
                name = self.string()
                out[name] = self.payload(t)
            return out
        if tag == 11:                     # IntArray
            n = self.i4()
            return [self.i4() for _ in range(n)]
        if tag == 12:                     # LongArray
            n = self.i4()
            return [self.i8() for _ in range(n)]
        raise ValueError("unknown tag %d at %d" % (tag, self.i))


def load(path):
    with open(path, "rb") as fh:
        raw = fh.read()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    r = Reader(raw)
    tag = r.u1()
    if tag != 10:
        raise ValueError("root tag is %s, expected Compound" % TAG_NAMES.get(tag, tag))
    r.string()                            # root name (empty)
    return r.payload(10)


def show(node, indent=0, key=""):
    pad = "  " * indent
    if isinstance(node, dict):
        print("%s%s:" % (pad, key))
        for k, v in node.items():
            show(v, indent + 1, k)
    elif isinstance(node, list):
        if node and isinstance(node[0], (dict, list)):
            print("%s%s: [%d]" % (pad, key, len(node)))
            for n, item in enumerate(node[:8]):
                show(item, indent + 1, "[%d]" % n)
        else:
            print("%s%s: %s" % (pad, key, node))
    else:
        print("%s%s: %s" % (pad, key, node))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("save", help="path to the world save folder")
    ap.add_argument("--file", default="tnc_sky_island_v5.dat")
    args = ap.parse_args()

    path = os.path.join(args.save, "data", args.file)
    if not os.path.exists(path):
        print("not found: %s" % path)
        return 1

    data = load(path)
    print("== %s ==" % args.file)
    show(data)
    return 0


if __name__ == "__main__":
    sys.exit(main())
