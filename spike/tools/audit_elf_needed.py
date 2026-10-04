"""Extract ELF DT_NEEDED entries from the built debug APK (no third-party parser)."""
from pathlib import Path
import json, struct, zipfile

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "android/app/build/outputs/apk/debug/app-debug.apk"
OUT = ROOT / "results/elf-needed.json"

def needed(data: bytes):
    if data[:4] != b"\x7fELF" or data[4] != 2 or data[5] != 1:
        raise ValueError("Expected little-endian ELF64 shared object")
    phoff = struct.unpack_from("<Q", data, 32)[0]
    phentsize, phnum = struct.unpack_from("<HH", data, 54)
    loads, dynamic = [], None
    for i in range(phnum):
        off = phoff + i * phentsize
        kind = struct.unpack_from("<I", data, off)[0]
        fileoff, vaddr = struct.unpack_from("<QQ", data, off + 8)
        filesz = struct.unpack_from("<Q", data, off + 32)[0]
        if kind == 1:
            loads.append((vaddr, vaddr + filesz, fileoff))
        elif kind == 2:
            dynamic = (fileoff, filesz)
    if dynamic is None:
        return []
    tags = []
    for off in range(dynamic[0], dynamic[0] + dynamic[1], 16):
        tag, value = struct.unpack_from("<qQ", data, off)
        if tag == 0:
            break
        tags.append((tag, value))
    strtab_addr = next((v for t, v in tags if t == 5), None)
    if strtab_addr is None:
        return []
    strtab_off = next((fo + strtab_addr - start for start, end, fo in loads if start <= strtab_addr < end), None)
    if strtab_off is None:
        raise ValueError("Could not map ELF DT_STRTAB")
    result = []
    for tag, value in tags:
        if tag == 1:
            start = strtab_off + value
            end = data.index(b"\0", start)
            result.append(data[start:end].decode("ascii", errors="replace"))
    return result

rows = []
android_system_libs = {"libandroid.so", "libc.so", "libdl.so", "liblog.so", "libm.so",
                       "libmediandk.so", "libOpenSLES.so", "libz.so"}
with zipfile.ZipFile(APK) as apk:
    libs = {Path(name).name: apk.read(name) for name in apk.namelist()
            if name.startswith("lib/arm64-v8a/") and name.endswith(".so")}
packaged = set(libs)
for name, data in sorted(libs.items()):
    deps = needed(data)
    rows.append({"file": name, "bytes": len(data), "needed": deps,
                 "android_system_dependencies": [d for d in deps if d in android_system_libs],
                 "other_external_dependencies": [d for d in deps if d not in packaged and d not in android_system_libs]})
OUT.write_text(json.dumps({"apk": str(APK.relative_to(ROOT)), "abi": "arm64-v8a",
    "scope": "Built APK ELF DT_NEEDED only; does not recover static linkage or prove upstream source closure",
    "libraries": rows}, indent=2), encoding="utf-8")
print(OUT)
print(json.dumps(rows, indent=2))
