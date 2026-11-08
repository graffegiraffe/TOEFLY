"""Collins, Vocabulary and Grammar for the TOEFL iBT (2013): vocabulary charts and the list of grammar units.

The book is a noisy scan. The word charts have a stable three-column layout and are read by the coordinates of the
words; the exercises and their multi-column keys need a vision model and are not extracted yet (units carry
``exercises: []``). Every unit is written with ``reviewed: false`` so that content-service imports it as IN_REVIEW.
"""

import re
from collections.abc import Iterator
from pathlib import Path

import pdfplumber

from ..common.envelope import Report
from ..common.ids import slugify
from ..common.text import normalise
from ..common.units import unit_envelope

SOURCE_CODE = "collins-vocab-grammar-2013"
THEMES = {"Arts": "ARTS", "Life Science": "LIFE_SCIENCE", "Social Science": "SOCIAL_SCIENCE",
          "Physical Science": "PHYSICAL_SCIENCE", "General Topics": "GENERAL"}
_ENTRY = re.compile(r"^(?P<word>[A-Za-z][A-Za-z' -]*?)\s*\((?P<pos>[^)]+)\)?$")
_UNIT = re.compile(r"^\s*Unit (\d{1,2})\s+(.+?)\s{2,}(\d{1,3})\s*$")


def source_info() -> dict:
    return {
        "sourceCode": SOURCE_CODE,
        "title": "Collins English for Exams: Vocabulary and Grammar for the TOEFL iBT Test",
        "publisher": "HarperCollins Publishers (Ingrid Wisniewska)",
        "year": 2013,
        "url": "https://www.collinselt.com",
        "examFormat": "LEGACY",
        "license": "THIRD_PARTY_ATTRIBUTION",
        "attributionText": "Collins English for Exams, “Vocabulary and Grammar for the TOEFL iBT Test”, 2013, HarperCollins",
        "extra": {},
    }


def _contents(pdf) -> tuple[list[tuple[str, int, str, int]], list[tuple[int, str, int]]]:
    """Vocabulary units (theme, no, title, page) and grammar units (no, title, page) from the contents pages."""
    text = "\n".join((pdf.pages[i].extract_text(layout=True) or "") for i in range(1, 6))
    theme, section = "", ""
    vocab, grammar = [], []
    for line in text.splitlines():
        s = line.strip()
        if s == "Vocabulary":
            section = "vocab"
        elif s.startswith("Grammar") and "Key grammar" not in s:
            section = "grammar"
        elif s in THEMES:
            theme = s
        m = _UNIT.match(line.replace(" ", " "))
        if m and section == "vocab":
            vocab.append((theme, int(m.group(1)), normalise(m.group(2)), int(m.group(3))))
        elif m and section == "grammar":
            grammar.append((int(m.group(1)), normalise(m.group(2)), int(m.group(3))))
    return vocab, grammar


def _lines(words: list[dict], tol: float = 4.0) -> list[list[dict]]:
    out: list[list[dict]] = []
    for w in sorted(words, key=lambda w: (w["top"], w["x0"])):
        if out and abs(out[-1][0]["top"] - w["top"]) <= tol:
            out[-1].append(w)
        else:
            out.append([w])
    return [sorted(line, key=lambda w: w["x0"]) for line in out]


def read_chart(page) -> list[dict]:
    """Word, part of speech, definition and example from the three columns of a chart page."""
    words = page.extract_words()
    head = {w["text"].split("(")[0]: w for w in words if w["text"].startswith(("Word", "Definition", "Example"))}
    if not {"Word", "Definition", "Example"} <= head.keys():
        return []
    definition_x, example_x = head["Definition"]["x0"] - 8, head["Example"]["x0"] - 8
    top = head["Word"]["top"] + 8
    body = [w for w in words if w["top"] > top and w["top"] < page.height - 45]
    word_col = [w for w in body if w["x0"] < definition_x]
    def_col = [w for w in body if definition_x <= w["x0"] < example_x]
    ex_col = [w for w in body if w["x0"] >= example_x]

    entries = []
    for line in _lines(word_col):
        text = " ".join(w["text"] for w in line)
        m = _ENTRY.match(text.replace("(cid:9)", "").strip())
        if m:
            entries.append({"word": m.group("word").strip(), "pos": m.group("pos").strip().rstrip(")"),
                            "top": line[0]["top"]})
        elif entries and text.strip():  # a wrapped headword continues the previous entry
            entries[-1]["word"] = f"{entries[-1]['word']} {text.strip()}"
    for i, entry in enumerate(entries):
        end = entries[i + 1]["top"] - 3 if i + 1 < len(entries) else page.height
        parts = [w for w in def_col if entry["top"] - 3 <= w["top"] < end]
        entry["definition"] = normalise(" ".join(" ".join(w["text"] for w in line) for line in _lines(parts)))

    # examples are sentences that float next to several entries: cluster lines, then give a sentence to every
    # entry whose headword occurs in it
    sentences: list[str] = []
    cluster: list[dict] = []
    last_top = None
    for line in _lines(ex_col):
        top_line = line[0]["top"]
        if last_top is not None and top_line - last_top > 16:
            sentences.append(" ".join(" ".join(w["text"] for w in l) for l in _lines(cluster)))
            cluster = []
        cluster += line
        last_top = top_line
    if cluster:
        sentences.append(" ".join(" ".join(w["text"] for w in l) for l in _lines(cluster)))
    for entry in entries:
        stem = entry["word"].lower().split()[0][:5]
        match = next((normalise(s) for s in sentences if stem in s.lower()), None)
        entry["example"] = match
    return [{k: v for k, v in e.items() if k != "top" and v is not None} for e in entries]


def extract_file(path: Path, report: Report) -> Iterator[dict]:
    with pdfplumber.open(str(path)) as pdf:
        vocab, grammar = _contents(pdf)
        for theme, number, title, page_no in vocab:
            slug = f"vocab-u{number:02d}"
            words: list[dict] = []
            for offset in (0, 1):
                words = read_chart(pdf.pages[page_no + offset])
                if words:
                    break
            for w in [w for w in words if not w.get("definition")]:
                report.warn(slug, f"“{w['word']}” has no definition in the chart and is left out")
            words = [w for w in words if w.get("definition")]
            if not words:
                report.warn(slug, "no Word/Definition/Example chart on the opening pages (a functional-language "
                                  "unit); not extracted")
                continue
            if not 10 <= len(words) <= 40:
                report.warn(slug, f"{len(words)} words read from the chart (expected 10-40)")
            unit = {"unitNo": number, "theme": THEMES.get(theme, "GENERAL"), "title": title, "words": words,
                    "exercises": []}
            yield unit_envelope(kind="VOCABULARY_UNIT", source_code=SOURCE_CODE, slug=slug, unit=unit,
                                locator={"pages": [page_no, page_no + 3]}, report=report, where=slug)
        for number, title, page_no in grammar:
            slug = f"grammar-u{number:02d}"
            unit = {"framework": "COLLINS_GRAMMAR", "unitNo": number, "title": title,
                    "topicCodes": [slugify(title)], "ruleCard": None, "exercises": []}
            yield unit_envelope(kind="GRAMMAR_UNIT", source_code=SOURCE_CODE, slug=slug, unit=unit,
                                locator={"pages": [page_no, page_no + 3]}, report=report, where=slug)
