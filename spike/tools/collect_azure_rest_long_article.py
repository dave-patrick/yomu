"""Collect and validate the already-generated long-article WAV and offset map."""
from __future__ import annotations

import argparse
import json
import subprocess
import wave
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--output-dir", type=Path,
                        default=Path(__file__).resolve().parents[1] / "results")
    args = parser.parse_args()
    adb = args.adb
    output = args.output_dir
    output.mkdir(parents=True, exist_ok=True)
    raw = subprocess.run([adb, "exec-out", "run-as", "dev.yomu.spike", "cat",
                          "files/azure-rest-long-article.json"], check=True,
                         capture_output=True).stdout
    report = json.loads(raw)
    evidence_raw = subprocess.run([adb, "exec-out", "run-as", "dev.yomu.spike", "cat",
                                   "files/evidence.jsonl"], check=True,
                                  capture_output=True, text=True).stdout
    evidence = [json.loads(row) for row in evidence_raw.splitlines() if row.strip()]
    run = next((row for row in reversed(evidence)
                if row.get("test") == "azure_rest_long_article"), None)
    if run is None:
        raise SystemExit("No device evidence found for the long-article run")
    wav_path = output / "azure-rest-long-article.wav"
    wav_path.write_bytes(subprocess.run([adb, "exec-out", "run-as", "dev.yomu.spike", "cat",
                                         "files/azure-rest-long-article.wav"], check=True,
                                        capture_output=True).stdout)
    with wave.open(str(wav_path), "rb") as audio:
        duration_ms = audio.getnframes() * 1000 / audio.getframerate()
        report["downloaded_wav_validation"] = {
            "channels": audio.getnchannels(), "sample_rate": audio.getframerate(),
            "bits_per_sample": audio.getsampwidth() * 8, "frames": audio.getnframes(),
            "duration_ms": duration_ms, "bytes": wav_path.stat().st_size,
        }
    if abs(duration_ms - report["sentences"][-1]["end_ms"]) > 1:
        raise SystemExit("The final sentence offset does not match the WAV duration")
    for previous, current in zip(report["sentences"], report["sentences"][1:]):
        if abs(previous["end_ms"] - current["start_ms"]) > 0.001:
            raise SystemExit("Sentence offsets contain a gap or overlap")
    report["device_evidence"] = run
    (output / "azure-rest-long-article-device.json").write_text(
        json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({"status": "passed", "fixture": report["fixture"],
                      "voice_id": report["voice_id"], "characters": len(report["text"]),
                      "sentence_count": report["sentence_count"],
                      "rest_requests": report["rest_requests"],
                      "synthesis_ms": report["chunked_requests_ms"],
                      "duration_ms": duration_ms, "wav_bytes": wav_path.stat().st_size,
                      "offsets_match_wav": True, "artifacts": str(output)}, indent=2))


if __name__ == "__main__":
    main()
