"""Summarize allocated static-archive contributions in a sherpa LLD link map."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import defaultdict
from pathlib import Path


ARCHIVE = re.compile(r"(?:^|\.lto\.|[\\/])(?P<archive>lib[^/\\\s:]+?\.a)\((?P<member>[^)]*)\)")
SECTION = re.compile(r":\((?P<section>\.[^)]+)\)")
ALLOCATED = re.compile(r"^\.(?:text|rodata|data|bss|init_array|fini_array|eh_frame|got|tdata|tbss)(?:\.|$)")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--map", type=Path, required=True)
    parser.add_argument("--binary", type=Path, required=True,
                        help="Stripped sherpa-onnx-jni.so produced from the same link")
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--device-smoke", type=Path)
    args = parser.parse_args()

    archives: dict[str, dict] = defaultdict(lambda: {"members": set(), "allocated_section_rows": 0,
                                                     "allocated_section_bytes": 0})
    for line in args.map.read_text(encoding="utf-8", errors="replace").splitlines():
        match = ARCHIVE.search(line)
        section = SECTION.search(line)
        if not match or not section or not ALLOCATED.match(section.group("section")):
            continue
        columns = line.split()
        if len(columns) < 4:
            continue
        name = match.group("archive")
        name = name.rsplit(".lto.", 1)[-1]
        member = re.sub(r" at \d+$", "", match.group("member"))
        record = archives[name]
        record["members"].add(member)
        record["allocated_section_rows"] += 1
        record["allocated_section_bytes"] += int(columns[2], 16)

    archive_rows = []
    for name, record in sorted(archives.items()):
        archive_rows.append({"archive": name, "linked_member_count": len(record["members"]),
                             "linked_members": sorted(record["members"]),
                             "allocated_section_rows": record["allocated_section_rows"],
                             "allocated_section_bytes": record["allocated_section_bytes"]})

    result = {
        "scope": "Fresh arm64 Android source build of the pinned sherpa JNI target; LLD map counts allocated input sections from static archives. This is a build-closure result, not a legal opinion or a reconstruction of the separately downloaded prebuilt archive.",
        "sherpa_commit": args.commit,
        "configuration": {
            "abi": "arm64-v8a", "android_platform": 21, "ndk": "28.2.13676358",
            "build_type": "Release", "tts": True, "jni": True,
            "speaker_diarization": False, "c_api": False, "binaries": False,
            "websocket": False, "python": False, "tests": False,
            "portaudio": False, "onnxruntime": "1.28.2 shared",
            "espeak_ng": "ed530aa113046142eb5115cf2fc9157854d0ffe1",
            "piper_phonemize": "f3ff95afc03640bc1399e113e83361192a2fafb4",
            "link_time_optimization": True,
        },
        "output_binary": {"path": str(args.binary), "bytes": args.binary.stat().st_size,
                          "sha256": sha256(args.binary)},
        "link_map": {"path": str(args.map), "bytes": args.map.stat().st_size,
                      "sha256": sha256(args.map)},
        "static_archives_with_allocated_sections": archive_rows,
        "runtime_dependencies_from_readelf": [
            "libandroid.so", "liblog.so", "libonnxruntime.so", "libm.so", "libdl.so", "libc.so"
        ],
        "license_classification": {
            "libespeak-ng.a and libucd.a": "GPL-3.0-or-later; source headers state version 3 or later",
            "libpiper_phonemize.a": "MIT; statically links the GPL eSpeak-NG implementation",
            "libkaldi-decoder-core.a": "Apache-2.0",
            "libkaldi-native-fbank-core.a": "Apache-2.0; includes BSD-3-Clause KISS FFT code",
            "libsherpa-onnx-kaldifst-core.a": "Apache-2.0 Kaldifst and Apache-2.0 OpenFST sources; Eigen headers are MPL-2.0",
            "libsherpa-onnx-fst.a, libsherpa-onnx-fstfar.a, libsherpa-onnx-core.a": "Apache-2.0 sherpa-onnx source",
            "nlohmann/json headers": "MIT; header-only code is compiled into sherpa core",
            "libssentencepiece_core.a": "Apache-2.0",
            "libc++_static.a, libc++abi.a, libunwind.a, libclang_rt.builtins-aarch64-android.a": "Android NDK LLVM runtime; Apache-2.0 WITH LLVM-exception and bundled NOTICE/third-party notices",
            "libonnxruntime.so": "MIT top-level license plus the versioned ONNX Runtime ThirdPartyNotices.txt",
        },
        "not_included_by_configuration": ["hclust speaker-diarization library", "websocketpp/Asio", "PortAudio", "eSpeak MBROLA, libsonic, libpcaudio, Klatt, and speech-player options"],
    }
    if args.device_smoke:
        smoke = json.loads(args.device_smoke.read_text(encoding="utf-8-sig"))
        if smoke.get("test") != "offline_provider_smoke" or smoke.get("phonemizer") != "espeak-ng":
            raise SystemExit("The on-device smoke evidence does not describe the expected local Kokoro path")
        result["device_smoke"] = smoke
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps({"output": str(args.output), "binary_bytes": result["output_binary"]["bytes"],
                      "static_archives": len(archive_rows),
                      "archive_member_count": sum(row["linked_member_count"] for row in archive_rows),
                      "archives": [{"name": row["archive"], "members": row["linked_member_count"],
                                    "bytes": row["allocated_section_bytes"]} for row in archive_rows]}, indent=2))


if __name__ == "__main__":
    main()
