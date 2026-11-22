"""Reproduce the Skyhaven field using a local Forge 1.20.1 dev classpath, JDK 17 and NumPy.

python tools/skyhaven_rtf/sample.py RTF-0.0.6.jar output-dir --gradle-home GRADLE_USER_HOME
The ReTerraForged jar is needed here only. The output is consumed by skyhaven_alpine.fit_survey.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

import numpy as np

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parent.parent


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def forge_classpath(cache, runtime_list):
    forge_gradle = cache / "forge_gradle"
    joined = ("mcp_repo/net/minecraft/joined/1.20.1-20230612.114412/"
              "joined-1.20.1-20230612.114412-srg.jar")
    forge = ("minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.22/"
             "forge-1.20.1-47.4.22-srg.jar")
    libraries = [forge_gradle / joined, forge_gradle / forge]
    for jar in libraries:
        if not jar.is_file():
            raise FileNotFoundError(f"ForgeGradle SRG artifact missing: {jar}")
    for value in runtime_list.read_text(encoding="utf-8-sig").splitlines():
        posix_path = value.strip().replace("\\", "/")
        jar = Path(posix_path)
        if not jar.exists() and "/caches/" in posix_path:
            jar = cache / posix_path.split("/caches/", 1)[1]
        if jar.exists():
            libraries.append(jar)
    return libraries


def classpath_jar(destination, entries):
    # File URIs keep paths with spaces and non-ASCII names portable on JDK 17.
    urls = []
    for path in entries:
        url = path.resolve().as_uri()
        urls.append(url + "/" if path.is_dir() and not url.endswith("/") else url)
    attribute = "Class-Path: " + " ".join(urls)
    lines = [attribute[:70]]
    lines.extend(" " + attribute[i:i + 69] for i in range(70, len(attribute), 69))
    manifest = "\r\n".join(["Manifest-Version: 1.0", *lines, "", ""])
    with zipfile.ZipFile(destination, "w") as archive:
        archive.writestr("META-INF/MANIFEST.MF", manifest.encode("ascii"))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("rtf_jar", type=Path)
    parser.add_argument("output_directory", type=Path)
    parser.add_argument("--gradle-home", type=Path, default=Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")))
    parser.add_argument("--runtime-classpath", type=Path, default=PROJECT / "build/classpath/runClient_minecraftClasspath.txt")
    parser.add_argument("--jdk", type=Path, default=os.environ.get("JAVA_HOME"), help="JDK 17 root (otherwise uses JAVA_HOME or PATH)")
    args = parser.parse_args()
    manifest = json.loads((HERE / "manifest.json").read_text(encoding="utf8"))
    if digest(args.rtf_jar) != manifest["jarSHA256"]:
        raise ValueError("Expected the ReTerraForged Forge 1.20.1 0.0.6 JAR identified by manifest.json")
    java = args.jdk / "bin/java" if args.jdk else "java"
    javac = args.jdk / "bin/javac" if args.jdk else "javac"
    output = args.output_directory.resolve()
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output) as name:
        temp = Path(name)
        classes = temp / "classes"
        classes.mkdir()
        libraries = forge_classpath(args.gradle_home / "caches", args.runtime_classpath)
        cp = temp / "classpath.jar"
        classpath_jar(cp, [classes, args.rtf_jar, *libraries])
        subprocess.run([javac, "-proc:none", "-encoding", "UTF-8", "-classpath", cp, "-d", classes, HERE / "RTFProbe.java"], cwd=temp, check=True)
        raw = temp / "terrain.bin"
        parameters = [str(manifest[k]) for k in ("seed", "x0", "z0", "width", "depth", "spacing")]
        subprocess.run([java, "-XX:ActiveProcessorCount=3", "-Xmx700m", "-classpath", cp, "net.minecraft.core.RTFProbe", *parameters, raw], cwd=temp, check=True)
        if digest(raw) != manifest["rawSHA256"]:
            raise ValueError("Native ReTerraForged output differs from the approved field")
        fields = np.fromfile(raw, dtype=[("height", ">i2"), ("terrain", ">i2"), ("mask", ">f4")])
        grid = fields.reshape(manifest["depth"], manifest["width"])
        target = temp / "uplands-field.npz"
        np.savez_compressed(target, height=grid["height"].astype("int16"), terrain=grid["terrain"].astype("int16"),
                            river=grid["terrain"] == 5, river_mask=grid["mask"].astype("float32"),
                            origin_x=manifest["x0"], origin_z=manifest["z0"], seed=manifest["seed"], spacing=manifest["spacing"])
        target.replace(output / target.name)
    exported = output / "manifest.json"
    if exported != HERE / "manifest.json":
        manifest["fieldSHA256"] = digest(output / "uplands-field.npz")
        exported.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf8")
    print(output / "uplands-field.npz")


if __name__ == "__main__":
    main()

