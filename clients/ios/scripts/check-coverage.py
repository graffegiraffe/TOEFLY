#!/usr/bin/env python3
"""Fails when the line coverage of a ToeflyKit target is below the threshold.

Usage: check-coverage.py <codecov json from `swift test --show-codecov-path`> <target> <percent>
The phase 12 DoD asks for view model coverage of at least 80% (docs/12-implementation-plan.md).
"""
import json
import sys


def main() -> int:
    path, target, threshold = sys.argv[1], sys.argv[2], float(sys.argv[3])
    data = json.load(open(path))
    covered = count = 0
    for entry in data["data"][0]["files"]:
        if f"/Sources/{target}/" in entry["filename"]:
            lines = entry["summary"]["lines"]
            covered += lines["covered"]
            count += lines["count"]
    if count == 0:
        print(f"No sources of {target} in {path}")
        return 1
    percent = 100 * covered / count
    print(f"{target}: {percent:.1f}% of {count} lines covered (threshold {threshold:.0f}%)")
    return 0 if percent >= threshold else 1


if __name__ == "__main__":
    sys.exit(main())
