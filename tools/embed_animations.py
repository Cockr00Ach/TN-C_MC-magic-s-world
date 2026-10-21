"""把 Bedrock 动画 json 内嵌进 .bbmodel，省掉在 Blockbench 里手动 Import 的步骤。

用法：
    python tools/embed_animations.py <in.bbmodel> <animation.json> [out.bbmodel]

映射规则（Bedrock -> Blockbench 工程格式）：
    animations.<name>.loop              -> "loop": "loop" / "once"
    animations.<name>.animation_length  -> "length"
    animations.<name>.bones.<bone>.<ch> -> animators[bone_uuid].keyframes
        键是秒，值是 [x, y, z]；channel 取 rotation / position / scale
    jaw / plate_l / plate_r 这类一阶段没有的骨骼会被自动跳过（不报错）。
"""

import io
import json
import sys
import uuid


CHANNELS = ("rotation", "position", "scale")


def stable_uuid(seed: str, ns: str = "tnc:kf:") -> str:
    return uuid.uuid5(uuid.NAMESPACE_URL, ns + seed).hex


def collect_bones(doc):
    """骨骼名 -> uuid"""
    out = {}

    def walk(node):
        if isinstance(node, str):
            return
        out[node["name"]] = node["uuid"]
        for ch in node.get("children", []):
            walk(ch)

    for n in doc.get("outliner", []):
        walk(n)
    return out


def point(value):
    if isinstance(value, dict):
        # Catmull-Rom 的 pre/post 写法，取 post（不常见，兜底）
        for k in ("post", "pre"):
            if k in value:
                return point(value[k])
        return [0, 0, 0]
    if isinstance(value, (int, float)):
        return [value, value, value]
    return [float(value[0]), float(value[1]), float(value[2])]


def embed(bb_path, anim_path, out_path=None):
    doc = json.load(io.open(bb_path, encoding="utf-8"))
    anims = json.load(io.open(anim_path, encoding="utf-8"))
    bones = collect_bones(doc)
    result = []
    skipped_bones = set()

    for name, anim in anims.get("animations", {}).items():
        length = float(anim.get("animation_length", 1.0) or 1.0)
        loop = "loop" if anim.get("loop") else "once"
        animators = {}
        for bone_name, channels in (anim.get("bones") or {}).items():
            if bone_name not in bones:
                skipped_bones.add(bone_name)
                continue
            kfs = []
            for channel in CHANNELS:
                raw = channels.get(channel)
                if not raw:
                    continue
                for t, val in raw.items():
                    p = point(val)
                    kfs.append(
                        {
                            "channel": channel,
                            "time": float(t),
                            "interpolation": "linear",
                            "data_points": [{"x": p[0], "y": p[1], "z": p[2]}],
                            "uuid": stable_uuid("%s|%s|%s|%s" % (name, bone_name, channel, t)),
                            "color": -1,
                        }
                    )
            if not kfs:
                continue
            kfs.sort(key=lambda k: (k["channel"], k["time"]))
            animators[bones[bone_name]] = {
                "name": bone_name,
                "type": "bone",
                "keyframes": kfs,
            }
        result.append(
            {
                "name": name,
                "loop": loop,
                "override": False,
                "length": length,
                "snapping": 20,
                "selected": False,
                "anim_time_update": "",
                "blend_weight": "",
                "start_delay": "",
                "loop_delay": "",
                "animators": animators,
            }
        )

    doc["animations"] = result
    io.open(out_path or bb_path, "w", encoding="utf-8", newline="\n").write(
        json.dumps(doc, ensure_ascii=False, indent=1)
    )
    return result, skipped_bones


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print(__doc__)
        raise SystemExit(2)
    res, skipped = embed(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
    for a in res:
        kf = sum(len(b["keyframes"]) for b in a["animators"].values())
        print("  %-12s loop=%-4s len=%.2fs bones=%d keyframes=%d"
              % (a["name"], a["loop"], a["length"], len(a["animators"]), kf))
    if skipped:
        print("  skipped (bone not in model):", ", ".join(sorted(skipped)))
