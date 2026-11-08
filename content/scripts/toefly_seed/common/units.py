"""Envelope of a GRAMMAR_UNIT / VOCABULARY_UNIT (docs/08-content-pipeline.md sections 4.7 and 7)."""

from .. import SCRIPT_VERSION
from .envelope import Report
from .hashing import content_hash
from .ids import item_set_id


def unit_envelope(*, kind: str, source_code: str, slug: str, unit: dict, locator: dict, report: Report, where: str,
                  method: str = "TEXT_LAYER", reviewed: bool = False) -> dict:
    seed_id = f"{source_code}/{slug}"
    report.sets += 1
    report.items += len(unit.get("words", []))
    report.by_type[kind] = report.by_type.get(kind, 0) + 1
    return {
        "schemaVersion": "1.0",
        "kind": kind,
        "seedId": seed_id,
        "id": item_set_id(seed_id),
        "sourceCode": source_code,
        "locator": locator,
        "extraction": {
            "scriptVersion": SCRIPT_VERSION,
            "method": method,
            "ocrCleaned": False,
            "reviewed": reviewed,
            "overrides": [],
            "warnings": [w for w in report.warnings if w.startswith(where)],
        },
        "contentHash": content_hash(unit),
        "unit": unit,
        "_file": f"{'vocabulary' if kind == 'VOCABULARY_UNIT' else 'grammar'}-units/{slug}.json",
    }
