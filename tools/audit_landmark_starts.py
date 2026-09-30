"""Read-only comparison of native structure starts and TN-C construction jobs."""
import argparse
import json
from pathlib import Path
import anvil
from read_tnc_sky_island_data import load


def value(tag):
    if hasattr(tag, "value"):
        return tag.value
    return tag


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("save", type=Path)
    args = parser.parse_args()
    jobs_file = args.save / "data" / "tnc_landmarks_v1.dat"
    jobs = load(jobs_file).get("data", {}).get("Jobs", []) if jobs_file.exists() else []
    print(json.dumps({"jobs": jobs}, ensure_ascii=True))
    scanned = 0
    found = 0
    for path in sorted((args.save / "region").glob("r.*.*.mca")):
        if path.stat().st_size < 8192:
            continue
        region = anvil.Region.from_file(str(path))
        for z in range(32):
            for x in range(32):
                # Avoid asking the decoder to parse empty region-header slots.
                slot = 4 * (x + z * 32)
                if region.data[slot:slot + 4] == b"\0\0\0\0":
                    continue
                chunk = region.chunk_data(x, z)
                if chunk is None:
                    continue
                scanned += 1
                starts = chunk.get("structures", {}).get("starts", {})
                for name, start in starts.items():
                    if name not in ("tnc:stonecrest_fortress", "tnc:elden_coastal_castle", "tnc:gothic_cathedral", "tnc:end_pvp_island"):
                        continue
                    if value(start.get("id", "INVALID")) == "INVALID":
                        continue
                    children = start.get("Children", [])
                    print(json.dumps({"region": path.name, "structure": name,
                        "chunk": [value(chunk.get("xPos")), value(chunk.get("zPos"))],
                        "status": value(chunk.get("Status")), "pieces": len(children),
                        "first_piece": {k: value(v) for k, v in children[0].items()} if children else {}},
                        ensure_ascii=True, default=str))
                    found += 1
    print(json.dumps({"scanned_chunks": scanned, "found_starts": found}))


if __name__ == "__main__":
    main()
