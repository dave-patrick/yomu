"""Audit PT_LOAD alignment for every arm64 ELF bundled in the built APK."""
from pathlib import Path
import hashlib
import json
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "android/app/build/outputs/apk/debug/app-debug.apk"
OUT = ROOT / "results"

def load_alignments(data: bytes):
    if data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("Expected little-endian ELF64")
    phoff = struct.unpack_from("<Q", data, 32)[0]
    phentsize, phnum = struct.unpack_from("<HH", data, 54)
    alignments = []
    for i in range(phnum):
        offset = phoff + i * phentsize
        p_type, _, _, _, _, _, _, p_align = struct.unpack_from("<IIQQQQQQ", data, offset)
        if p_type == 1:
            alignments.append(p_align)
    return alignments

rows = []
with zipfile.ZipFile(APK) as archive:
    for name in sorted(n for n in archive.namelist()
                       if n.startswith("lib/arm64-v8a/") and n.endswith(".so")):
        data = archive.read(name)
        alignments = load_alignments(data)
        rows.append({"file": name, "load_segment_alignment_bytes": alignments,
                     "all_load_segments_16k_or_higher": bool(alignments) and min(alignments) >= 16384,
                     "sha256": hashlib.sha256(data).hexdigest()})
assert rows and all(row["all_load_segments_16k_or_higher"] for row in rows), "At least one arm64 ELF has a LOAD segment below 16 KB"
(OUT / "apk-native-alignment.json").write_text(json.dumps(rows, indent=2), encoding="utf-8")
(OUT / "apk-sha256.json").write_text(json.dumps({"Algorithm": "SHA256",
    "Hash": hashlib.sha256(APK.read_bytes()).hexdigest(), "Path": str(APK)}, indent=2), encoding="utf-8")
print(f"Validated {len(rows)} arm64 ELF files; all PT_LOAD segments are 16 KB aligned.")
