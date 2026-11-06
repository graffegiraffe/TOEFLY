"""Command line of the extraction scripts: extract | validate | manifest | report (docs/08 section 4.2)."""

import argparse
import json
import sys
from pathlib import Path

from . import SCRIPT_VERSION
from .common import schema
from .common.hashing import sha256_hex
from .common.envelope import Report
from .sources import secret_toefl_html, tst_listening_2026, tst_complete_test_2026, tst_listening_legacy, tst_speaking_guide_2026, tst_reading_2026, tst_reading_legacy

ROOT = Path(__file__).resolve().parents[2]
CONTENT = ROOT
EXTRACTORS = {m.SOURCE_CODE: m for m in (secret_toefl_html, tst_listening_2026, tst_reading_2026, tst_reading_legacy, tst_listening_legacy, tst_complete_test_2026, tst_speaking_guide_2026)}
RAW = Path("/Users/katusarublevsk/Documents/English")
SOURCE_FILES = {
    "secret-toefl-society-reading": "TOEFL Reading Sets - The Secret TOEFL Society.html",
    "tst-reading-2026": "toefl-reading-100-questions-freebie-2026.pdf",
    "tst-reading-legacy-2020": "TG__TST Prep - 100 TOEFL Reading Practice Questions.pdf",
    "tst-listening-legacy-2020": "TST Prep - 100 TOEFL Listening Practice Questions.pdf",
    "tst-complete-test-1-2026": "toefl-complete-test-1-2026.pdf",
    "tst-speaking-guide-2026": "your-guide-to-the-toefl-speaking.pdf",
    "tst-listening-2026": "2026 toefl-listening-100-questions-freebie-2026.pdf",
}


def _write(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def extract(args) -> int:
    module = EXTRACTORS[args.source]
    report = Report()
    out = Path(args.out) / args.source
    for envelope in module.extract_file(Path(args.input or RAW / SOURCE_FILES[args.source]), report):
        relative = envelope.pop("_file")
        _write(out / relative, envelope)
    _write(out / "source.json", module.source_info())
    _write(
        out / "_report" / "extraction-report.json",
        {"scriptVersion": SCRIPT_VERSION, "sets": report.sets, "items": report.items, "byType": report.by_type,
         "bySubskill": report.by_subskill, "warnings": report.warnings},
    )
    print(f"{args.source}: {report.sets} sets, {report.items} items, {len(report.warnings)} warnings")
    return 0


def validate(args) -> int:
    seed = Path(args.out)
    expected = json.loads((CONTENT / "manifest" / "expected-counts.json").read_text())
    failed = 0
    seen_ids: set[str] = set()
    for source_dir in sorted(p for p in seed.iterdir() if (p / "source.json").exists()):
        code = source_dir.name
        for problem in schema.errors(json.loads((source_dir / "source.json").read_text()),
                                     schema.SCHEMAS / "source.schema.json"):
            print(f"{code}/source.json {problem}")
            failed += 1
        sets = items = 0
        for file in sorted((source_dir / "item-sets").glob("*.json")):
            envelope = json.loads(file.read_text())
            for problem in schema.validate_envelope(envelope):
                print(f"{code}/{file.name} {problem}")
                failed += 1
            for key in ("seedId", "id"):
                if envelope[key] in seen_ids:
                    print(f"{code}/{file.name} duplicate {key} {envelope[key]}")
                    failed += 1
                seen_ids.add(envelope[key])
            sets += 1
            items += len(envelope["itemSet"]["payload"]["items"])
        want = expected.get(code)
        if want and (want["sets"], want["items"]) != (sets, items):
            print(f"{code}: expected {want['sets']} sets / {want['items']} items, found {sets} / {items}")
            failed += 1
    print("OK" if not failed else f"{failed} problems")
    return 1 if failed else 0


def manifest(args) -> int:
    seed = Path(args.out)
    sources = []
    for source_dir in sorted(p for p in seed.iterdir() if (p / "source.json").exists()):
        files, counts = [], {}
        for file in sorted((source_dir / "item-sets").glob("*.json")):
            data = file.read_bytes()
            envelope = json.loads(data)
            task = envelope["itemSet"]["taskType"]
            counts[task] = counts.get(task, 0) + 1
            files.append({"path": f"{source_dir.name}/item-sets/{file.name}", "seedId": envelope["seedId"],
                          "kind": envelope["kind"], "sha256": "sha256:" + sha256_hex(data), "bytes": len(data)})
        bundle = "".join(f"{f['path']}\0{f['sha256']}\n" for f in files)
        sources.append({"sourceCode": source_dir.name, "bundleSha256": "sha256:" + sha256_hex(bundle.encode()),
                        "counts": counts, "files": files})
    document = {"manifestVersion": 1, "scriptVersion": SCRIPT_VERSION, "bucket": "toefly-seed", "prefix": "seed/v1/",
                "sources": sources}
    _write(seed / "manifest.json", document)
    # the copy in Git carries ids, hashes and counts only (docs/08 section 4.9): no text of third parties
    _write(CONTENT / "manifest" / "seed-manifest.json", document)
    print(f"manifest: {len(sources)} sources, {sum(len(s['files']) for s in sources)} files")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="toefly-seed")
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("extract")
    p.add_argument("--source", required=True, choices=sorted(EXTRACTORS))
    p.add_argument("--input")
    p.add_argument("--out", default=str(CONTENT / "seed"))
    p.set_defaults(run=extract)
    for name, run in (("validate", validate), ("manifest", manifest)):
        p = sub.add_parser(name)
        p.add_argument("--out", default=str(CONTENT / "seed"))
        p.set_defaults(run=run)
    args = parser.parse_args(argv)
    return args.run(args)


if __name__ == "__main__":
    sys.exit(main())
