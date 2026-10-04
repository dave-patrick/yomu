"""Stage private test inputs and run the incremental Azure REST playback probe."""
from __future__ import annotations

import argparse
import json
import subprocess
import time
from pathlib import Path


def call(adb: str, *args: str, **kwargs):
    return subprocess.run([adb, *args], check=True, **kwargs)


def stage(adb: str, app_id: str, name: str, data: bytes) -> None:
    result = subprocess.run([adb, "shell", "run-as", app_id, "sh", "-c", f'"cat > files/{name}"'],
                            input=data, capture_output=True, check=False)
    if result.returncode:
        raise RuntimeError(f"Could not stage private test input: {name}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--credentials", type=Path, required=True,
                        help="Local text file; first nonblank line is an Azure key")
    parser.add_argument("--article", type=Path,
                        default=Path(__file__).resolve().parents[1] / "results" / "clean-9to5google-mozilla-clean.txt")
    parser.add_argument("--output-dir", type=Path, default=Path(__file__).resolve().parents[1] / "results")
    parser.add_argument("--simulate-outage", action="store_true",
                        help="Inject a deterministic REST failure after three online sentences and exercise Kokoro fallback")
    parser.add_argument("--auto-switch-back", action="store_true",
                        help="Run the actual-connectivity recovery probe that retries REST at a later sentence boundary")
    args = parser.parse_args()
    key = next((line.strip() for line in args.credentials.read_text(encoding="utf-8").splitlines() if line.strip()), "")
    article = args.article.read_text(encoding="utf-8").strip()
    if not key or len(article) < 500:
        raise SystemExit("The F0 key or long article fixture is missing")
    secret_json = json.dumps({"key": key, "region": "eastus", "tier": "F0"}).encode()
    del key
    article_bytes = article.encode()
    del article
    app_id = "dev.yomu.spike"
    staged_names: list[str] = []
    try:
        subprocess.run([args.adb, "shell", "am", "force-stop", app_id], capture_output=True, check=False)
        call(args.adb, "install", "-r", str(args.apk), capture_output=True)
        stage(args.adb, app_id, "azure-credentials.json", secret_json)
        staged_names.append("azure-credentials.json")
        del secret_json
        stage(args.adb, app_id, "azure-rest-long-article.txt", article_bytes)
        staged_names.append("azure-rest-long-article.txt")
        del article_bytes
        uptime = call(args.adb, "shell", "cat", "/proc/uptime", capture_output=True, text=True).stdout
        started_ms = int(float(uptime.split()[0]) * 1000)
        print("Inputs staged privately; incremental playback is starting.", flush=True)
        probe_extra = ("runAzureRestFailoverProbe" if args.simulate_outage else
                       "runAzureRestRecoveryProbe" if args.auto_switch_back else "runAzureRestIncrementalProbe")
        call(args.adb, "shell", "am", "start", "-n", f"{app_id}/.SpikeActivity",
             "--ez", probe_extra, "true", capture_output=True)
        deadline = time.monotonic() + 120
        matched = None
        while time.monotonic() < deadline:
            time.sleep(2)
            raw = call(args.adb, "shell", "run-as", app_id, "cat", "files/evidence.jsonl",
                       capture_output=True).stdout.decode("utf-8", "replace")
            rows = [json.loads(line) for line in raw.splitlines() if line.strip()]
            matched = next((row for row in reversed(rows)
                            if row.get("test") == "azure_rest_incremental_generation_complete"
                            and row.get("uptime_ms", 0) >= started_ms), None)
            if matched:
                break
            failure = next((row for row in reversed(rows)
                            if row.get("test") == "failure" and row.get("uptime_ms", 0) >= started_ms), None)
            if failure:
                raise RuntimeError("Incremental probe failed; inspect app logcat")
        if not matched:
            raise TimeoutError("Timed out waiting for incremental speech generation")
        report_bytes = call(args.adb, "exec-out", "run-as", app_id, "cat",
                            "files/azure-rest-incremental.json", capture_output=True).stdout
        report = json.loads(report_bytes)
        report["device_evidence"] = matched
        queued = next((row for row in reversed(rows) if row.get("test") == "azure_rest_incremental_playback_queued"
                       and row.get("uptime_ms", 0) >= started_ms), None)
        first_transition = next((row for row in reversed(rows)
                                 if row.get("test") == "azure_rest_incremental_sentence_start"
                                 and row.get("uptime_ms", 0) >= started_ms), None)
        report["playback_start_evidence"] = queued
        report["first_transition_seen_by_generation_complete"] = first_transition is not None
        args.output_dir.mkdir(parents=True, exist_ok=True)
        stem = ("azure-rest-failover" if args.simulate_outage else
                "azure-rest-auto-recovery" if args.auto_switch_back else "azure-rest-incremental")
        target = args.output_dir / f"{stem}-device.json"
        target.write_text(json.dumps(report, indent=2), encoding="utf-8")
        transitions = [row for row in rows if row.get("test") == "azure_rest_incremental_sentence_start"
                       and row.get("uptime_ms", 0) >= started_ms]
        target.with_name(f"{stem}-playback.jsonl").write_text(
            "\n".join(json.dumps(row, ensure_ascii=False) for row in transitions) +
            ("\n" if transitions else ""), encoding="utf-8")
        print(json.dumps({
            "status": "passed",
            "sentences": report["sentence_count"],
            "prebuffered_sentences": report["prebuffered_sentences"],
            "time_to_playable_head_ms": queued.get("time_to_playable_head_ms") if queued else None,
            "generation_wall_ms": report["generation_wall_ms"],
            "total_audio_duration_ms": report["total_audio_duration_ms"],
            "wav_bytes": report["generated_wav_bytes"],
            "provider_sequence": report.get("provider_sequence"),
            "first_transition_seen_by_collection": bool(transitions),
            "evidence": str(target),
        }, indent=2))
    finally:
        for name in staged_names:
            subprocess.run([args.adb, "shell", "run-as", app_id, "rm", "-f", f"files/{name}"],
                           capture_output=True, check=False)
        secret_json = b""
        article_bytes = b""


if __name__ == "__main__":
    main()
