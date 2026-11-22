"""Install the optional TN-C voice runtime into a Minecraft game directory."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tarfile
from tempfile import TemporaryDirectory
from urllib.request import urlopen
import venv
import wave


MODEL = "sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01"
MODEL_URL = f"https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/{MODEL}.tar.bz2"
MODEL_SHA256 = "b3b309f7ce4a737195fcc6963ea19b0653a7d3401580af5ae0d3e284cbb71f0b"
MODEL_FILES = ("model.int8.onnx", "tokens.txt")
REQUIREMENTS = ("sherpa-onnx==1.13.8", "numpy==2.2.6")


def sha256(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def model_installed(model_dir):
    try:
        manifest = json.loads((model_dir / "manifest.json").read_text(encoding="utf-8"))
        return manifest["model"] == MODEL and all(
            sha256(model_dir / name) == manifest["files"][name] for name in MODEL_FILES
        )
    except (OSError, ValueError, KeyError, TypeError):
        return False


def extract_model(archive, staging):
    if sha256(archive) != MODEL_SHA256:
        raise RuntimeError("model archive checksum mismatch")
    with tarfile.open(archive, "r:bz2") as tar:
        for name in (*MODEL_FILES, "test_wavs/0.wav"):
            member = tar.getmember(f"./{MODEL}/{name}")
            if not member.isfile():
                raise RuntimeError(f"invalid model file: {name}")
            file = staging / Path(name).name
            with tar.extractfile(member) as source, file.open("wb") as dest:
                shutil.copyfileobj(source, dest)


def smoke_test(python, helper, model_dir, fixture=None, require_keywords=False):
    pcm = b""
    rate = 16000
    if fixture is not None:
        with wave.open(str(fixture), "rb") as wav:
            if (wav.getnchannels(), wav.getsampwidth()) != (1, 2):
                raise RuntimeError("expected a mono PCM16 speech fixture")
            rate = wav.getframerate()
            pcm = wav.readframes(wav.getnframes())
    # Decode the speech, then a second empty utterance in the same process.
    # An empty second result verifies that the first stream does not leak text.
    frames = [struct.pack(">iq", -rate, 1)]
    for offset in range(0, len(pcm), 1280):
        chunk = pcm[offset:offset + 1280]
        frames.append(struct.pack(">i", len(chunk)) + chunk)
    frames += [struct.pack(">i", 0), struct.pack(">iqi", -rate, 2, 0)]
    result = subprocess.run(
        [str(python), "-I", "-u", str(helper), "--model-dir", model_dir.name],
        cwd=model_dir.parent, input=b"".join(frames), capture_output=True, timeout=30, check=False,
    )
    if result.returncode:
        raise RuntimeError(result.stderr.decode("utf-8", errors="replace").strip()
                           or "recognizer could not load its model")
    if require_keywords and b"TN-C keyword spotter unavailable:" in result.stderr:
        raise RuntimeError(result.stderr.decode("utf-8", errors="replace").strip())
    updates = [json.loads(line) for line in result.stdout.splitlines()]
    finals = [event for event in updates if event["kind"] == "final"]
    if [event["id"] for event in finals] != [1, 2] or finals[-1]["text"] or finals[-1].get("shape"):
        raise RuntimeError("recognizer did not finish or reset its utterance")
    if fixture is not None and (
        not any(event["kind"] == "partial" and event["text"] for event in updates)
        or "研究感兴趣" not in finals[0]["text"]
    ):
        raise RuntimeError("recognizer did not decode the bundled Mandarin sample")


def install_keywords(model_dir, bundled, source_dir=None):
    manifest_path = bundled / "keyword_model.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    destination = model_dir / "keywords"
    destination.mkdir(exist_ok=True)
    # Stage and check every download before replacing any existing model file.
    with TemporaryDirectory(prefix=".keywords-", dir=model_dir) as work:
        staging = Path(work)
        downloaded = []
        for name, source in manifest["files"].items():
            current = destination / name
            if current.is_file() and sha256(current) == source["sha256"]:
                continue
            candidate = staging / name
            if source_dir is not None:
                shutil.copyfile(source_dir / name, candidate)
            else:
                url = f"{manifest['repository']}/resolve/{source['revision']}/{name}"
                with urlopen(url, timeout=60) as response, candidate.open("wb") as output:
                    shutil.copyfileobj(response, output)
            if sha256(candidate) != source["sha256"]:
                raise RuntimeError(f"keyword model checksum mismatch for {name}")
            downloaded.append(candidate)
        for candidate in downloaded:
            candidate.replace(destination / candidate.name)
        keyword_file = staging / "keywords.txt"
        shutil.copyfile(bundled / "keywords.txt", keyword_file)
        manifest["keywords_sha256"] = sha256(keyword_file)
        installed_manifest = staging / "manifest.json"
        installed_manifest.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        keyword_file.replace(destination / keyword_file.name)
        installed_manifest.replace(destination / installed_manifest.name)


def install(target, archive=None, keyword_source=None):
    target.mkdir(parents=True, exist_ok=True)
    helper = Path(__file__).with_name("recognize.py")
    environment = target / "venv"
    python = environment / ("Scripts/python.exe" if sys.platform == "win32" else "bin/python")
    if not python.is_file():
        venv.EnvBuilder(with_pip=True).create(environment)
    subprocess.run([str(python), "-m", "pip", "install", "--disable-pip-version-check",
                    "--only-binary=:all:", *REQUIREMENTS], check=True)

    model_dir = target / "model"
    if not model_installed(model_dir):
        with TemporaryDirectory(prefix=".model-", dir=target) as work:
            staging = Path(work)
            if archive is None:
                archive = staging / "model.tar.bz2"
                print("Downloading the 21 MB Mandarin model...", flush=True)
                with urlopen(MODEL_URL, timeout=60) as source, archive.open("wb") as dest:
                    shutil.copyfileobj(source, dest)
            extract_model(archive, staging)
            smoke_test(python, helper, staging, staging / "0.wav")
            model_dir.mkdir(exist_ok=True)
            for name in MODEL_FILES:
                (staging / name).replace(model_dir / name)
            (model_dir / "manifest.json").write_text(json.dumps({
                "model": MODEL, "files": {name: sha256(model_dir / name) for name in MODEL_FILES},
            }, indent=2) + "\n", encoding="utf-8")

    install_keywords(model_dir, helper.parent, keyword_source)
    smoke_test(python, helper, model_dir, require_keywords=True)
    temporary_helper = target / "recognize.py.tmp"
    shutil.copyfile(helper, temporary_helper)
    temporary_helper.replace(target / "recognize.py")
    print(f"TN-C offline voice runtime ready: {target}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", type=Path, required=True, help="Path to <gameDir>/free-magic-voice")
    parser.add_argument("--model-archive", type=Path, help="Use a local copy of the official model archive")
    parser.add_argument("--keyword-model-dir", type=Path, help="Use the pinned local keyword model files")
    args = parser.parse_args()
    install(args.target.resolve(), args.model_archive, args.keyword_model_dir)


if __name__ == "__main__":
    main()
