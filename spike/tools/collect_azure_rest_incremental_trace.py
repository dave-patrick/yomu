"""Collect the completed incremental-playback trace without exporting any API credentials."""
from __future__ import annotations

import argparse
import json
import statistics
import subprocess
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", help="ADB device serial when more than one device is connected")
    parser.add_argument("--output-dir", type=Path,
                        default=Path(__file__).resolve().parents[1] / "results")
    parser.add_argument("--failover", action="store_true")
    parser.add_argument("--real-outage", action="store_true",
                        help="Collect a run that lost Wi-Fi and mobile data during playback")
    parser.add_argument("--auto-recovery", action="store_true",
                        help="Collect a run that returned from Kokoro to Azure after connectivity recovered")
    args = parser.parse_args()
    command = [args.adb]
    if args.serial:
        command += ["-s", args.serial]
    command += ["shell", "run-as", "dev.yomu.spike", "cat", "files/evidence.jsonl"]
    raw = subprocess.run(command,
                         check=True, capture_output=True).stdout.decode("utf-8", "replace")
    rows = [json.loads(line) for line in raw.splitlines() if line.strip()]
    queued = next((row for row in reversed(rows) if row.get("test") == "azure_rest_incremental_playback_queued"), None)
    run_rows = [row for row in rows if queued is None or row.get("pid") == queued.get("pid")]
    starts = [row for row in run_rows if row.get("test") == "azure_rest_incremental_sentence_start"]
    ended = next((row for row in reversed(rows) if row.get("test") == "azure_rest_incremental_playback_ended"), None)
    complete = next((row for row in reversed(run_rows) if row.get("test") == "azure_rest_incremental_generation_complete"), None)
    failover_event = next((row for row in reversed(run_rows) if row.get("test") == "speech_provider_failover"), None)
    announcement_event = next((row for row in reversed(run_rows)
                               if row.get("test") == "azure_rest_incremental_offline_announcement_started"), None)
    announcement_ready = next((row for row in reversed(run_rows) if row.get("test") == "speech_failover_announcement_ready"), None)
    stem = "azure-rest-auto-recovery" if args.auto_recovery else "azure-rest-real-outage" if args.real_outage else (
        "azure-rest-failover" if args.failover else "azure-rest-incremental")
    report_path = args.output_dir / f"{stem}-device.json"
    report = json.loads(report_path.read_text(encoding="utf-8"))
    expected = report["chunks"]
    starts.sort(key=lambda row: row["sentence_index"])
    if len(starts) != report["sentence_count"]:
        raise SystemExit(f"Expected {report['sentence_count']} sentence transitions, found {len(starts)}")
    offsets = []
    boundaries = []
    for previous, current in zip(starts, starts[1:]):
        duration = expected[previous["sentence_index"]]["audio_duration_ms"]
        elapsed = current["uptime_ms"] - previous["uptime_ms"]
        inserted_announcement_ms = 0.0
        if failover_event and announcement_ready and previous["sentence_index"] == failover_event["at_sentence_index"] - 1:
            inserted_announcement_ms = announcement_ready.get("audio_duration_ms") or max(
                0.0, (announcement_ready.get("wav_bytes", 44) - 44) / 48.0)
        deviation = elapsed - duration - inserted_announcement_ms
        offsets.append(deviation)
        boundaries.append({
            "after_sentence_index": previous["sentence_index"],
            "actual_transition_interval_ms": elapsed,
            "previous_chunk_audio_ms": duration,
            "inserted_announcement_audio_ms": inserted_announcement_ms,
            "estimated_extra_gap_ms": deviation,
        })
    report["playback_observed"] = {
        "queued": queued,
        "first_sentence_started": starts[0],
        "first_sentence_started_after_playback_queued_ms": starts[0]["uptime_ms"] - queued["uptime_ms"] if queued else None,
        "transitions_observed": len(starts),
        "last_sentence_started": starts[-1],
        "playback_ended": ended,
        "generation_complete": complete,
        "failover": failover_event,
        "recovery_attempt": next((row for row in reversed(run_rows) if row.get("test") == "speech_provider_recovery_attempt"), None),
        "recovered": next((row for row in reversed(run_rows) if row.get("test") == "speech_provider_recovered"), None),
        "offline_announcement_started": announcement_event,
        "offline_announcement_audio": announcement_ready,
        "provider_modes_heard_or_queued": sorted({row.get("provider", "unknown") for row in starts}),
        "provider_sequence_observed": [
            provider for index, provider in enumerate(row.get("provider", "unknown") for row in starts)
            if index == 0 or provider != starts[index - 1].get("provider", "unknown")
        ],
        "boundary_intervals": boundaries,
        "estimated_boundary_gap_ms": {
            "minimum": min(offsets) if offsets else None,
            "median": statistics.median(offsets) if offsets else None,
            "maximum": max(offsets) if offsets else None,
            "basis": "wall-clock delta between Media3 item-transition callbacks minus preceding WAV duration; approximate, not acoustic silence measurement",
        },
        "listener_feedback": None if (args.failover or args.real_outage or args.auto_recovery) else "User said the incremental sentence-joined article sounded good.",
    }
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    trace_path = args.output_dir / f"{stem}-playback.jsonl"
    trace_path.write_text("\n".join(json.dumps(row, ensure_ascii=False) for row in starts) + "\n", encoding="utf-8")
    print(json.dumps({
        "sentence_transitions": len(starts),
        "first_audio_ready_ms": queued.get("time_to_playable_head_ms") if queued else None,
        "generation_complete_ms": complete.get("generation_wall_ms") if complete else None,
        "playback_ended": ended is not None,
        "estimated_boundary_gap_ms": report["playback_observed"]["estimated_boundary_gap_ms"],
        "listener_feedback_recorded": not (args.failover or args.real_outage or args.auto_recovery),
    }, indent=2))


if __name__ == "__main__":
    main()
