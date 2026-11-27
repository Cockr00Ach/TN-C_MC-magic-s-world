"""Publish the team's first-party client build and pin the exact bytes for pull-time installs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.error
import urllib.request
import zipfile


ROOT = Path(__file__).resolve().parents[1]
REPO = "Cockr00Ach/TN-C_MC-magic-s-world"
API = f"https://api.github.com/repos/{REPO}"
INPUTS = ("src/main", "build.gradle", "gradle.properties", "settings.gradle", "gradle/wrapper")
FILENAME = "tnc-1.0.0.jar"


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args], text=True).strip()


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def credential():
    if token := os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN"):
        return token
    query = f"protocol=https\nhost=github.com\npath={REPO}.git\n\n"
    result = subprocess.run(["git", "-C", str(ROOT), "-c", "credential.interactive=false",
                             "credential", "fill"], input=query, text=True, capture_output=True,
                            env={**os.environ, "GIT_TERMINAL_PROMPT": "0", "GCM_INTERACTIVE": "never"})
    fields = dict(row.split("=", 1) for row in result.stdout.splitlines() if "=" in row)
    if result.returncode or not fields.get("password"):
        raise SystemExit("No GitHub release credential in GH_TOKEN or the Git credential manager.")
    return fields["password"]


def github(token, endpoint, method="GET", payload=None, length=None):
    headers = {"User-Agent": "TNC-team-release", "Accept": "application/vnd.github+json",
               "Authorization": f"Bearer {token}", "X-GitHub-Api-Version": "2022-11-28"}
    if isinstance(payload, dict):
        headers["Content-Type"] = "application/json"
        payload = json.dumps(payload).encode()
    elif length is not None:
        headers.update({"Content-Type": "application/java-archive", "Content-Length": str(length)})
    req = urllib.request.Request(endpoint, data=payload, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=300) as response:
        return None if response.status == 204 else json.load(response)


def publish(jar):
    sha = digest(jar)
    if git("status", "--porcelain", "--untracked-files=normal", "--", *INPUTS):
        raise SystemExit("Commit all build inputs before publishing.")
    commit = git("rev-parse", "HEAD")
    trees = {part: git("rev-parse", f"{commit}:{part}") for part in INPUTS}
    local_only = json.loads((ROOT / "tools/skyhaven-local-assets.json").read_text(encoding="utf-8-sig"))
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        if ("data/tnc/skyhaven/starter.zip" not in names or "META-INF/mods.toml" not in names or
                names.intersection(local_only) or
                any(n.startswith(("data/tnc/dungeon_gate_assets/", "data/tnc/structures/dungeon_gates/"))
                    and not n.endswith("/") for n in names)):
            raise SystemExit("Expected a public TN-C build without local-only music or gates.")
        marker = "META-INF/tnc-team-build.json"
        if marker not in names or json.loads(archive.read(marker))["sourceTrees"] != trees:
            raise SystemExit("Build provenance does not match the current committed inputs. Rebuild with -PteamPublicRelease.")
        for resource in (
            "assets/tnc/models/item/sky_halberd.json",
            "assets/tnc/models/item/arsenal_icons/ashwood_jian.json",
            "assets/tnc/models/item/weapon_surfaces/ashwood_jian.obj",
        ):
            source = ROOT / "src/main/resources" / resource
            if source.is_file() and (resource not in names or archive.read(resource) != source.read_bytes()):
                raise SystemExit(f"Packaged asset differs from the repository: {resource}")
    tag = f"team-client-{commit[:12]}"
    # Publish the source snapshot without making an incomplete runtime lock visible on main.
    git("push", "origin", f"{commit}:refs/tags/{tag}")
    token = credential()
    try:
        release = github(token, f"{API}/releases/tags/{tag}")
    except urllib.error.HTTPError as exc:
        if exc.code != 404:
            raise
        # GitHub may list a previous draft only in the authenticated release list.
        release = next((r for r in github(token, f"{API}/releases?per_page=100") if r["tag_name"] == tag), None)
        if release is None:
            release = github(token, f"{API}/releases", "POST", {
                "tag_name": tag, "target_commitish": commit, "draft": True,
                "name": f"TN-C team client · {commit[:12]}",
                "body": f"TN-C 客户端构建。源码 {commit}。SHA-256 \x60{sha}\x60。队友拉取后由 tools/sync-team-client.ps1 安装。",
            })
    asset = next((a for a in release["assets"] if a["name"] == FILENAME), None)
    if asset and release["draft"] and asset["state"] == "starter" and asset["size"] == 0:
        github(token, f"{API}/releases/assets/{asset['id']}", "DELETE")
        asset = None
    if asset is None:
        url = f"https://uploads.github.com/repos/{REPO}/releases/{release['id']}/assets?name={FILENAME}"
        print(f"Uploading {jar.stat().st_size:,} bytes to the GitHub release {tag}", flush=True)
        with jar.open("rb") as stream:
            asset = github(token, url, "POST", stream, jar.stat().st_size)
    if asset.get("digest") != f"sha256:{sha}" or asset["size"] != jar.stat().st_size:
        raise SystemExit("Published asset differs from the verified local build. A published tag is immutable.")
    if release["draft"]:
        github(token, f"{API}/releases/{release['id']}", "PATCH", {"draft": False, "make_latest": "false"})
    # A draft asset can still report an 'untagged-...' download URL until publication.
    published = github(token, f"{API}/releases/tags/{tag}")
    asset = next(a for a in published["assets"] if a["id"] == asset["id"])
    if published["draft"] or asset["browser_download_url"] != f"https://github.com/{REPO}/releases/download/{tag}/{FILENAME}":
        raise SystemExit("Published asset does not yet have its public tagged URL. Retry this command.")
    lock = {"sourceCommit": commit, "sourceTrees": trees,
            "entry": {"id": "tnc", "folder": "mods", "name": FILENAME,
                      "url": asset["browser_download_url"], "size": asset["size"], "sha256": sha}}
    output = ROOT / "tools/tnc-client-release.lock.json"
    temp = output.with_suffix(".tmp")
    temp.write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")
    temp.replace(output)
    print(f"{asset['browser_download_url']}\nSHA-256 {sha}\nUpdated {output}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True, help="Verified public first-party JAR from this source revision")
    publish(parser.parse_args().jar)
