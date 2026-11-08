"""Upload of the seed bundle to the private bucket ``toefly-seed`` (docs/08-content-pipeline.md §4.9).

Every file the manifest lists and every ``source.json`` is put first, with its sha256 as object metadata; the
manifest goes last, so content-service never sees a half-uploaded bundle. Old object versions stay in the bucket and
a rollback is the upload of the previous manifest.
"""

import json
from pathlib import Path

from .hashing import sha256_hex


def plan(seed: Path) -> list[str]:
    """Paths relative to the seed directory in upload order: listed files and sources, then ``manifest.json``."""
    manifest = json.loads((seed / "manifest.json").read_text(encoding="utf-8"))
    paths: list[str] = []
    for source in manifest["sources"]:
        paths.append(f"{source['sourceCode']}/source.json")
        paths += [f["path"] for f in source["files"]]
    return paths + ["manifest.json"]


def upload(seed: Path, client, bucket: str, prefix: str, *, dry_run: bool = False) -> list[str]:
    """Puts the bundle under ``prefix``; ``client`` is a boto3 S3 client (or anything with ``put_object``)."""
    prefix = prefix if not prefix or prefix.endswith("/") else prefix + "/"
    done = []
    for path in plan(seed):
        data = (seed / path).read_bytes()
        key = prefix + path
        if not dry_run:
            client.put_object(Bucket=bucket, Key=key, Body=data, ContentType="application/json",
                              Metadata={"sha256": "sha256:" + sha256_hex(data)})
        done.append(key)
    return done
