"""Acceptance checks for the cleaned saved Galaxy extraction fixtures."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
cases = {
    "twz-mozilla": {
        "markers_removed": ["Contact the author:", "pic.twitter.com", "Progress you can see."],
        "content_kept": ["A top U.S. Air Force acquisition officer", "A rendering of the F-47", "We only have so many production lines"],
    },
    "twz-readability4j": {
        "markers_removed": ["The TWZ Newsletter", "Contact the author:", "pic.twitter.com", "Progress you can see."],
        "content_kept": ["A top U.S. Air Force acquisition officer", "A rendering of the F-47", "We only have so many production lines"],
    },
    "9to5google-mozilla": {
        "markers_removed": ["More on Wear OS:", "Follow Ben:", "FTC:", "Advertisement - scroll for more content"],
        "content_kept": ["Facer provides a huge library", "Watch Brands:", "A new \u201cFacer Originals\u201d program"],
    },
    "9to5google-readability4j": {
        "markers_removed": ["More on Wear OS:", "Follow Ben:", "FTC:", "Advertisement - scroll for more content"],
        "content_kept": ["Facer provides a huge library", "Watch Brands:", "A new \u201cFacer Originals\u201d program"],
    },
}
rows = []
for case, expectations in cases.items():
    path = ROOT / f"results/clean-{case}-clean.txt"
    if not path.exists():
        # Device export names include the original engine suffix, already in case.
        path = ROOT / f"results/clean-{case}.txt"
    text = path.read_text(encoding="utf-8-sig")
    removed = [m for m in expectations["markers_removed"] if m not in text]
    retained = [m for m in expectations["content_kept"] if m in text]
    missing = [m for m in expectations["content_kept"] if m not in text]
    assert len(removed) == len(expectations["markers_removed"]), (case, "boilerplate remains", removed)
    assert not missing, (case, "article content was lost", missing)
    rows.append({"fixture_engine": case, "characters": len(text),
                 "removed_boilerplate_checks": removed, "retained_content_checks": retained,
                 "status": "passed"})
out = ROOT / "results/extraction-cleanup-verification.json"
out.write_text(json.dumps(rows, indent=2), encoding="utf-8")
print(json.dumps(rows, indent=2))
