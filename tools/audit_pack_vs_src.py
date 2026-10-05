# -*- coding: utf-8 -*-
"""
audit_pack_vs_src.py  --  does the jar in the game actually contain the whole repo?

WHY
===
"identical: True" only means "the copy in mods/ equals one build output".
It does NOT mean "the jar has everything the repo has". And a check that compares
the jar against build/resources/main can lie: build/ is itself a stale copy, so a
renamed or newly added source file shows up as "not missing" while the jar really
does not have it.

This script therefore compares the jar against **src/main/** only:
  1. does every top-level type declared in src/main/java have a .class in the jar?
  2. does every file under src/main/resources exist in the jar, byte for byte?
  3. are build/libs and the game instance's jar the same bytes?

USAGE
=====
    python tools/audit_pack_vs_src.py
    python tools/audit_pack_vs_src.py --pack-dir "<instance>\\mods"
    python tools/audit_pack_vs_src.py --jar build/libs/tnc-1.0.0.jar

Exit code 0 = in sync, 1 = drift found.

Pure ASCII (repo rule).
"""

import argparse
import hashlib
import io
import os
import re
import sys
import zipfile

JAR_NAME = "tnc-1.0.0.jar"
INSTANCE = "\u5143\u7d20\u89c9\u91921.4.3-\u9b54\u6539\u7248-20260915"

# files that are dev-only and deliberately not shipped
RESOURCE_SKIP = (".gitignore", ".bbmodel", ".psd", ".xcf", ".bak", ".pyc")
# files Gradle REWRITES on the way into the jar, so a byte compare is meaningless
RESOURCE_TRANSFORMED = ("META-INF/mods.toml", "pack.mcmeta")


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def find_pack_jar():
    roots = []
    env = os.environ.get("TNC_LIVE_ROOT")
    if env:
        roots.append(env)
    home = os.path.expanduser("~")
    roots += [os.path.join(home, "AppData", "Roaming"),
              os.path.join(home, "AppData", "Roaming", ".minecraft"),
              os.path.join(home, ".minecraft")]
    for root in roots:
        if not os.path.isdir(root):
            continue
        for cand in (os.path.join(root, ".minecraft", "versions", INSTANCE, "mods", JAR_NAME),
                     os.path.join(root, "versions", INSTANCE, "mods", JAR_NAME)):
            if os.path.isfile(cand):
                return cand
        try:
            subs = os.listdir(root)
        except OSError:
            continue
        for sub in subs:
            cand = os.path.join(root, sub, ".minecraft", "versions", INSTANCE, "mods", JAR_NAME)
            if os.path.isfile(cand):
                return cand
    return None


def sha(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def top_level_types(text):
    """
    Names of the TOP-LEVEL types in a compilation unit.

    A naive regex is wrong here: TNMod.java declares "ClientModEvents" as a nested
    type, so the first match would be reported as com/tnc/tnc/ClientModEvents.class,
    which does not exist (the real path is com/tnc/tnc/TNMod$ClientModEvents.class).
    So walk the text tracking brace depth and accept declarations at depth 0 only.
    """
    depth = 0
    top = []
    for m in re.finditer(r"[{}]|\b(?:class|interface|enum|record)\s+([A-Za-z_$][\w$]*)", text):
        token = m.group(0)
        if token == "{":
            depth += 1
        elif token == "}":
            depth = max(0, depth - 1)
        elif depth == 0:
            top.append(m.group(1))
    return top


def expected_classes(repo):
    root = os.path.join(repo, "src", "main", "java")
    out = []
    for dirpath, _dirs, files in os.walk(root):
        for fn in files:
            if not fn.endswith(".java"):
                continue
            with io.open(os.path.join(dirpath, fn), encoding="utf-8", errors="replace") as f:
                text = f.read()
            text = re.sub(r"//[^\n]*", "", text)
            text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
            pkg = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
            pkg_path = (pkg.group(1).replace(".", "/") + "/") if pkg else ""
            for n in top_level_types(text):
                out.append(pkg_path + n + ".class")
    return sorted(set(out))


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--pack-dir", default=None,
                    help="the instance's mods folder (or the jar path itself)")
    ap.add_argument("--jar", default=None, help="jar to audit (default: build/libs)")
    args = ap.parse_args(argv[1:])

    repo = repo_root()
    jar = args.jar or os.path.join(repo, "build", "libs", JAR_NAME)
    if not os.path.isfile(jar):
        print("ERROR: no jar at %s -- run gradlew build first" % jar)
        return 1

    pack_jar = args.pack_dir or find_pack_jar()
    if pack_jar and os.path.isdir(pack_jar):
        pack_jar = os.path.join(pack_jar, JAR_NAME)
    if pack_jar and not os.path.isfile(pack_jar):
        pack_jar = None

    problems = []
    print("jar under audit : %s" % jar)
    print("jar in the game : %s" % (pack_jar or "(not found)"))

    if pack_jar:
        same = sha(jar) == sha(pack_jar)
        print("same bytes      : %s" % ("YES" if same else "NO"))
        if not same:
            problems.append("the jar installed in the game is not this jar "
                            "(%d vs %d bytes) -- install it, or find out who rebuilt"
                            % (os.path.getsize(jar), os.path.getsize(pack_jar)))

    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())

        exp_cls = expected_classes(repo)
        missing_cls = [c for c in exp_cls if c not in names]
        print("")
        print("classes from src/main/java  : %d expected, %d missing" % (len(exp_cls), len(missing_cls)))
        for c in missing_cls[:20]:
            print("   [X] %s" % c)

        res_root = os.path.join(repo, "src", "main", "resources")
        exp_res, missing_res, differing, transformed = [], [], [], []
        for dirpath, _dirs, files in os.walk(res_root):
            for fn in files:
                if fn.endswith(RESOURCE_SKIP):
                    continue
                rel = os.path.relpath(os.path.join(dirpath, fn), res_root).replace("\\", "/")
                exp_res.append(rel)
        for rel in exp_res:
            if rel in RESOURCE_TRANSFORMED:
                transformed.append(rel)
                continue
            if rel not in names:
                missing_res.append(rel)
                continue
            with io.open(os.path.join(res_root, rel), "rb") as f:
                if z.read(rel) != f.read():
                    differing.append(rel)
        print("resources from src/main/resources : %d expected, %d missing, %d stale"
              % (len(exp_res), len(missing_res), len(differing)))
        for rel in missing_res[:20]:
            print("   [X] missing: %s" % rel)
        for rel in differing[:20]:
            print("   [X] stale  : %s" % rel)
        if transformed:
            print("   (skipped, Gradle rewrites): %s" % ", ".join(transformed))

    if missing_cls:
        problems.append("%d class(es) from the sources are not in the jar" % len(missing_cls))
    if missing_res:
        problems.append("%d resource(s) never made it into the jar" % len(missing_res))
    if differing:
        problems.append("%d resource(s) in the jar are STALE (edited after the build)"
                        % len(differing))

    print("")
    if problems:
        print("DRIFT FOUND (%d):" % len(problems))
        for p in problems:
            print("  [X] " + p)
        return 1
    print("in sync: the audited jar contains every source file, byte for byte.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
