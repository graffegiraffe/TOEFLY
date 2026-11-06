"""Canonical JSON and sha256 (docs/08-content-pipeline.md sections 4.6 and 4.9)."""

import hashlib
import json


def canonical(value) -> str:
    """Sorted keys, no whitespace, non-ASCII kept: the same text content-service hashes."""
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def content_hash(payload) -> str:
    return "sha256:" + sha256_hex(canonical(payload).encode("utf-8"))
