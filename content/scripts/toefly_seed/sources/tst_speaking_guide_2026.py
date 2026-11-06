"""TST Prep, Your Guide to the TOEFL Speaking (2026): Listen and Repeat scenarios and one Take an Interview set."""

import re
from collections.abc import Iterator
from pathlib import Path

from ..common import pdf
from ..common.envelope import Report, envelope
from ..common.text import normalise, word_count
from . import tst_complete_test_2026 as complete

SOURCE_CODE = "tst-speaking-guide-2026"
HEADER = ("TST Prep", "Your Guide to the TOEFL Speaking")


def source_info() -> dict:
    return {
        "sourceCode": SOURCE_CODE,
        "title": "Your Guide to the TOEFL Speaking (2026)",
        "publisher": "TST Prep",
        "year": 2026,
        "url": "https://tstprep.com",
        "examFormat": "TOEFL_2026",
        "license": "THIRD_PARTY_ATTRIBUTION",
        "attributionText": "TST Prep, “Your Guide to the TOEFL Speaking”, 2026, tstprep.com",
        "extra": {},
    }


def _repeat_set(slug: str, scenario: str, phrases: list[str], report: Report) -> Iterator[dict]:
    if len(phrases) != 7:
        report.warn(slug, f"{len(phrases)} phrases instead of 7")
        return
    items = [{"ordinal": n, "text": normalise(t), "wordCount": word_count(t), "audio": complete._estimated_audio(t)}
             for n, t in enumerate(phrases, start=1)]
    yield envelope(source_code=SOURCE_CODE, slug=slug, task_type="LISTEN_AND_REPEAT",
                   payload={"stimulus": {"scenario": scenario}, "items": items}, topic=scenario, cefr="B2",
                   skills=["speaking.listen_and_repeat"], locator={}, report=report, where=slug)


def extract_file(path: Path, report: Report) -> Iterator[dict]:
    lines = pdf.clean(pdf.pages(path), HEADER).splitlines()
    text = "\n".join(lines)
    key_at = text.index("Answer Key\nListen and Repeat #1") if "Answer Key\nListen and Repeat #1" in text else text.rindex("Answer Key")
    body, key = text[:key_at], text[key_at:]
    # scenarios of the three Listen and Repeat examples
    scenarios = [normalise(m.group(1).replace("\n", " ")) for m in
                 re.finditer(r"^\s*(You are (?:welcoming|explaining|being trained)[^\n]*(?:\n[^\n0-9]*?Repeat only once\.)?)",
                             body, flags=re.M)]
    kitchen = []
    for m in re.finditer(r'^\s*(\d)\. Listen and repeat only once\.\s+"([^"]*)"?', body, flags=re.M | re.S):
        kitchen.append(normalise(m.group(2).replace("\n", " ")))
    # the first example prints its seven phrases in the body (7 appears twice: with the image note)
    unique = []
    for phrase in kitchen:
        if phrase not in unique:
            unique.append(phrase)
    if scenarios:
        yield from _repeat_set("listen-and-repeat-001", scenarios[0], unique[:7], report)
    blocks = re.split(r"^Listen and Repeat #(\d)\s*$", key, flags=re.M)
    for number, block in zip(blocks[1::2], blocks[2::2], strict=False):
        phrases = re.findall(r"^\d\. (.+)$", block.split("Take an Interview")[0], flags=re.M)
        scenario = scenarios[int(number)] if int(number) < len(scenarios) else "Listen to the speaker and repeat."
        yield from _repeat_set(f"listen-and-repeat-{int(number) + 1:03d}", scenario, phrases[:7], report)
    interview_scenario = re.search(r"(You have agreed to participate[^\n]*\n[^\n]*)", body)
    interview = key[key.index("Take an Interview #1") :]
    parts = re.split(r"^(\d)\. ", interview, flags=re.M)[1:]
    types = ["PERSONAL_EXPERIENCE", "PREFERENCE", "FUTURE_OPINION", "ABSTRACT_OPINION"]
    questions = []
    for n, chunk in zip(parts[0::2], parts[1::2], strict=False):
        question, _, answer = chunk.partition("Sample Answer")
        questions.append({"ordinal": int(n), "questionType": types[int(n) - 1],
                          "text": normalise(question.replace("\n", " ")), "responseSeconds": 45,
                          "modelAnswer": {"text": normalise(answer.replace("\n", " ")), "source": "BOOK"},
                          "audio": complete._estimated_audio(question)})
    if len(questions) == 4 and interview_scenario:
        scenario = normalise(interview_scenario.group(1).replace("\n", " "))
        yield envelope(source_code=SOURCE_CODE, slug="take-an-interview-001", task_type="TAKE_AN_INTERVIEW",
                       payload={"stimulus": {"scenario": scenario}, "items": questions}, topic=scenario, cefr="B2",
                       skills=["speaking.interview"], locator={}, report=report, where="take-an-interview-001")
    else:
        report.warn("take-an-interview-001", f"{len(questions)} questions or no scenario")
