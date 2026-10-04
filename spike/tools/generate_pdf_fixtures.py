"""Create selectable, scanned-image, and mixed-page OCR fixtures for the Android Spike."""
from __future__ import annotations

import hashlib
import textwrap
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from reportlab.lib.pagesizes import letter
from reportlab.lib.utils import ImageReader
from reportlab.pdfgen import canvas


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "results" / "pdf-fixtures"
LINES = [
    "OCR accuracy fixture for Yomu PDF import.",
    "The quick brown fox reads beside the quiet river.",
    "Numbers and punctuation: 17, 42.5%, and A/B testing.",
    "This sentence is intentionally long enough to test line wrapping and page reading order.",
]


def make_scan(path: Path) -> Image.Image:
    image = Image.new("RGB", (1800, 2400), "white")
    draw = ImageDraw.Draw(image)
    font = ImageFont.truetype(r"C:\Windows\Fonts\arial.ttf", 48)
    y = 220
    draw.text((180, y), "SCANNED DOCUMENT TEST PAGE", font=font, fill="black")
    y += 150
    for line in LINES:
        for segment in textwrap.wrap(line, 54):
            draw.text((180, y), segment, font=font, fill="black")
            y += 90
        y += 35
    image.save(path, dpi=(300, 300))
    return image


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    image_path = OUTPUT / "scanned-page.png"
    image = make_scan(image_path)
    selectable = canvas.Canvas(str(OUTPUT / "pdf-selectable.pdf"), pagesize=letter)
    selectable.setFont("Helvetica-Bold", 18)
    selectable.drawString(72, 730, "Selectable PDF extraction fixture")
    selectable.setFont("Helvetica", 12)
    for index, line in enumerate(LINES):
        selectable.drawString(72, 690 - index * 28, line)
    selectable.save()

    scanned = canvas.Canvas(str(OUTPUT / "pdf-scanned.pdf"), pagesize=letter)
    scanned.drawImage(ImageReader(image), 0, 0, width=612, height=816, preserveAspectRatio=False)
    scanned.save()

    mixed = canvas.Canvas(str(OUTPUT / "pdf-mixed.pdf"), pagesize=letter)
    mixed.setFont("Helvetica-Bold", 18)
    mixed.drawString(72, 730, "Mixed document first selectable page")
    mixed.setFont("Helvetica", 12)
    for index, line in enumerate(LINES):
        mixed.drawString(72, 690 - index * 28, f"MIXED TEXT: {line}")
    mixed.showPage()
    mixed.drawImage(ImageReader(image), 0, 0, width=612, height=816, preserveAspectRatio=False)
    mixed.save()

    protected = canvas.Canvas(str(OUTPUT / "pdf-password.pdf"), pagesize=letter, encrypt="YomuPass42")
    protected.setFont("Helvetica-Bold", 18)
    protected.drawString(72, 730, "Password protected PDF fixture")
    protected.setFont("Helvetica", 12)
    protected.drawString(72, 690, "This document should open only with the supplied test password.")
    protected.save()

    for path in sorted(OUTPUT.glob("*.pdf")):
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        print(f"{path.name}\t{path.stat().st_size}\t{digest}")


if __name__ == "__main__":
    main()
