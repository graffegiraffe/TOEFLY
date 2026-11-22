#!/usr/bin/env python3
"""Writes the week and section covers of docs/07-ux-ui.md §8 as vector image sets.

The source is the SVG of design/prototype/js/covers.js (the web copies the same file). The asset catalog does not
know CSS variables, so `var(--cover-stop-N, #HEX)` becomes `#HEX`; the dark theme dims the cover in the app instead
(§8.1). Run after changing a cover:  python3 clients/ios/scripts/generate-cover-assets.py
"""
import json
import pathlib
import re
import shutil

IOS = pathlib.Path(__file__).resolve().parent.parent
SOURCE = IOS.parent.parent / "design" / "prototype" / "js" / "covers.js"
COVERS = IOS / "Toefly" / "Resources" / "Assets.xcassets" / "Covers"


def main() -> None:
    text = SOURCE.read_text()
    covers = re.findall(r"'([a-z0-9-]+)':\s*`(<svg.*?</svg>)`", text, re.DOTALL)
    if len(covers) != 22:
        raise SystemExit(f"expected 22 covers in {SOURCE}, found {len(covers)}")
    shutil.rmtree(COVERS, ignore_errors=True)
    COVERS.mkdir(parents=True)
    (COVERS / "Contents.json").write_text(json.dumps(
        {"info": {"author": "xcode", "version": 1}, "properties": {"provides-namespace": True}}, indent=2) + "\n")
    for name, svg in covers:
        svg = re.sub(r"var\(--[a-z0-9-]+,\s*(#[0-9A-Fa-f]{6})\)", r"\1", svg)
        svg = re.sub(r'\s(role|aria-hidden|preserveAspectRatio)="[^"]*"', "", svg)
        imageset = COVERS / f"{name}.imageset"
        imageset.mkdir()
        (imageset / f"{name}.svg").write_text(svg + "\n")
        (imageset / "Contents.json").write_text(json.dumps({
            "images": [{"filename": f"{name}.svg", "idiom": "universal"}],
            "info": {"author": "xcode", "version": 1},
            "properties": {"preserves-vector-representation": True},
        }, indent=2) + "\n")
    print(f"wrote {len(covers)} covers to {COVERS.relative_to(IOS)}")


if __name__ == "__main__":
    main()
