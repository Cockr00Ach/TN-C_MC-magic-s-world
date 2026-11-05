"""Trim lightning spell particle counts (2026-11-05 author: "粒子可以删去很多").

Rules:
  * drop the pure filler particles (witch / squid_ink / smoke_medium)
  * cast particles   -> *0.5, cap 12
  * travel particles -> *0.12, cap 16
  * impact/area      -> *0.14, cap 60 / 120
Also: heavenly_thunder's 30 meteors -> 10, and its old flash model is made
invisible (scale 0.05) because the Java side now throws real bolts.

Usage: python trim_particles.py <file...>
"""
import json
import sys

DROP = {"minecraft:witch", "minecraft:squid_ink", "spell_engine:smoke_medium"}


def trim(entries, scale, cap):
    out = []
    for e in entries:
        if e.get("particle_id") in DROP:
            continue
        if "count" in e:
            c = int(round(float(e["count"]) * scale))
            e["count"] = max(3, min(cap, c))
        out.append(e)
    return out


def walk(node, stats):
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "particles" and isinstance(value, list):
                scale, cap = 0.5, 12
                if stats.get("in_travel"):
                    scale, cap = 0.12, 16
                elif stats.get("in_impact"):
                    scale, cap = 0.14, 120
                node[key] = trim(value, scale, cap)
            elif key == "travel_particles" and isinstance(value, list):
                stats["in_travel"] = True
                node[key] = trim(value, 0.12, 16)
                stats["in_travel"] = False
            elif key in ("impact", "area_impact", "cast", "release"):
                if key in ("impact", "area_impact"):
                    stats["in_impact"] = True
                walk(value, stats)
                if key in ("impact", "area_impact"):
                    stats["in_impact"] = False
            else:
                walk(value, stats)
    elif isinstance(node, list):
        for item in node:
            walk(item, stats)


def main(paths):
    for path in paths:
        with open(path, "r", encoding="utf-8") as fh:
            data = json.load(fh)
        walk(data, {})
        name = path.replace("\\", "/").rsplit("/", 1)[-1]
        if name == "heavenly_thunder.json":
            meteor = data["release"]["target"]["meteor"]
            meteor["launch_properties"]["extra_launch_count"] = 9
            model = meteor["projectile"]["client_data"]["model"]
            model["scale"] = 0.05          # invisible: Java throws the real bolts now
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(data, fh, ensure_ascii=False, indent=2)
            fh.write("\n")
        print("trimmed", path)


if __name__ == "__main__":
    main(sys.argv[1:])
