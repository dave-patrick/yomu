"""Run the sentence-chunk REST TTS probe without printing or persisting the Azure key."""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import time
import wave
import xml.etree.ElementTree as ET
from pathlib import Path


def adb_call(adb: str, *args: str, check: bool = True, **kwargs):
    return subprocess.run([adb, *args], check=check, **kwargs)


def tap_diagnostic_button(adb: str, label: str) -> None:
    """Find the visible Android button by its accessibility text, then tap its center."""
    for _ in range(12):
        subprocess.run([adb, "shell", "uiautomator", "dump", "/sdcard/yomu-spike-window.xml"],
                       capture_output=True, check=False)
        hierarchy = subprocess.run([adb, "exec-out", "cat", "/sdcard/yomu-spike-window.xml"],
                                   capture_output=True, check=False, text=True).stdout
        try:
            root = ET.fromstring(hierarchy)
        except ET.ParseError:
            time.sleep(1)
            continue
        for node in root.iter("node"):
            if node.attrib.get("text", "").casefold() == label.casefold():
                bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",
                                      node.attrib.get("bounds", ""))
                if not bounds:
                    continue
                left, top, right, bottom = map(int, bounds.groups())
                subprocess.run([adb, "shell", "input", "tap", str((left + right) // 2),
                                str((top + bottom) // 2)], check=True, capture_output=True)
                subprocess.run([adb, "shell", "rm", "-f", "/sdcard/yomu-spike-window.xml"],
                               capture_output=True, check=False)
                return
        time.sleep(1)
    subprocess.run([adb, "shell", "rm", "-f", "/sdcard/yomu-spike-window.xml"],
                   capture_output=True, check=False)
    raise RuntimeError(f"Could not find diagnostic button: {label}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--credentials", type=Path, required=True,
                        help="Local text file whose first nonblank line is an Azure key")
    parser.add_argument("--region", default="eastus")
    parser.add_argument("--output-dir", type=Path,
                        default=Path(__file__).resolve().parents[1] / "results")
    parser.add_argument("--long-article", action="store_true",
                        help="Run the cleaned 9to5Google article fixture instead of the short comparison")
    args = parser.parse_args()

    key_lines = [line.strip() for line in args.credentials.read_text(encoding="utf-8").splitlines()
                 if line.strip()]
    if not key_lines:
        raise SystemExit("Credential file contains no key")
    credential_json = json.dumps({"key": key_lines[0], "region": args.region, "tier": "F0"})
    key_lines.clear()

    app_id = "dev.yomu.spike"
    credential_staged = False
    article_staged = False
    long_article = args.long_article
    if long_article:
        article_path = args.output_dir / "clean-9to5google-mozilla-clean.txt"
        article_text = article_path.read_text(encoding="utf-8").strip()
        if len(article_text) < 500:
            raise SystemExit(f"Long article fixture is missing or too short: {article_path}")
    try:
        adb_call(args.adb, "install", "-r", str(args.apk), check=True, capture_output=True)
        credential_staged = True
        stage = subprocess.run(
            [args.adb, "shell", "run-as", app_id, "sh", "-c",
             '"cat > files/azure-credentials.json"'],
            input=credential_json.encode("utf-8"), capture_output=True, check=False,
        )
        if stage.returncode != 0:
            raise RuntimeError(stage.stderr.decode("utf-8", "replace").strip() or
                               f"Credential staging failed with exit code {stage.returncode}")
        del credential_json
        if long_article:
            article_staged = True
            stage_article = subprocess.run(
                [args.adb, "shell", "run-as", app_id, "sh", "-c",
                 '"cat > files/azure-rest-long-article.txt"'],
                input=article_text.encode("utf-8"), capture_output=True, check=False,
            )
            del article_text
            if stage_article.returncode != 0:
                raise RuntimeError(stage_article.stderr.decode("utf-8", "replace").strip() or
                                   f"Article staging failed with exit code {stage_article.returncode}")
        before = adb_call(args.adb, "shell", "cat", "/proc/uptime", check=True,
                          capture_output=True, text=True).stdout
        min_uptime_ms = int(float(before.split()[0]) * 1000)
        probe_name = "runAzureRestLongArticleProbe" if long_article else "runAzureRestChunkProbe"
        test_name = "azure_rest_long_article" if long_article else "azure_rest_sentence_chunks"
        audio_name = "azure-rest-long-article.wav" if long_article else "azure-rest-chunks.wav"
        report_name = "azure-rest-long-article.json" if long_article else "azure-rest-sentence-chunks.json"
        if long_article:
            adb_call(args.adb, "shell", "am", "start", "-n", f"{app_id}/.SpikeActivity",
                     check=True, capture_output=True)
            time.sleep(1)
            tap_diagnostic_button(args.adb, "GENERATE LONGER ARTICLE REST CHUNKS")
        else:
            adb_call(args.adb, "shell", "am", "start", "-n", f"{app_id}/.SpikeActivity",
                     "--ez", probe_name, "true", check=True, capture_output=True)

        deadline = time.monotonic() + 240
        device_rows = []
        while time.monotonic() < deadline:
            time.sleep(5)
            evidence = adb_call(args.adb, "shell", "run-as", app_id, "cat", "files/evidence.jsonl",
                                check=True, capture_output=True, text=True).stdout
            device_rows = [json.loads(line) for line in evidence.splitlines() if line.strip()]
            matched = [row for row in device_rows if row.get("test") == test_name
                       and row.get("uptime_ms", 0) >= min_uptime_ms]
            if matched:
                break
            if any(row.get("test") == "failure" and row.get("uptime_ms", 0) >= min_uptime_ms
                   for row in device_rows):
                raise RuntimeError("Spike activity recorded a failure; inspect device logcat")
        else:
            raise TimeoutError("Timed out waiting for sentence-chunk synthesis evidence")

        args.output_dir.mkdir(parents=True, exist_ok=True)
        report_bytes = adb_call(args.adb, "exec-out", "run-as", app_id, "cat",
                                f"files/{report_name}", check=True,
                                capture_output=True).stdout
        report = json.loads(report_bytes)
        report["device_uptime_start_ms"] = min_uptime_ms
        report["device_evidence"] = matched[0]
        audio_names = (audio_name,) if long_article else (audio_name, "azure-rest-single.wav")
        for device_name in audio_names:
            audio = adb_call(args.adb, "exec-out", "run-as", app_id, "cat", f"files/{device_name}",
                             check=True, capture_output=True).stdout
            target = args.output_dir / device_name
            target.write_bytes(audio)
            with wave.open(str(target), "rb") as wav:
                report.setdefault("downloaded_wav_validation", {})[device_name] = {
                    "channels": wav.getnchannels(),
                    "sample_rate": wav.getframerate(),
                    "bits_per_sample": wav.getsampwidth() * 8,
                    "duration_ms": wav.getnframes() * 1000.0 / wav.getframerate(),
                    "frames": wav.getnframes(),
                    "bytes": target.stat().st_size,
                }
        chunk_audio_ms = report["downloaded_wav_validation"][audio_name]["duration_ms"]
        last_chunk_ms = report["sentences"][-1]["end_ms"]
        if abs(chunk_audio_ms - last_chunk_ms) > 1.0:
            raise AssertionError(f"WAV duration {chunk_audio_ms:.2f} ms differs from map {last_chunk_ms:.2f} ms")
        for previous, current in zip(report["sentences"], report["sentences"][1:]):
            if abs(previous["end_ms"] - current["start_ms"]) > 0.001:
                raise AssertionError("Sentence offsets contain a gap or overlap")
        result_name = "azure-rest-long-article-device.json" if long_article else "azure-rest-sentence-chunks-device.json"
        (args.output_dir / result_name).write_text(
            json.dumps(report, indent=2), encoding="utf-8")
        print(json.dumps({
            "status": "passed",
            "voice_id": report["voice_id"],
            "sentence_count": report["sentence_count"],
            "rest_requests": report["rest_requests"],
            "single_request_ms": report.get("single_request_ms"),
            "chunked_requests_ms": report["chunked_requests_ms"],
            "single_duration_ms": None if long_article else report["downloaded_wav_validation"]["azure-rest-single.wav"]["duration_ms"],
            "chunked_duration_ms": chunk_audio_ms,
            "alignment_map_matches_wav": True,
            "artifacts": str(args.output_dir),
        }, indent=2))
    finally:
        if credential_staged:
            adb_call(args.adb, "shell", "run-as", app_id, "rm", "-f", "files/azure-credentials.json",
                     check=False, capture_output=True)
        if article_staged:
            adb_call(args.adb, "shell", "run-as", app_id, "rm", "-f", "files/azure-rest-long-article.txt",
                     check=False, capture_output=True)
        credential_json = ""


if __name__ == "__main__":
    main()
