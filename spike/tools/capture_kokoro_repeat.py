"""Save the latest current-boot Kokoro benchmark evidence from the attached device."""
from __future__ import annotations

import argparse
import json
import statistics
import subprocess
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True, help="Path to adb executable")
    parser.add_argument("--min-uptime-ms", required=True, type=int,
                        help="Device boot-time lower bound for selecting this run")
    parser.add_argument("--max-uptime-ms", required=True, type=int,
                        help="Device boot-time upper bound for selecting this run")
    parser.add_argument("--output", type=Path,
                        default=Path(__file__).resolve().parents[1] / "results" / "kokoro-repeat-device.json")
    args = parser.parse_args()

    result = subprocess.run(
        [args.adb, "shell", "run-as", "dev.yomu.spike", "cat", "files/evidence.jsonl"],
        check=True, capture_output=True, text=True,
    )
    all_rows = [json.loads(line) for line in result.stdout.splitlines() if line.strip()]
    rows = [row for row in all_rows if row.get("test") == "tts_benchmark"
            and args.min_uptime_ms < row.get("uptime_ms", 0) < args.max_uptime_ms]
    if len(rows) != 12:
        raise SystemExit(f"Expected 12 rows for this benchmark, found {len(rows)}")

    summary = []
    for threads in (1, 2, 4):
        measured = [row for row in rows if row["threads"] == threads and not row["warmup"]]
        summary.append({
            "threads": threads,
            "rtf_runs": [row["rtf"] for row in measured],
            "median_rtf": statistics.median(row["rtf"] for row in measured),
            "median_cpu_percent_one_core_basis": statistics.median(
                row["cpu_percent_one_core_basis"] for row in measured),
            "median_peak_pss_mib": statistics.median(row["peak_pss_kb"] for row in measured) / 1024,
            "median_peak_rss_mib": statistics.median(row["peak_rss_kb"] for row in measured) / 1024,
        })

    report = {
        "test": "kokoro_repeat_benchmark",
        "date": "2026-10-02",
        "device": "Samsung Galaxy S26 Ultra SM-S948U",
        "android_api": 37,
        "model": "Kokoro-82M-v0.19",
        "voice_id": 0,
        "audio_duration_ms": 16726,
        "warmup_and_measured_runs": "one warmup plus three measured runs per thread count; 1, 2, then 4 threads",
        "thermal_status_start": 0,
        "thermal_status_end": 1,
        "phonemizer_data_bytes": 17991651,
        "rows": rows,
        "summary": summary,
        "caveat": "Thermal status rose to 1 during the sequential repeat; use as a cool-start repeat, not a fully cool sustained-performance result.",
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({"path": str(args.output), "row_count": len(rows), "summary": summary}, indent=2))


if __name__ == "__main__":
    main()
