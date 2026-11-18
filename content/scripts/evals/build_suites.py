#!/usr/bin/env python3
"""Build the eval suites of WritingGrader from the anonymised drafts and the filled scoring sheet.

Reads evals/data/drafts.jsonl and evals/data/scoring-sheet.csv (column "raterA (0-5)"; "skip" drops a case,
an empty cell leaves the case without a gold score), and optionally evals/data/tasks.json ({"<topic>": "<task text>"})
for drafts that do not contain their task, and evals/data/curation.json: per case id either {"skip": true} or
{"from": "...", "to": "..."} (keep the text between two markers, both included), "task", "taskSource" and
"gold" with "goldSource" (docs/12 phase 5b decision 2: AI_RATER when no human rater is available). Writes evals/data/writing-email.jsonl and writing-discussion.jsonl and
evals/manifest.json with ids, splits and sha256 only (docs/06-ai-agent.md §7.2, docs/12 phase 5b decision 2).
"""
import csv
import hashlib
import json
import sys
from pathlib import Path

SUITES = {"WRITE_EMAIL": "writing-email", "WRITE_ACADEMIC_DISCUSSION": "writing-discussion"}


def main(root: Path) -> int:
    data = root / "evals" / "data"
    scores = {}
    with (data / "scoring-sheet.csv").open(encoding="utf-8") as sheet:
        for row in csv.DictReader(sheet):
            scores[row["id"]] = row["raterA (0-5)"].strip()
    tasks_file = data / "tasks.json"
    tasks = json.loads(tasks_file.read_text(encoding="utf-8")) if tasks_file.exists() else {}
    curation_file = data / "curation.json"
    curation = json.loads(curation_file.read_text(encoding="utf-8")) if curation_file.exists() else {}
    suites = {name: [] for name in SUITES.values()}
    for line in (data / "drafts.jsonl").read_text(encoding="utf-8").splitlines():
        case = json.loads(line)
        cure = curation.get(case["id"], {})
        if cure.get("skip"):
            continue
        if "from" in cure:
            start = case["text"].index(cure["from"])
            end = case["text"].index(cure["to"], start) + len(cure["to"])
            case["text"] = case["text"][start:end].strip()
            case["tutorErrors"] = [e for e in case["tutorErrors"] if e["quote"] in case["text"]]
        for old, new in cure.get("replace", {}).items():
            case["text"] = case["text"].replace(old, new)
        if "task" in cure:
            case["taskPrompt"] = cure["task"]
            case["taskSource"] = cure.get("taskSource", "RECONSTRUCTED")
        score = str(cure["gold"]) if "gold" in cure else scores.get(case["id"], "")
        case["goldSource"] = cure.get("goldSource", "HUMAN") if score else None
        if score.lower() == "skip":
            continue
        if score:
            value = float(score.replace(",", "."))
            if not 0 <= value <= 5 or value * 2 != int(value * 2):
                raise SystemExit(f"{case['id']}: score must be 0-5 in steps of 0.5, got {score}")
            case["goldScores"] = {"raterA": value}
        case["taskPrompt"] = case.get("taskPrompt") or tasks.get(case["topic"])
        case.setdefault("distortionOf", None)
        case.setdefault("distortion", None)
        suites[SUITES[case["taskType"]]].append(case)
    manifest = {"version": 1, "note": "Texts stay with the author; see docs/06-ai-agent.md §7.2.", "suites": {}}
    for name, cases in suites.items():
        body = "".join(json.dumps(c, ensure_ascii=False) + "\n" for c in cases)
        (data / f"{name}.jsonl").write_text(body, encoding="utf-8")
        manifest["suites"][name] = {
            "sha256": hashlib.sha256(body.encode("utf-8")).hexdigest(),
            "cases": len(cases),
            "gold": sum(1 for c in cases if c["goldScores"]),
            "ids": [{"id": c["id"], "split": c["split"],
                     "sha256": hashlib.sha256(c["text"].encode("utf-8")).hexdigest()} for c in cases],
        }
    (root / "evals" / "manifest.json").write_text(json.dumps(manifest, indent=1) + "\n", encoding="utf-8")
    for name, entry in manifest["suites"].items():
        print(f"{name}: {entry['cases']} cases, {entry['gold']} with a gold score", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main(Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")))
