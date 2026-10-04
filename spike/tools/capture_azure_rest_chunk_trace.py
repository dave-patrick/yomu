"""Attach the Galaxy playback-position trace to the REST sentence-chunk report."""
from __future__ import annotations

import argparse
import json
import statistics
import subprocess
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--report", type=Path,
                        default=Path(__file__).resolve().parents[1] / "results" / "azure-rest-sentence-chunks-device.json")
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    min_uptime = report["device_evidence"]["uptime_ms"]
    result = subprocess.run(
        [args.adb, "shell", "run-as", "dev.yomu.spike", "cat", "files/evidence.jsonl"],
        check=True, capture_output=True, text=True,
    )
    rows = [json.loads(line) for line in result.stdout.splitlines() if line.strip()]
    trace_name = ("azure_rest_long_article_highlight"
                  if report.get("fixture") == "9to5google-cleaned-extraction"
                  else "azure_rest_sentence_highlight")
    trace = [row for row in rows if row.get("test") == trace_name
             and row.get("uptime_ms", 0) >= min_uptime]
    trace.sort(key=lambda row: row["sentence_index"])
    expected_count = report["sentence_count"]
    if len(trace) != expected_count:
        raise SystemExit(f"Expected {expected_count} playback boundary rows, found {len(trace)}")
    lateness = [row["highlight_lateness_ms"] for row in trace]
    report["playback_trace"] = {
        "source": "Galaxy ExoPlayer currentPosition, polled every 20 ms",
        "boundary_count": len(trace),
        "median_highlight_lateness_ms": statistics.median(lateness),
        "max_highlight_lateness_ms": max(lateness),
        "rows": trace,
    }
    args.report.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report["playback_trace"], indent=2))


if __name__ == "__main__":
    main()
