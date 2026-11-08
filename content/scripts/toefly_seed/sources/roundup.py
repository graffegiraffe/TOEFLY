"""Pearson, New Round-Up 5: the 15 grammar units and their progress checks.

The Student's Book is a scan whose text layer is unreadable and whose exercises carry handwriting, and the
Teacher's Book key lists answers without the questions. The exercises therefore need a vision model (docs/08
sections 5.2 and 5.3) and are not extracted yet; the units are written from the clean contents page of the
Teacher's Book with ``reviewed: false``.
"""

import re
from collections.abc import Iterator
from pathlib import Path

from ..common import pdf
from ..common.envelope import Report
from ..common.ids import slugify
from ..common.text import normalise
from ..common.units import unit_envelope

SOURCE_CODE = "new-roundup-5"
PROGRESS_CHECK_AFTER = {3: 1, 6: 2, 9: 3, 12: 4, 15: 5}


def source_info() -> dict:
    return {
        "sourceCode": SOURCE_CODE,
        "title": "New Round-Up 5: English Grammar Practice",
        "publisher": "Pearson Education (Virginia Evans, Jenny Dooley)",
        "year": 2011,
        "url": "https://www.pearson.com",
        "examFormat": "LEGACY",
        "license": "THIRD_PARTY_ATTRIBUTION",
        "attributionText": "Pearson, “New Round-Up 5” (Student’s Book and Teacher’s Book), 2011",
        "extra": {},
    }


def _titles(text: str) -> dict[int, str]:
    """Unit number to title from the left column of the Teacher's Book key contents."""
    lines = text.splitlines()
    start = next(i for i, l in enumerate(lines) if re.match(r"^\s*Present Forms \.{3,}", l))
    titles: dict[int, str] = {}
    current: int | None = None
    for raw in lines[start : start + 40]:
        line = raw[:78].rstrip()
        if "Revision" in line:
            break
        line = re.sub(r"\s*\.[ .,]*\d*\s*$", "", line)
        if not line.strip() or "Progress Check" in line:
            continue
        m = re.match(r"^\s{0,12}(\d{1,2})\s{2,}(.+)$", line)
        if m:
            current = int(m.group(1))
            titles[current] = m.group(2).strip()
        elif not titles:
            current = 1
            titles[1] = line.strip()
        elif current is not None:
            titles[current] = f"{titles[current]} {line.strip()}"
    return titles


def extract_file(path: Path, report: Report) -> Iterator[dict]:
    teacher = path.with_name(path.name.replace("Student_39_s", "Teacher_39_s"))
    text = "\n".join(pdf.pages(teacher)[:8])
    titles = _titles(text)
    for number in range(1, 16):
        slug = f"u{number:02d}"
        title = titles.get(number)
        if not title:
            report.warn(slug, "title not found in the contents")
            continue
        title = re.sub(r"(\w)- ", r"\1 - ", re.sub(r"\s+", " ", re.sub(r"\s*\.{2,}.*$", "", title))).strip(" .,")
        unit = {"framework": "ROUND_UP_5", "unitNo": number, "title": normalise(title),
                "topicCodes": [slugify(part) for part in re.split(r"\s+[-–/]\s+", title)],
                "ruleCard": None, "exercises": []}
        check = next((c for after, c in PROGRESS_CHECK_AFTER.items() if after == number), None)
        if check:
            unit["progressCheckNo"] = check
        yield unit_envelope(kind="GRAMMAR_UNIT", source_code=SOURCE_CODE, slug=slug, unit=unit, locator={},
                            report=report, where=slug)
