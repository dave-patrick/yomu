"""Stage the DOCX fixture and collect its on-device text extraction report."""
from __future__ import annotations

import json
import subprocess
import time
from pathlib import Path


def call(adb: str, *args: str, check: bool = True, **kwargs):
    return subprocess.run([adb, *args], check=check, **kwargs)


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    app_id = "dev.yomu.spike"
    fixture = args.root / "results" / "docx-fixture.docx"
    if not fixture.is_file():
        raise SystemExit("Run tools/generate_docx_fixture.py first")
    call(args.adb, "shell", "am", "force-stop", app_id, check=False)
    call(args.adb, "install", "-r", str(args.apk), capture_output=True)
    remote = "/data/local/tmp/yomu-docx-fixture.docx"
    call(args.adb, "push", str(fixture), remote, capture_output=True)
    call(args.adb, "shell", "run-as", app_id, "cp", remote, "files/docx-fixture.docx")
    call(args.adb, "shell", "rm", "-f", remote, check=False)
    baseline = int(float(call(args.adb, "shell", "cat", "/proc/uptime", capture_output=True, text=True).stdout.split()[0]) * 1000)
    call(args.adb, "shell", "am", "start", "-n", f"{app_id}/.SpikeActivity", "--ez", "runDocxProbe", "true", capture_output=True)
    deadline = time.monotonic() + 30
    matched = None
    while time.monotonic() < deadline:
        raw = call(args.adb, "shell", "run-as", app_id, "cat", "files/evidence.jsonl", capture_output=True).stdout
        for line in raw.decode("utf-8", "replace").splitlines():
            try:
                row = json.loads(line)
                if row.get("test") == "docx_probe_complete" and row.get("uptime_ms", 0) >= baseline:
                    matched = row
                if row.get("test") == "failure" and row.get("uptime_ms", 0) >= baseline:
                    raise RuntimeError(f"Probe failed: {row}")
            except json.JSONDecodeError:
                continue
        if matched:
            break
        time.sleep(0.5)
    if not matched:
        raise TimeoutError("Timed out waiting for DOCX probe")
    report = call(args.adb, "exec-out", "run-as", app_id, "cat", "files/docx-probe.json", capture_output=True).stdout
    report_data = json.loads(report)
    expected = ["DOCX reading order fixture", "The first paragraph should be read before the table.",
                "Table cell alpha", "Table cell beta", "The final paragraph should be read last."]
    if report_data.get("paragraphs") != expected:
        raise RuntimeError(f"DOCX extraction failed reading-order validation: {report_data.get('paragraphs')}")
    if "REPEATED HEADER" in report_data.get("reading_text", "") or "REPEATED FOOTER" in report_data.get("reading_text", ""):
        raise RuntimeError("DOCX header/footer leaked into body reading text")
    target = args.root / "results" / "docx-device.json"
    target.write_bytes(report)
    call(args.adb, "shell", "run-as", app_id, "rm", "-f", "files/docx-fixture.docx", check=False)
    print(json.dumps({"status": "passed", "report": str(target), "evidence": matched,
                      "report_data": report_data}, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
