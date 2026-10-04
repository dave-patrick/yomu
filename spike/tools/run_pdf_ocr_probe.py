"""Stage small public PDF fixtures and run Android PdfRenderer/Tesseract OCR probe."""
from __future__ import annotations

import json
import subprocess
import time
from pathlib import Path


def call(adb: str, *args: str, check: bool = True, **kwargs):
    return subprocess.run([adb, *args], check=check, **kwargs)


def stage(adb: str, local: Path, name: str) -> None:
    remote = f"/data/local/tmp/yomu-{name.replace('/', '-')}"
    call(adb, "push", str(local), remote, capture_output=True)
    result = call(adb, "shell", "run-as", "dev.yomu.spike", "cp", remote, f"files/{name}", check=False)
    call(adb, "shell", "rm", "-f", remote, check=False)
    if result.returncode:
        raise RuntimeError(f"Could not stage {name} into private app storage")


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    app_id = "dev.yomu.spike"
    fixtures = args.root / "results" / "pdf-fixtures"
    model = args.root / "downloads" / "tesseract" / "eng.traineddata"
    files = ["pdf-selectable.pdf", "pdf-scanned.pdf", "pdf-mixed.pdf", "pdf-password.pdf"]
    if not model.is_file() or any(not (fixtures / name).is_file() for name in files):
        raise SystemExit("PDF fixtures or English OCR model are missing")
    call(args.adb, "shell", "am", "force-stop", app_id, check=False)
    call(args.adb, "install", "-r", str(args.apk), capture_output=True)
    call(args.adb, "shell", "run-as", app_id, "mkdir", "-p", "files/tesseract/tessdata")
    call(args.adb, "shell", "rm", "-f", *[f"files/{n}" for n in files], check=False)
    for name in files:
        stage(args.adb, fixtures / name, name)
    stage(args.adb, model, "tesseract/tessdata/eng.traineddata")
    # copy command above uses a path under files, so ensure nested model path exists and stage it explicitly.
    call(args.adb, "shell", "run-as", app_id, "mkdir", "-p", "files/tesseract/tessdata")
    call(args.adb, "shell", "am", "start", "-n", f"{app_id}/.SpikeActivity", "--ez", "runPdfOcrProbe", "true",
         capture_output=True)
    baseline = int(float(call(args.adb, "shell", "cat", "/proc/uptime", capture_output=True, text=True).stdout.split()[0]) * 1000)
    deadline = time.monotonic() + 180
    matched = None
    while time.monotonic() < deadline:
        raw = call(args.adb, "shell", "run-as", app_id, "cat", "files/evidence.jsonl", capture_output=True).stdout
        for line in raw.decode("utf-8", "replace").splitlines():
            try:
                row = json.loads(line)
                if row.get("test") == "pdf_ocr_probe_complete" and row.get("uptime_ms", 0) >= baseline:
                    matched = row
                    break
                if row.get("test") == "failure" and row.get("uptime_ms", 0) >= baseline:
                    raise RuntimeError(f"Probe failed: {row}")
            except json.JSONDecodeError:
                continue
        if matched:
            break
        time.sleep(1)
    if not matched:
        raise TimeoutError("Timed out waiting for PDF/OCR probe")
    report = call(args.adb, "exec-out", "run-as", app_id, "cat", "files/pdf-ocr-probe.json", capture_output=True).stdout
    report_data = json.loads(report)
    by_name = {item["file"]: item for item in report_data["fixtures"]}
    selectable = by_name["pdf-selectable.pdf"]["pages"][0]
    scanned = by_name["pdf-scanned.pdf"]["pages"][0]
    mixed = by_name["pdf-mixed.pdf"]["pages"]
    if selectable["source"] != "pdf-renderer-text" or "quick brown fox" not in selectable["text"].lower():
        raise RuntimeError("Selectable PDF text did not meet the fixture check")
    if scanned["source"] != "tesseract-ocr" or "quiet river" not in scanned["text"].lower():
        raise RuntimeError("Scanned PDF OCR did not meet the fixture check")
    if len(mixed) != 2 or mixed[0]["source"] != "pdf-renderer-text" or mixed[1]["source"] != "tesseract-ocr":
        raise RuntimeError("Mixed PDF did not use the expected text/OCR paths")
    password = report_data.get("password_test", {})
    if password.get("no_password_failure") != "SecurityException" or password.get("wrong_password_failure") != "SecurityException" or "Password protected PDF fixture" not in password.get("correct_password_text", ""):
        raise RuntimeError("PDF password handling did not match the expected prompt/unlock behavior")
    target = args.root / "results" / "pdf-ocr-device.json"
    target.write_bytes(report)
    print(json.dumps({"status": "passed", "report": str(target), "evidence": matched,
                      "report_data": report_data}, indent=2))
    call(args.adb, "shell", "run-as", app_id, "rm", "-f", *[f"files/{n}" for n in files], check=False)


if __name__ == "__main__":
    main()
