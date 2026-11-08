"""Upload order and metadata of the seed bundle (docs/08 §4.9), with a fake S3 client."""

import json

from toefly_seed.common import upload


class FakeS3:
    def __init__(self):
        self.puts = []

    def put_object(self, **kwargs):
        self.puts.append(kwargs)


def bundle(tmp_path):
    (tmp_path / "demo" / "item-sets").mkdir(parents=True)
    (tmp_path / "demo" / "source.json").write_text("{}")
    (tmp_path / "demo" / "item-sets" / "a-001.json").write_text('{"x": 1}')
    (tmp_path / "manifest.json").write_text(json.dumps({"sources": [
        {"sourceCode": "demo", "files": [{"path": "demo/item-sets/a-001.json"}]}]}))
    return tmp_path


def test_files_and_sources_go_first_and_the_manifest_last(tmp_path):
    s3 = FakeS3()

    keys = upload.upload(bundle(tmp_path), s3, "toefly-seed", "seed/v1")

    assert keys == ["seed/v1/demo/source.json", "seed/v1/demo/item-sets/a-001.json", "seed/v1/manifest.json"]
    assert [p["Key"] for p in s3.puts] == keys
    assert s3.puts[1]["Metadata"]["sha256"].startswith("sha256:") and len(s3.puts[1]["Metadata"]["sha256"]) == 71


def test_a_dry_run_puts_nothing(tmp_path):
    s3 = FakeS3()

    assert len(upload.upload(bundle(tmp_path), s3, "toefly-seed", "seed/v1/", dry_run=True)) == 3
    assert s3.puts == []
