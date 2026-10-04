"""Compare Tesseract4Android AAR native files to the built APK and record ABI dependencies."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path


LICENSES = {
    "libtesseract.so": "Apache-2.0 (Tesseract 5.5.1)",
    "libleptonica.so": "BSD-2-Clause (Leptonica 1.85.0)",
    "libjpeg.so": "Independent JPEG Group custom permissive license (libjpeg v9f)",
    "libpngx.so": "PNG Reference Library License v2 (libpng 1.6.48)",
}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def readelf(path: Path, args: list[str]) -> str:
    return subprocess.run([str(path), *args], check=True, capture_output=True, text=True).stdout


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--readelf", type=Path, required=True)
    parser.add_argument("--aar", type=Path, required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("results/tesseract-native-bom.json"))
    args = parser.parse_args()
    libraries = []
    with zipfile.ZipFile(args.aar) as aar, zipfile.ZipFile(args.apk) as apk:
        apk_names = set(apk.namelist())
        for name in sorted(x for x in aar.namelist() if x.startswith("jni/arm64-v8a/") and x.endswith(".so")):
            blob = aar.read(name)
            lib_name = Path(name).name
            apk_path = f"lib/arm64-v8a/{lib_name}"
            packaged = apk.read(apk_path) if apk_path in apk_names else None
            temp = args.output.parent / ".tesseract-audit" / lib_name
            temp.parent.mkdir(parents=True, exist_ok=True)
            temp.write_bytes(blob)
            dynamic = readelf(args.readelf, ["-d", str(temp)])
            program = readelf(args.readelf, ["-lW", str(temp)])
            needed = re.findall(r"Shared library: \[(.*?)\]", dynamic)
            alignments = [int(line.split()[-1], 16) for line in program.splitlines()
                          if line.lstrip().startswith("LOAD") and line.split()[-1].startswith("0x")]
            libraries.append({
                "name": lib_name,
                "aar_entry": name,
                "size_bytes": len(blob),
                "sha256": sha(blob),
                "license": LICENSES.get(lib_name, "unclassified"),
                "apk_entry": apk_path if packaged is not None else None,
                "apk_sha256": sha(packaged) if packaged is not None else None,
                "apk_matches_aar": packaged == blob if packaged is not None else False,
                "needed": needed,
                "load_segment_alignments": sorted(set(alignments)),
            })
    aar_sha = sha(args.aar.read_bytes())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({
        "component": "cz.adaptech.tesseract4android:tesseract4android:4.9.0",
        "aar_sha256": aar_sha,
        "apk_sha256": sha(args.apk.read_bytes()),
        "dynamic_system_dependencies": sorted({dep for lib in libraries for dep in lib["needed"] if dep not in {x["name"] for x in libraries}}),
        "libraries": libraries,
        "license_sources": {
            "wrapper": "results/licenses/tesseract/Tesseract4Android-4.9.0-LICENSE.txt",
            "tesseract": "results/licenses/tesseract/Tesseract-5.5.1-LICENSE.txt",
            "leptonica": "results/licenses/tesseract/Leptonica-1.85.0-LICENSE.txt",
            "libjpeg": "results/licenses/tesseract/libjpeg-v9f-README.txt",
            "libpng": "results/licenses/tesseract/libpng-1.6.48-LICENSE.txt",
        },
        "model": {"name": "eng.traineddata (tessdata_fast)", "bytes": 4113088,
                  "sha256": "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
                  "license": "Apache-2.0"},
        "source_notes": "Version mapping follows the official Tesseract4Android 4.9.0 release README; SHA-256 matches were compared directly between the resolved AAR and APK.",
    }, indent=2))
    print(args.output)


if __name__ == "__main__":
    main()
