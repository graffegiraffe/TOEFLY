"""JSON Schema validation with readable messages (docs/08-content-pipeline.md section 4.8)."""

import json
from pathlib import Path

from jsonschema import Draft202012Validator

SCHEMAS = Path(__file__).resolve().parents[3] / "schemas"


def _load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def errors(instance, schema_path: Path) -> list[str]:
    validator = Draft202012Validator(_load(schema_path))
    return [
        f"{'/' + '/'.join(str(p) for p in e.absolute_path)}: {e.message}"
        for e in sorted(validator.iter_errors(instance), key=lambda e: list(e.absolute_path))
    ]


def validate_envelope(envelope: dict) -> list[str]:
    problems = errors(envelope, SCHEMAS / "seed-envelope.schema.json")
    task_type = envelope.get("itemSet", {}).get("taskType")
    payload_schema = SCHEMAS / "payload" / f"{task_type}.schema.json"
    if task_type and payload_schema.exists():
        problems += [f"payload{p}" for p in errors(envelope["itemSet"]["payload"], payload_schema)]
    elif task_type:
        problems.append(f"no payload schema for {task_type}")
    return problems
