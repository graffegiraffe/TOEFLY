"""Seed envelope assembly shared by all extractors (docs/08-content-pipeline.md section 4.7)."""

from dataclasses import dataclass, field

from .. import SCRIPT_VERSION
from .hashing import content_hash
from .ids import item_set_id

SECTIONS = {
    "COMPLETE_THE_WORDS": "READING", "READ_DAILY_LIFE": "READING", "READ_ACADEMIC_PASSAGE": "READING",
    "LISTEN_CHOOSE_RESPONSE": "LISTENING", "LISTEN_CONVERSATION": "LISTENING", "LISTEN_ANNOUNCEMENT": "LISTENING",
    "LISTEN_ACADEMIC_TALK": "LISTENING", "BUILD_A_SENTENCE": "WRITING", "WRITE_EMAIL": "WRITING",
    "WRITE_ACADEMIC_DISCUSSION": "WRITING", "LISTEN_AND_REPEAT": "SPEAKING", "TAKE_AN_INTERVIEW": "SPEAKING",
    "GRAMMAR_EXERCISE": None, "VOCAB_EXERCISE": None, "LEGACY_READING": "READING", "LEGACY_LISTENING": "LISTENING",
}


@dataclass
class Report:
    sets: int = 0
    items: int = 0
    by_type: dict[str, int] = field(default_factory=dict)
    by_subskill: dict[str, int] = field(default_factory=dict)
    warnings: list[str] = field(default_factory=list)

    def warn(self, where: str, message: str) -> None:
        self.warnings.append(f"{where}: {message}")


def envelope(*, source_code: str, slug: str, task_type: str, payload: dict, topic: str, cefr: str | None,
             skills: list[str], locator: dict, report: Report, where: str, method: str = "TEXT_LAYER",
             domain: str | None = None) -> dict:
    seed_id = f"{source_code}/{slug}"
    report.sets += 1
    report.items += len(payload["items"])
    report.by_type[task_type] = report.by_type.get(task_type, 0) + 1
    return {
        "schemaVersion": "1.0",
        "kind": "ITEM_SET",
        "seedId": seed_id,
        "id": item_set_id(seed_id),
        "sourceCode": source_code,
        "locator": locator,
        "extraction": {
            "scriptVersion": SCRIPT_VERSION,
            "method": method,
            "ocrCleaned": False,
            "reviewed": method != "OCR_LLM",
            "overrides": [],
            "warnings": [w for w in report.warnings if w.startswith(where)],
        },
        "contentHash": content_hash(payload),
        "itemSet": {
            "taskType": task_type,
            "examFormat": "LEGACY" if task_type.startswith("LEGACY_") else "TOEFL_2026",
            "section": SECTIONS[task_type],
            "provenance": "EXTRACTED",
            "cefr": cefr,
            "topic": topic[:120],
            "domain": domain,
            "skills": skills,
            "payload": payload,
        },
        "_file": f"item-sets/{slug}.json",
    }
