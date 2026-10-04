import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
model = ROOT / "downloads/kokoro-en-v0_19"
assets = ["model.onnx", "voices.bin", "tokens.txt", "LICENSE", "README.md"]
info = {}
for name in assets:
    p = model / name
    b = p.read_bytes()
    info[name] = {"bytes": len(b), "sha256": hashlib.sha256(b).hexdigest()}

result = {
    "checked_date": "2026-10-02",
    "upstream": {
        "repository": "https://huggingface.co/hexgrad/kLegacy",
        "revision": "d0acca3b0748cae52e0a14a28ad03515002ff22f",
        "license": "Apache-2.0",
        "revision_metadata_api": "https://huggingface.co/api/models/hexgrad/kLegacy",
        "revision_claim": "Hub API current repository revision at audit time; v0.19 path within that revision",
    },
    "sherpa_export_provenance": {
        "script": "https://github.com/k2-fsa/sherpa-onnx/tree/master/scripts/kokoro/v0.19",
        "workflow": "https://github.com/k2-fsa/sherpa-onnx/blob/master/.github/workflows/export-kokoro.yaml",
        "notes": [
            "run.sh obtains kLegacy/v0.19/kokoro-v0_19.onnx, generates voices.bin and tokens.txt, and adds sherpa metadata to model.onnx",
            "voices.bin is concatenated float32 data for 11 vectors, each 511*256*4 bytes, using the IDs recorded below",
            "Sherpa's eSpeak data tarball is a separate GPL-3.0-or-later input; its exact file tree is audited separately",
        ],
        "voice_id_map": {
            "0": "af", "1": "af_bella", "2": "af_nicole", "3": "af_sarah", "4": "af_sky",
            "5": "am_adam", "6": "am_michael", "7": "bf_emma", "8": "bf_isabella", "9": "bm_george", "10": "bm_lewis"
        },
    },
    "local_assets": info,
    "upstream_model_blob": {
        "path": "v0.19/kokoro-v0_19.onnx",
        "bytes": 345554732,
        "sha256": "ebef42457f7efee9b60b4f1d5aec7692f2925923948a0d7a2a49d2c9edf57e49",
        "note": "Sherpa's run.sh subsequently adds metadata; bundled model.onnx is therefore an expected transformed artifact and not byte-identical to the Hub ONNX blob.",
    },
    "voice_source_files": {
        "source_directory": "https://huggingface.co/hexgrad/kLegacy/tree/d0acca3b0748cae52e0a14a28ad03515002ff22f/v0.19/voices",
        "license": "Apache-2.0 (repository revision metadata)",
        "method": "The pinned sherpa v0.19 generator loads each listed source .pt tensor and writes its float32 bytes in ID order to voices.bin.",
        "source_file_hub_lfs_sha256": {
            "af.pt": "fad4192fd8a840f925b0e3fc2be54e20531f91a9ac816a485b7992ca0bd83ebf",
            "af_bella.pt": "2828c6c2f94275ef3441a2edfcf48293298ee0f9b56ce70fb2e344345487b922",
            "af_nicole.pt": "9401802fb0b7080c324dec1a75d60f31d977ced600a99160e095dbc5a1172692",
            "af_sarah.pt": "ba7918c4ace6ace4221e7e01eb3a6d16596cba9729850551c758cd2ad3a4cd08",
            "af_sky.pt": "9f16f1bb778de36a177ae4b0b6f1e59783d5f4d3bcecf752c3e1ee98299b335e",
            "am_adam.pt": "1921528b400a553f66528c27899d95780918fe33b1ac7e2a871f6a0de475f176",
            "am_michael.pt": "a255c9562c363103adc56c09b7daf837139d3bdaa8bd4dd74847ab1e3e8c28be",
            "bf_emma.pt": "992e6d8491b8926ef4a16205250e51a21d9924405a5d37e2db6e94adfd965c3b",
            "bf_isabella.pt": "d0865a03931230100167f7a81d394b143c072efe2d7e4c4a87b5c54d6283f580",
            "bm_george.pt": "7d763dfe13e934357f4d8322b718787d79e32f2181e29ca0cf6aa637d8092b96",
            "bm_lewis.pt": "f70d9ea4d65f522f224628f06d86ea74279faae23bd7e765848a374aba916b76"
        },
        "note": "Source vector hashes are the Hugging Face LFS SHA-256 metadata at the pinned repository revision; voices.bin was not independently regenerated from the 11 vectors in this audit.",
    },
    "limitations": [
        "Apache-2.0 repository licensing does not independently establish the legal status of every training data item; the model card's dataset statements remain upstream author claims.",
        "The voice asset is a generated concatenation of vectors rather than individual source files; reproducible regeneration from the pinned source and pinned generator remains a release task.",
    ],
}
out = ROOT / "results/kokoro-asset-audit.json"
out.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(json.dumps({"output": str(out), "local_assets": info, "voice_bytes_expected_from_shape": 11 * 511 * 256 * 4}, indent=2))
