import hashlib
import json
import tarfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
archive = ROOT / "downloads/espeak-ng-data-upstream.tar.bz2"
bundle = ROOT / "downloads/kokoro-en-v0_19/espeak-ng-data"
out = ROOT / "results/espeak-data-asset-audit.json"

def sha_bytes(data):
    return hashlib.sha256(data).hexdigest()

bundle_files = {}
for path in bundle.rglob("*"):
    if path.is_file():
        rel = path.relative_to(bundle).as_posix()
        bundle_files[rel] = {"bytes": path.stat().st_size, "sha256": sha_bytes(path.read_bytes())}

archive_files = {}
with tarfile.open(archive, "r:bz2") as tf:
    for member in tf.getmembers():
        if member.isfile() and member.name.startswith("espeak-ng-data/"):
            rel = member.name.removeprefix("espeak-ng-data/")
            data = tf.extractfile(member).read()
            archive_files[rel] = {"bytes": len(data), "sha256": sha_bytes(data)}

missing = sorted(set(bundle_files) - set(archive_files))
extra = sorted(set(archive_files) - set(bundle_files))
mismatched = sorted(k for k in set(bundle_files) & set(archive_files) if bundle_files[k] != archive_files[k])
result = {
    "source_url": "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2",
    "archive": {"path": str(archive.relative_to(ROOT)), "bytes": archive.stat().st_size, "sha256": sha_bytes(archive.read_bytes())},
    "bundle_tree": {"files": len(bundle_files), "bytes": sum(x["bytes"] for x in bundle_files.values())},
    "archive_tree": {"files": len(archive_files), "bytes": sum(x["bytes"] for x in archive_files.values())},
    "exact_tree_match": not (missing or extra or mismatched),
    "missing_from_archive": missing,
    "extra_in_archive": extra,
    "mismatched": mismatched,
    "device_english_subset_test": {
        "device": "Samsung Galaxy S26 Ultra (SM-S948U), Android 17/API 37",
        "full_tree_bytes": 17991651,
        "subset_with_english_voice_variants_bytes": 889580,
        "subset_without_espeak_voice_variants_bytes": 838291,
        "size_reduction_without_voice_variants_percent": round((1 - 838291 / 17991651) * 100, 2),
        "synthesis_succeeded_without_voice_variants": True,
        "sample_rate_hz": 24000,
        "sample_duration_ms": 3921.625,
        "test_text": "English pronunciation still works without eSpeak voice variants.",
        "interpretation": "Kokoro's speaker identity/style vectors are separate from eSpeak voice-variant files; this test proves English Kokoro synthesis works with the minimal selected data, not every possible phonemizer input or every locale.",
    },
    "device_voice_regression": {
        "evidence": "results/english-subset-voice-regression.json",
        "voice_ids_tested": list(range(11)),
        "all_voices_passed": True,
        "corpus_chars": 214,
        "corpus_features": ["currency", "date", "abbreviations", "possessives", "ordinal", "time", "quotation", "punctuation"],
        "subset_bytes": 838291,
        "espeak_voice_variants_included": False,
        "interpretation": "All eleven packaged Kokoro speaker IDs generated non-empty 24 kHz audio on the Galaxy using the minimal English subset. This checks synthesis operation and selected input classes, not subjective pronunciation quality or every English spelling/name.",
    },
    "files": bundle_files,
}
out.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(json.dumps({k: v for k, v in result.items() if k != "files"}, indent=2))
