"""Add ID3 chapter variants to an Android-generated LAME fixture and verify them."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/python-deps"))
from mutagen.id3 import CHAP, CTOC, CTOCFlags, ID3, TPE1, TIT2
from mutagen.mp3 import MP3
from imageio_ffmpeg import get_ffmpeg_exe

source = ROOT / "results/lame-android-base.mp3"
if not source.is_file():
    raise SystemExit(f"Missing Android-generated MP3: {source}")
ffmpeg = get_ffmpeg_exe()
report = {"source": source.name, "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
          "generator": "Android LAME 3.100 encoder-only JNI on Galaxy S26 Ultra",
          "device": {"model": "SM-S948U", "android": 17, "api": 37},
          "encoder": {"version": "3.100", "license": "LGPL-2.0-or-later",
                      "decoder_included": False, "sample_rate_hz": 24000,
                      "channels": 1, "bitrate_bps": 96000, "requested_duration_s": 900,
                      "encode_ms": 1460.819218, "base_bytes": source.stat().st_size,
                      "measured_duration_s": MP3(source).info.length},
          "device_output_path": "/storage/emulated/0/Android/data/dev.yomu.spike/files/Music/lame-15-minute-base.mp3",
          "files": []}
for version in (3, 4):
    path = ROOT / f"results/lame-android-chapters-v2{version}.mp3"
    path.write_bytes(source.read_bytes())
    tags = ID3()
    tags.add(TIT2(encoding=3, text=["Yomu Android LAME chapter fixture"]))
    tags.add(TPE1(encoding=3, text=["Yomu spike"])
    )
    ids = [f"ch{i + 1}" for i in range(3)]
    for i, element_id in enumerate(ids):
        tags.add(CHAP(element_id=element_id, start_time=i * 300000,
                      end_time=(i + 1) * 300000, start_offset=0xffffffff,
                      end_offset=0xffffffff,
                      sub_frames=[TIT2(encoding=3, text=[f"Chapter {i + 1}"])]))
    tags.add(CTOC(element_id="toc", flags=CTOCFlags.TOP_LEVEL | CTOCFlags.ORDERED,
                  child_element_ids=ids))
    tags.save(path, v2_version=version)
    parsed = ID3(path)
    chapters = sorted(parsed.getall("CHAP"), key=lambda c: c.start_time)
    toc = parsed.getall("CTOC")
    assert parsed.version == (2, version, 0), parsed.version
    assert [(c.start_time, c.end_time) for c in chapters] == [
        (0, 300000), (300000, 600000), (600000, 900000)]
    assert [str(c.sub_frames["TIT2"]) for c in chapters] == [
        "Chapter 1", "Chapter 2", "Chapter 3"]
    assert len(toc) == 1 and toc[0].child_element_ids == ids
    audio = MP3(path)
    assert abs(audio.info.length - 900) < 0.2
    subprocess.run([ffmpeg, "-v", "error", "-i", str(path), "-f", "null", "NUL"], check=True)
    report["files"].append({"file": path.name, "id3_version": parsed.version,
                            "duration_s": audio.info.length, "bitrate": audio.info.bitrate,
                            "chapters": [{"id": c.element_id, "start_ms": c.start_time,
                                          "end_ms": c.end_time,
                                          "title": str(c.sub_frames["TIT2"])} for c in chapters],
                            "toc": ids, "decode": "passed", "bytes": path.stat().st_size,
                            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                            "pocket_casts_android": "passed: user confirmed Chapters 1-3 display and next/previous chapter navigation for both ID3 variants"})
out = ROOT / "results/lame-android-inspection.json"
out.write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report, indent=2))
