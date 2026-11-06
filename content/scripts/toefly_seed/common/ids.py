"""Stable identifiers (docs/08-content-pipeline.md section 4.6). Must stay equal to content-service."""

import uuid

NAMESPACE_TOEFLY_SEED = uuid.UUID("5b1c1c4e-6a8e-5a3b-9d5f-2f0c8d1a7e11")


def item_set_id(seed_id: str) -> str:
    return str(uuid.uuid5(NAMESPACE_TOEFLY_SEED, seed_id))


def item_id(seed_id: str, ordinal: int) -> str:
    return str(uuid.uuid5(NAMESPACE_TOEFLY_SEED, f"{seed_id}#{ordinal}"))


def slugify(text: str) -> str:
    out = "".join(c.lower() if c.isalnum() else "-" for c in text)
    return "-".join(part for part in out.split("-") if part)[:60]
