# -*- coding: utf-8 -*-
"""Verify the embedded base64 payloads in TNDragonRenderData.java decode to the real assets."""
import base64
import hashlib
import os
import re

JAVA = "src/main/java/com/tnc/tnc/light/client/TNDragonRenderData.java"
PNGS = {
    "TEXTURE_LIGHT_B64": "src/main/resources/assets/tnc/textures/entity/dragon_bedrock.png",
    "TEXTURE_DARK_B64": "src/main/resources/assets/tnc/textures/entity/dragon_dark_bedrock.png",
}


def main():
    src = open(JAVA, encoding="utf-8").read()
    for name in ("GEOMETRY_B64", "TEXTURE_LIGHT_B64", "TEXTURE_DARK_B64"):
        m = re.search(name + r"\s*=\s*(.*?);", src, re.S)
        if not m:
            print(name, "NOT FOUND")
            continue
        joined = "".join(re.findall(r'"([^"]*)"', m.group(1)))
        raw = base64.b64decode(joined)
        sha = hashlib.sha256(raw).hexdigest()[:16]
        line = "%-20s parts=%-4d b64=%-7d bytes=%-7d sha=%s" % (
            name, len(re.findall(r'"([^"]*)"', m.group(1))), len(joined), len(raw), sha)
        if name in PNGS and os.path.exists(PNGS[name]):
            want = hashlib.sha256(open(PNGS[name], "rb").read()).hexdigest()[:16]
            line += "   matches png: %s" % (want == sha)
        print(line)
    # geometry: 100 boxes x (6 + 24) floats x 4 bytes
    m = re.search(r"BOX_COUNT\s*=\s*(\d+)", src)
    print("BOX_COUNT =", m.group(1) if m else "?")


if __name__ == "__main__":
    main()
