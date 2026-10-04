#!/usr/bin/env python3
"""Azure Speech TTS client for Hermes' `command`-type TTS provider.

Hermes has no builtin `azure` TTS provider (``BUILTIN_TTS_PROVIDERS`` in
``tools/tts_command_provider.py`` covers edge/elevenlabs/openai/minimax/xai/
mistral/gemini/neutts/kittentts/piper/deepinfra only), so Azure is wired as a
``tts.providers.azure`` command provider.

Reads AZURE_SPEECH_KEY + AZURE_SPEECH_REGION from the environment. Hermes runs
command providers with a SECRET-SCRUBBED child env, so config.yaml must list
both vars under the provider's ``env_passthrough`` or the key never arrives.

Stdlib only (urllib) -- no pip install required.

Usage (what Hermes invokes):
    python azure_tts.py --input {input_path} --output {output_path} \
                        [--voice en-GB-RyanNeural] [--format mp3] [--speed 1.0]

Exits non-zero with a stderr message on any failure, so Hermes surfaces
`tts provider 'azure' exited with code N: stderr: ...`.
"""

from __future__ import annotations

import argparse
import os
import ssl
import sys
import urllib.error
import urllib.request
from xml.sax.saxutils import escape

# Hermes' COMMAND_TTS_OUTPUT_FORMATS -> Azure X-Microsoft-OutputFormat.
FORMAT_MAP = {
    "mp3": "audio-24khz-48kbitrate-mono-mp3",
    "wav": "riff-24khz-16bit-mono-pcm",
    "ogg": "ogg-24khz-16bit-mono-opus",
    "opus": "ogg-24khz-16bit-mono-opus",
    "flac": "riff-24khz-16bit-mono-pcm",   # Azure has no native flac
    "m4a": "aac-24khz-96kbit-mono-m4a",
    "aac": "aac-24khz-96kbit-mono-m4a",
    "amr": "amr-wb-16khz-64kbit-mono-amr",
}

DEFAULT_VOICE = "en-GB-RyanNeural"   # matches tts.edge.voice in config.yaml
SSML_URL = "https://{region}.tts.speech.microsoft.com/cognitiveservices/v1"


def fail(message: str, code: int = 1) -> "NoReturn":  # noqa: F821
    print(f"azure_tts: {message}", file=sys.stderr, flush=True)
    raise SystemExit(code)


def read_text(path: str) -> str:
    try:
        with open(path, "r", encoding="utf-8") as handle:
            return handle.read().strip()
    except OSError as exc:
        fail(f"cannot read input file {path}: {exc}")


def build_ssml(text: str, voice: str, rate: str | None) -> bytes:
    """SSML v1 document. `rate` is an SSML prosody rate (e.g. '+10%')."""
    body = escape(text)
    if rate:
        body = f'<prosody rate="{rate}">{body}</prosody>'
    doc = (
        "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' "
        f"xml:lang='en-GB'><voice name='{voice}'>{body}</voice></speak>"
    )
    return doc.encode("utf-8")


def speed_to_rate(speed: str | None) -> str | None:
    """Hermes {speed} (may be empty/garbage) -> SSML rate, clamped to Azure's 0.5-2.0."""
    if not speed:
        return None
    try:
        factor = float(speed)
    except (TypeError, ValueError):
        return None
    if factor <= 0:
        return None
    factor = min(max(factor, 0.5), 2.0)
    return "+0%" if abs(factor - 1.0) < 0.01 else f"{'+' if factor > 1 else '-'}{abs(round((factor - 1) * 100)):d}%"


def main() -> int:
    parser = argparse.ArgumentParser(description="Azure Speech TTS for Hermes")
    parser.add_argument("--input", required=True, help="file containing the text to speak")
    parser.add_argument("--output", required=True, help="path to write audio to")
    parser.add_argument("--voice", default=DEFAULT_VOICE)
    parser.add_argument("--format", default="mp3")
    parser.add_argument("--speed", default=None)
    args = parser.parse_args()

    key = (os.environ.get("AZURE_SPEECH_KEY") or "").strip()
    region = (os.environ.get("AZURE_SPEECH_REGION") or "").strip()
    if not key:
        fail("AZURE_SPEECH_KEY is not set in the provider env "
             "(add it to env_passthrough in the azure provider config)")
    if not region:
        fail("AZURE_SPEECH_REGION is not set in the provider env")

    text = read_text(args.input)
    if not text:
        fail("input text is empty")

    out_format = (args.format or "mp3").lower().lstrip(".")
    azure_format = FORMAT_MAP.get(out_format)
    if azure_format is None:
        fail(f"unsupported --format {out_format!r} (supported: {', '.join(sorted(FORMAT_MAP))})")

    payload = build_ssml(text, args.voice or DEFAULT_VOICE, speed_to_rate(args.speed))
    request = urllib.request.Request(
        SSML_URL.format(region=region),
        data=payload,
        headers={
            "Ocp-Apim-Subscription-Key": key,
            "Content-Type": "application/ssml+xml; charset=utf-8",
            "X-Microsoft-OutputFormat": azure_format,
            "User-Agent": "hermes-azure-tts/1.0",
        },
        method="POST",
    )
    context = ssl.create_default_context()
    try:
        with urllib.request.urlopen(request, timeout=60, context=context) as response:
            audio = response.read()
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", "replace")[:400] or exc.reason
        fail(f"Azure returned HTTP {exc.code}: {detail}")
    except urllib.error.URLError as exc:
        fail(f"cannot reach {region}.tts.speech.microsoft.com: {exc.reason}")

    if not audio:
        fail("Azure returned an empty audio body")

    try:
        os.makedirs(os.path.dirname(os.path.abspath(args.output)) or ".", exist_ok=True)
        with open(args.output, "wb") as handle:
            handle.write(audio)
    except OSError as exc:
        fail(f"cannot write {args.output}: {exc}")

    print(f"azure_tts: wrote {len(audio)} bytes to {args.output}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
