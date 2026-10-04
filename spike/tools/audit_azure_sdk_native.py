"""Inventory Azure Speech SDK redistribution and native dependency evidence."""
from pathlib import Path
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
out = ROOT / "results"
runtime = json.loads((out / "runtime-bom.json").read_text(encoding="utf-8"))
sdk = next(row for row in runtime if row["group"] == "com.microsoft.cognitiveservices.speech")
aar = Path(sdk["file"])
elf = json.loads((out / "elf-needed.json").read_text(encoding="utf-8"))
codec = next(row for row in elf["libraries"]
             if row["file"] == "libMicrosoft.CognitiveServices.Speech.extension.codec.so")
with zipfile.ZipFile(aar) as archive:
    names = archive.namelist()
    redist = archive.read("REDIST.txt").decode("utf-8", "replace")
    redistributable = [line.strip() for line in redist.splitlines()
                       if line.strip().startswith("libMicrosoft.CognitiveServices.Speech.")]
    native = [name for name in names if name.startswith("jni/arm64-v8a/") and name.endswith(".so")]
    third_party = archive.read("ThirdPartyNotices.md").decode("utf-8", "replace")
    license_text = archive.read("LICENSE.md").decode("utf-8", "replace")

device_evidence = json.loads((out / "azure-composite-device.json").read_text(encoding="utf-8"))
runtime_trace_path = out / "azure-sdk-native-load-device.json"
runtime_trace = json.loads(runtime_trace_path.read_text(encoding="utf-8")) if runtime_trace_path.exists() else None
runtime_trace_proves_no_codec = bool(runtime_trace and
    not runtime_trace.get("codec_extension_loaded") and
    not runtime_trace.get("gstreamer_loaded") and
    not any("Microsoft.CognitiveServices.Speech" in path for path in runtime_trace.get("during_synthesis", [])))
report = {
    "sdk": f"{sdk['group']}:{sdk['name']}:{sdk['version']}",
    "aar": str(aar),
    "aar_sha256": hashlib.sha256(aar.read_bytes()).hexdigest(),
    "pom_license": sdk.get("pom_declared_licenses"),
    "redist_native_entries": redistributable,
    "aar_arm64_native_libraries": native,
    "codec_extension_dt_needed": codec["needed"],
    "missing_codec_dependencies": codec["other_external_dependencies"],
    "gstreamer_library_present_in_aar": any("libgstreamer_android.so" in name for name in names),
    "gstreamer_library_present_in_apk": any("libgstreamer_android.so" == row["file"]
                                              for row in elf["libraries"]),
    "gstreamer_named_in_third_party_notices": "gstreamer" in third_party.lower(),
    "sdk_terms_mark_redistributables": "REDIST.TXT" in license_text and "Distribution Requirements" in license_text,
    "on_device_sdk_synthesis_passed": device_evidence.get("test") == "azure_synthesis" and device_evidence.get("boundaries", 0) > 0,
    "runtime_native_load_trace": {
        "evidence_file": str(runtime_trace_path) if runtime_trace else None,
        "pcm_wav_synthesis_passed": bool(runtime_trace and runtime_trace.get("wav_bytes", 0) > 0),
        "sdk_native_modules_mapped_during_synthesis": sorted({
            path for path in (runtime_trace.get("during_synthesis", []) if runtime_trace else [])
            if "Microsoft.CognitiveServices.Speech" in path
        }),
        "codec_extension_loaded_during_synthesis": runtime_trace.get("codec_extension_loaded") if runtime_trace else None,
        "gstreamer_loaded_during_synthesis": runtime_trace.get("gstreamer_loaded") if runtime_trace else None,
        "scope": "One Android PCM WAV route; does not cover other SDK features or establish license compatibility.",
    },
    "status": ("PCM WAV route device trace passed with no SDK native module, codec extension, or GStreamer mapping; "
               "the absent codec dependency is not exercised by that route. Proprietary SDK redistribution terms and "
               "GPL/application-license interaction remain unresolved." if runtime_trace_proves_no_codec else
               "OPEN: establish whether the SDK codec extension's absent libgstreamer_android.so dependency is used by the selected PCM/WAV route; proprietary SDK redistribution terms and GPL/application-license interaction require review."),
}
(out / "azure-sdk-native-audit.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps({k: report[k] for k in ("aar_sha256", "missing_codec_dependencies",
      "gstreamer_library_present_in_aar", "gstreamer_library_present_in_apk",
      "gstreamer_named_in_third_party_notices", "on_device_sdk_synthesis_passed",
      "runtime_native_load_trace", "status")}, indent=2))
