"""Pearson, New Round-Up 5: the 15 grammar units and their progress checks.

The Student's Book is a scan whose text layer is unreadable and whose exercises carry handwriting, and the
Teacher's Book key lists answers without the questions. Exercises, quizzes, tests and progress checks are therefore
read by a vision model (``roundup_exercises``, docs/08 sections 5.2 and 5.3); the units come from the clean contents
page of the Teacher's Book. Everything is written with ``reviewed: false``.
"""

import re
from collections.abc import Iterator
from pathlib import Path

from ..common import pdf
from ..common.envelope import Report
from ..common.ids import slugify
from ..common.text import normalise
from ..common.units import unit_envelope
from . import roundup_exercises as rx

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


def _keyed(keys: dict, kind: str, number: int) -> dict[str, dict[int, str]]:
    return {ex: answers for (k, n, ex), answers in keys.items() if k == kind and n == number}


def _unit_sets(student: Path, teacher: Path, keys: dict, number: int, title: str, report: Report,
               units: list[int] | None) -> tuple[list[dict], str]:
    """Exercises of the unit, its quiz and, after units 3, 6, 9, 12 and 15, the progress check and the test."""
    if units is not None and number not in units:
        return [], ""
    slug = f"u{number:02d}"
    ref = {"framework": "ROUND_UP_5", "unitNo": number, "topicTitle": title}
    common = {"report": report, "source_code": SOURCE_CODE}
    first, last = rx.SB_UNIT_PAGES[number]
    exercises, rules = rx.read_pages(student, first, last, rx.topics_for([number]))
    sets = rx.build_sets(exercises=exercises, keys=_keyed(keys, "UNIT", number), topics=rx.topics_for([number]),
                         unit_ref=ref, slug_base=slug, topic_title=title, role="EXERCISE",
                         locator={"pages": [first, last], "book": "STUDENTS_BOOK"}, **common)
    first, last = rx.TB_QUIZ_PAGES[number]
    quiz, _ = rx.read_pages(teacher, first, last, rx.topics_for([number]))
    sets += rx.build_sets(exercises=quiz, keys=_keyed(keys, "QUIZ", number), topics=rx.topics_for([number]),
                          unit_ref=ref, slug_base=f"quiz-{number:02d}", topic_title=f"Quiz {number}: {title}",
                          role="QUIZ", locator={"pages": [first, last], "book": "TEACHERS_BOOK"}, **common)
    check = PROGRESS_CHECK_AFTER.get(number)
    if check:
        covered = list(range(number - 2, number + 1))
        first, last = rx.SB_PROGRESS_CHECK_PAGES[check]
        pc, _ = rx.read_pages(student, first, last, rx.topics_for(covered))
        sets += rx.build_sets(
            exercises=pc, keys=_keyed(keys, "PROGRESS_CHECK", check), topics=rx.topics_for(covered),
            unit_ref={"framework": "ROUND_UP_5", "topicTitle": f"Progress Check {check} (Units {covered[0]}-{number})"},
            slug_base=f"pc-{check:02d}", topic_title=f"Progress Check {check}", role="PROGRESS_CHECK",
            locator={"pages": [first, last], "book": "STUDENTS_BOOK"}, **common)
        covered = list(range(1, number + 1))
        first, last = rx.TB_TEST_PAGES[check]
        test, _ = rx.read_pages(teacher, first, last, rx.topics_for(covered))
        sets += rx.build_sets(
            exercises=test, keys=_keyed(keys, "TEST", check), topics=rx.topics_for(covered),
            unit_ref={"framework": "ROUND_UP_5", "topicTitle": f"Test {check} (Units 1-{number})"},
            slug_base=f"test-{check:02d}", topic_title=f"Test {check}", role="TEST_PRACTICE",
            locator={"pages": [first, last], "book": "TEACHERS_BOOK"}, **common)
    return sets, rules


def extract_file(path: Path, report: Report, *, with_exercises: bool = True,
                 units: list[int] | None = None) -> Iterator[dict]:
    """``units`` limits the vision reading to some units (a trial run); the others keep no exercises."""
    teacher = path.with_name(path.name.replace("Student_39_s", "Teacher_39_s"))
    text = "\n".join(pdf.pages(teacher)[:8])
    titles = _titles(text)
    keys: dict = {}
    if with_exercises:
        for first, last in (rx.TB_UNIT_KEY_PAGES, rx.TB_TEST_KEY_PAGES, rx.TB_QUIZ_KEY_PAGES):
            keys.update(rx.read_keys(teacher, first, last, titles))
    for number in range(1, 16):
        slug = f"u{number:02d}"
        title = titles.get(number)
        if not title:
            report.warn(slug, "title not found in the contents")
            continue
        title = re.sub(r"(\w)- ", r"\1 - ", re.sub(r"\s+", " ", re.sub(r"\s*\.{2,}.*$", "", title))).strip(" .,")
        sets, rules = _unit_sets(path, teacher, keys, number, normalise(title), report,
                                 units) if with_exercises else ([], "")
        parts = []
        for ordinal, item_set in enumerate(sets, 1):
            parts.append({"role": item_set.pop("_role"), "itemSetSeedId": item_set["seedId"], "ordinal": ordinal})
            yield item_set
        card = rx.rule_card(normalise(title), rules, report, slug) if rules else None
        unit = {"framework": "ROUND_UP_5", "unitNo": number, "title": normalise(title),
                "topicCodes": [slugify(part) for part in re.split(r"\s+[-–/]\s+", title)],
                "ruleCard": card, "exercises": parts}
        check = next((c for after, c in PROGRESS_CHECK_AFTER.items() if after == number), None)
        if check:
            unit["progressCheckNo"] = check
        yield unit_envelope(kind="GRAMMAR_UNIT", source_code=SOURCE_CODE, slug=slug, unit=unit, locator={},
                            report=report, where=slug)
