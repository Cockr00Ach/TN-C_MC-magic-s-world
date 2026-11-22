"""TN-C framed microphone PCM -> streaming Mandarin text over local stdio."""

import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys

import numpy as np
import sherpa_onnx


SAMPLE_RATES = (16000, 44100, 48000)
KEYWORD_MODEL = "epoch-12-avg-2-chunk-16-left-64.int8.onnx"
LONG_SHAPES = {"水龙卷": "水龙", "水泡囚笼": "水炮"}


def emit(utterance, kind, text, shape):
    message = {"id": utterance, "kind": kind, "text": text}
    if shape:
        message["shape"] = shape
    print(json.dumps(message, ensure_ascii=True), flush=True)


def read_exact(source, length):
    value = source.read(length)
    if len(value) != length:
        raise EOFError("incomplete microphone frame")
    return value


def load_keyword_spotter(model_dir):
    files = {part: model_dir / f"{part}-{KEYWORD_MODEL}" for part in ("encoder", "decoder", "joiner")}
    files.update(tokens=model_dir / "tokens.txt", keywords_file=model_dir / "keywords.txt")
    manifest_path = model_dir / "manifest.json"
    try:
        if not manifest_path.is_file():
            return None
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        for part, path in files.items():
            expected = manifest["keywords_sha256"] if part == "keywords_file" else manifest["files"][path.name]["sha256"]
            with path.open("rb") as file:
                if hashlib.file_digest(file, "sha256").hexdigest() != expected:
                    raise ValueError(f"keyword model checksum mismatch: {path.name}")
        return sherpa_onnx.KeywordSpotter(
            **{part: str(path) for part, path in files.items()},
            num_threads=1,
            max_active_paths=12,
            keywords_score=1.5,
            keywords_threshold=0.25,
            num_trailing_blanks=8,
            provider="cpu",
        )
    except (OSError, ValueError, KeyError, TypeError, RuntimeError, AttributeError, AssertionError) as error:
        # An unavailable optional spotter must not break the full speech recognizer.
        print(f"TN-C keyword spotter unavailable: {error}", file=sys.stderr, flush=True)
        return None


class ShapeHints:
    def __init__(self, spotter):
        self.spotter = spotter
        self.stream = spotter.create_stream() if spotter else None
        self.hits = set()

    def decode(self):
        if self.stream is None:
            return
        while self.spotter.is_ready(self.stream):
            self.spotter.decode_stream(self.stream)
            keyword = self.spotter.get_result(self.stream)
            if keyword:
                self.hits.add(keyword)
                self.spotter.reset_stream(self.stream)

    def accept(self, rate, samples):
        if self.stream is not None:
            self.stream.accept_waveform(rate, samples)
            self.decode()

    def finish(self, rate):
        if self.stream is not None:
            self.stream.accept_waveform(rate, np.zeros(rate * 4 // 5, dtype=np.float32))
            self.stream.input_finished()
            self.decode()

    def shape(self):
        choices = self.hits.copy()
        # Prefer a whole keyword over its nested prefix (e.g. 九重雷 vs 雷击).
        choices = {word for word in choices if not any(word != full and word in full for full in choices)}
        for full, prefix in LONG_SHAPES.items():
            if full in choices:
                choices.discard(prefix)
        # The transcript can resolve multiple explicit shapes in speaking order.
        # A separate acoustic hint is emitted only for an unambiguous shape.
        return next(iter(choices)) if len(choices) == 1 else ""


def recognize(voice, spotter, source, utterance, rate):
    stream = voice.create_stream()
    hints = ShapeHints(spotter)
    previous = ("", "")
    captured = 0
    while frame_bytes := struct.unpack(">i", read_exact(source, 4))[0]:
        captured += frame_bytes
        if not 0 < frame_bytes <= 8192 or frame_bytes % 2 or captured > rate * 2 * 21:
            raise ValueError("invalid microphone frame")
        pcm = read_exact(source, frame_bytes)
        samples = np.frombuffer(pcm, dtype="<i2").astype(np.float32) / 32768.0
        stream.accept_waveform(rate, samples)
        while voice.is_ready(stream):
            voice.decode_stream(stream)
        hints.accept(rate, samples)
        result = (voice.get_result(stream), hints.shape())
        if result != previous:
            emit(utterance, "partial", *result)
            previous = result

    # Supply a short silence tail so the model can finish its last chunk.
    stream.accept_waveform(rate, np.zeros(rate // 2, dtype=np.float32))
    stream.input_finished()
    while voice.is_ready(stream):
        voice.decode_stream(stream)
    hints.finish(rate)
    emit(utterance, "final", voice.get_result(stream), hints.shape())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", type=Path, required=True)
    model_dir = parser.parse_args().model_dir
    voice = sherpa_onnx.OnlineRecognizer.from_zipformer2_ctc(
        tokens=str(model_dir / "tokens.txt"),
        model=str(model_dir / "model.int8.onnx"),
        num_threads=2,
        provider="cpu",
    )
    spotter = load_keyword_spotter(model_dir / "keywords")
    source = sys.stdin.buffer
    # Each utterance starts with a negated sample rate and a session ID.
    while header := source.read(4):
        rate = -struct.unpack(">i", header)[0]
        if rate not in SAMPLE_RATES:
            raise ValueError("invalid microphone rate")
        utterance = struct.unpack(">q", read_exact(source, 8))[0]
        recognize(voice, spotter, source, utterance, rate)


if __name__ == "__main__":
    main()
