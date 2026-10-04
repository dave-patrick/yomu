"""Run an Azure Speech SDK native-module load trace without printing the key."""
from __future__ import annotations

import argparse
import json
import subprocess
import time
from pathlib import Path


def adb(adb_path: str, *args: str, **kwargs):
    return subprocess.run([adb_path, *args], check=True, **kwargs)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--credentials", type=Path, required=True,
                        help="Local text file; first nonblank line is an Azure key")
    parser.add_argument("--region", default="eastus")
    parser.add_argument("--output", type=Path,
                        default=Path(__file__).resolve().parents[1] / "results" / "azure-sdk-native-load-device.json")
    args = parser.parse_args()

    keys = [line.strip() for line in args.credentials.read_text(encoding="utf-8").splitlines() if line.strip()]
    if not keys:
        raise SystemExit("Credential file contains no key")
    secret_json = json.dumps({"key": keys[0], "region": args.region, "tier": "F0"})
    keys.clear()
    app_id = "dev.yomu.spike"
    staged = False
    try:
        # Make the extra arrive through a fresh Activity onCreate, not an existing diagnostic screen.
        subprocess.run([args.adb, "shell", "am", "force-stop", app_id], check=False, capture_output=True)
        adb(args.adb, "install", "-r", str(args.apk), capture_output=True)
        staged = True
        result = subprocess.run([args.adb, "shell", "run-as", app_id, "sh", "-c",
                                 '"cat > files/azure-credentials.json"'],
                                input=secret_json.encode(), capture_output=True, check=False)
        if result.returncode:
            raise RuntimeError("Secure credential staging failed")
        del secret_json
        print("Credential staged privately; starting the SDK trace.", flush=True)
        uptime = adb(args.adb, "shell", "cat", "/proc/uptime", capture_output=True, text=True).stdout
        started_ms = int(float(uptime.split()[0]) * 1000)
        adb(args.adb, "shell", "am", "start", "-n", f"{app_id}/.SpikeActivity",
            "--ez", "runAzureSdkNativeTrace", "true", capture_output=True)
        deadline = time.monotonic() + 120
        evidence = []
        while time.monotonic() < deadline:
            time.sleep(3)
            raw = adb(args.adb, "shell", "run-as", app_id, "cat", "files/evidence.jsonl",
                      capture_output=True).stdout.decode("utf-8", "replace")
            evidence = [json.loads(line) for line in raw.splitlines() if line.strip()]
            matched = [row for row in evidence if row.get("test") == "azure_sdk_native_load_trace"
                       and row.get("uptime_ms", 0) >= started_ms]
            if matched:
                break
            if any(row.get("test") == "failure" and row.get("uptime_ms", 0) >= started_ms for row in evidence):
                raise RuntimeError("On-device SDK trace failed; inspect logcat")
        else:
            raise TimeoutError("Timed out waiting for Azure SDK trace")
        report = json.loads(adb(args.adb, "exec-out", "run-as", app_id, "cat",
                                "files/azure-native-load-trace.json", capture_output=True).stdout)
        report["device_evidence"] = matched[0]
        report["scope_note"] = "Loaded process mappings during one Azure SDK PCM WAV synthesis; not a universal execution-path proof."
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(json.dumps({
            "status": "passed",
            "wav_bytes": report["wav_bytes"],
            "azure_mappings_before": len(report["before"]),
            "azure_mappings_after": len(report["after"]),
            "codec_extension_loaded": report["codec_extension_loaded"],
            "gstreamer_loaded": report["gstreamer_loaded"],
            "evidence": str(args.output),
        }, indent=2))
    finally:
        if staged:
            subprocess.run([args.adb, "shell", "run-as", app_id, "rm", "-f", "files/azure-credentials.json"],
                           capture_output=True, check=False)
        secret_json = ""


if __name__ == "__main__":
    main()
