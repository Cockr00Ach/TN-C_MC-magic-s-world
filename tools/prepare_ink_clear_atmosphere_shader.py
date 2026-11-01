"""Build a dimension-scoped Complementary r5.6.1 variant; never modify the source pack."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

FLAG = "TNC_INK_CLEAR_ATMOSPHERE"
DISABLED = (
    "VL_CLOUDS_ACTIVE", "CLOUDS_REIMAGINED", "CLOUDS_UNBOUND", "CLOUD_SHADOWS",
    "ATMOSPHERIC_FOG", "BORDER_FOG", "CAVE_FOG", "BLOOM_FOG",
    "BLOOM_FOG_COMPOSITE1", "BLOOM_FOG_COMPOSITE3", "COLORED_LIGHT_FOG",
    "LIGHTSHAFTS_ACTIVE", "LIGHTSHAFT_SMOKE", "MOTION_BLUR_BLOOM_FOG_FIX",
)


def build(source: Path, output: Path):
    source = source.resolve()
    output = output.resolve()
    if output.exists() or source == output or source in output.parents:
        raise ValueError("Use a new output outside the original shader folder")
    original = {p.relative_to(source).as_posix(): p.read_bytes()
                for p in source.rglob("*") if p.is_file()}
    files = dict(original)
    common = "shaders/lib/common.glsl"
    dimensions = "shaders/dimension.properties"
    fog = "shaders/lib/atmospherics/fog/mainFog.glsl"
    properties = "shaders/shaders.properties"
    for key in (common, dimensions, fog, properties):
        if key not in files:
            raise ValueError(f"Unsupported Complementary layout: missing {key}")
    if any(FLAG.encode() in files[key] for key in (common, dimensions, fog)):
        raise ValueError("Input already contains the ink atmosphere patch")
    routing = files[dimensions].decode("utf-8")
    if "dimension.world0=*" not in routing or "tnc:ink_realm" in routing:
        raise ValueError("Unexpected existing custom dimension routing")
    files[dimensions] = (routing + "\n# TN-C: only the ink realm uses these atmosphere-free programs.\n"
                         "dimension.worldink=tnc:ink_realm\n").encode()
    properties_text = files[properties].decode("utf-8")
    properties_text, mapped = re.subn(r"^([ \t]*program\.world0/[^\r\n]+)",
        lambda match: match[0] + "\n" + match[0].replace("program.world0/", "program.worldink/", 1),
        properties_text, flags=re.MULTILINE)
    if mapped != 3:
        raise ValueError("Unexpected program switches; require Complementary r5.6.1")
    files[properties] = properties_text.encode()
    defines = "\n\n#ifdef " + FLAG + "\n"
    defines += "\n".join("#undef " + feature for feature in DISABLED)
    defines += "\n#undef LIGHTSHAFT_QUALI_DEFINE\n#define LIGHTSHAFT_QUALI_DEFINE 0\n#endif\n"
    files[common] += defines.encode()
    fog_source = files[fog].decode("utf-8")
    signature = r"(void DoFog\([^\n]*\)\s*\{)"
    fog_source, count = re.subn(signature, r"\1\n#ifdef " + FLAG + "\n    return;\n#endif", fog_source)
    if count != 1:
        raise ValueError("Unexpected DoFog signature; refuse an unverified patch")
    files[fog] = fog_source.encode()
    programs = 0
    for key, data in original.items():
        if not key.startswith("shaders/world0/"):
            continue
        if not key.endswith((".vsh", ".fsh", ".gsh", ".csh", ".tcs", ".tes")):
            raise ValueError(f"Unexpected world0 asset: {key}")
        text = data.decode("utf-8")
        if not text.startswith("#version ") or FLAG in text:
            raise ValueError(f"Unexpected shader entry point: {key}")
        first, rest = text.split("\n", 1)
        files[key.replace("shaders/world0/", "shaders/worldink/", 1)] = (
            first + "\n#define " + FLAG + "\n" + rest).encode()
        programs += 1
    if not programs:
        raise ValueError("No Overworld programs found")
    changed = sorted(k for k in original if original[k] != files[k])
    if changed != sorted([common, dimensions, fog, properties]):
        raise AssertionError("Unexpected changes to original programs")
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for key, data in sorted(files.items()):
            archive.writestr(key, data)
    report = {"source": str(source), "output": str(output), "customDimension": "tnc:ink_realm",
              "changedSharedFiles": changed, "newEntryPoints": programs,
              "originalWorldProgramsUnchanged": all(original[k] == files[k] for k in original if "/world" in k),
              "sourceUnchanged": all((source / k).read_bytes() == data for k, data in original.items()),
              "sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
              "runtimeShaderCompilationVerified": False}
    report_path = output.with_suffix(".json")
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    print(json.dumps(build(args.source, args.output), ensure_ascii=False, indent=2))
