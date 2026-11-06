"""TST Prep, 100 TOEFL Reading Practice Questions (2020, legacy format): one passage and one question per set."""

import re
from collections.abc import Iterator
from pathlib import Path

from ..common import pdf, tst
from ..common.envelope import Report, envelope
from ..common.text import normalise, word_count

SOURCE_CODE = "tst-reading-legacy-2020"
ZW = "\u200b"
STRAY = "\ue000"  # a zero-width mark without a pair (a typo of the book)
STRAY = ""  # a zero-width mark without a pair (a typo of the book)
HEADER_LINE = re.compile(r"^\s*(?:Practice for the TOEFL|100 Practice Questions for the TOEFL|TST Prep)")
TYPE_LINE = re.compile(r"^\s*([A-Z][A-Za-z ]+?) Questions(?: \(1\))?\s*$")
FLESCH = re.compile(r"Flesch-Kincaid Grade Level:\s*([\d.]+)")
KEY_ENTRY = re.compile(r"^(.+?) - ([A-F](?:, [A-F])*)\s*$")
MARKER = re.compile(r"\[\[([A-D])\]\]")
SENT_END = re.compile(r"[.!?][\"”’)]*(?=\s|$)")

SUBSKILL = {
    "Vocabulary": "VOCABULARY",
    "Factual Information": "FACTUAL",
    "Negative Factual Information": "NEGATIVE_FACTUAL",
    "Inference": "INFERENCE",
    "Rhetorical Purpose": "RHETORICAL_PURPOSE",
    "Reference": "REFERENCE",
    "Sentence Simplification": "SENTENCE_SIMPLIFICATION",
    "Insert Text": "INSERT_TEXT",
    "Summary": "PROSE_SUMMARY",
}


def source_info() -> dict:
    return {
        "sourceCode": SOURCE_CODE,
        "title": "100 TOEFL Reading Practice Questions (2020, legacy format)",
        "publisher": "TST Prep",
        "year": 2020,
        "url": "https://tstprep.com",
        "examFormat": "LEGACY",
        "license": "THIRD_PARTY_ATTRIBUTION",
        "attributionText": "TST Prep, “100 TOEFL Reading Practice Questions”, 2020, tstprep.com; passages are public-domain excerpts listed in the book",
        "extra": {},
    }


def _prepare(page: str) -> list[str]:
    """Marks squares and highlights before the zero-width characters are dropped."""
    page = re.sub(ZW + r"\s*([A-D])\s*" + ZW, r"[[\1]]", page)
    page = re.sub(ZW + r"((?:(?!\n[ \t]*\n).){1,400}?)" + ZW, r"{{mark:\1}}", page, flags=re.S)
    return page.replace(ZW, STRAY).splitlines()


def _norm(title: str) -> str:
    text = title.lower().replace("&", "and")
    text = re.sub(r"\b(?:the|a|an)\b", "", text)
    text = re.sub(r"[^a-z]", "", text)
    return text.replace("tissues", "tissue")


def _centered(line: str) -> bool:
    return bool(line.strip()) and len(line) - len(line.lstrip()) >= 20


def _blocks(lines: list[str]) -> list[list[str]]:
    blocks, current = [], []
    for line in lines:
        if line.strip():
            current.append(line)
        elif current:
            blocks.append(current)
            current = []
    if current:
        blocks.append(current)
    return blocks


def _plain(text: str) -> str:
    return re.sub(r"\{\{mark:(.*?)\}\}", r"\1", text, flags=re.S)


def _locate(paragraphs: list[str], text: str):
    for index, paragraph in enumerate(paragraphs):
        at = paragraph.find(text)
        if at >= 0:
            return {"text": text, "paragraph": index, "charStart": at, "charEnd": at + len(text)}
    return None


def extract_file(path: Path, report: Report) -> Iterator[dict]:
    passages: list[dict] = []
    keys: dict[int, list[tuple[str, list[str], list[str]]]] = {}
    section, mode, type_name = 0, "Q", ""
    current: dict | None = None
    key_entry = None
    for page in pdf.pages(path):
        for raw in _prepare(page):
            line = raw.replace(STRAY, "")
            stripped = line.strip()
            if HEADER_LINE.match(line) or re.fullmatch(r"\d{1,3}", stripped):
                continue
            t = TYPE_LINE.match(line)
            if t and t.group(1) in SUBSKILL:
                type_name = t.group(1)
                continue
            if not type_name:
                continue
            if stripped == "Answer Key":
                mode, key_entry = "KEY", None
                section += 0
                keys.setdefault(len(keys), [])
                continue
            if mode == "KEY":
                entry = KEY_ENTRY.match(stripped)
                if _centered(line) and not entry:
                    mode = "Q"
                    current = {"title": stripped, "lines": [], "q": [], "type": type_name, "section": len(keys),
                               "flesch": None}
                    passages.append(current)
                elif entry:
                    key_entry = (entry.group(1), entry.group(2).split(", "), [])
                    keys[len(keys) - 1].append(key_entry)
                elif key_entry is not None:
                    key_entry[2].append(line)
                continue
            if _centered(line) and (current is None or current["flesch"] is not None):
                current = {"title": stripped, "lines": [], "q": [], "type": type_name, "section": len(keys),
                           "flesch": None}
                passages.append(current)
            elif current is not None:
                m = FLESCH.search(line)
                if m and current["flesch"] is None:
                    current["flesch"] = float(m.group(1))
                elif current["flesch"] is None:
                    current["lines"].append(raw)
                else:
                    current["q"].append(line)
    passages = [p for p in passages if p["flesch"] is not None]
    per_section: dict[int, int] = {}
    used: set[tuple[int, int]] = set()
    for p in passages:
        index = per_section.get(p["section"], 0)
        per_section[p["section"]] = index + 1
        entries = keys.get(p["section"], [])
        entry = None
        wanted = _norm(p["title"])
        for position, candidate in enumerate(entries):
            if (p["section"], position) not in used and _norm(candidate[0]) == wanted:
                used.add((p["section"], position))
                entry = candidate
                break
        if entry is None and index < len(entries) and (p["section"], index) not in used:
            used.add((p["section"], index))
            entry = entries[index]
        yield from _build(p, index + 1, entry, report)


def _build(p: dict, number: int, entry, report: Report) -> Iterator[dict]:
    type_name = p["type"]
    subskill = SUBSKILL[type_name]
    slug = f"{type_name.lower().replace(' ', '-')}-{number:03d}"
    where = slug
    if entry is None:
        report.warn(where, "no key entry")
        return
    key_title, letters, expl_lines = entry
    if re.sub(r"\W", "", key_title.lower())[:12] != re.sub(r"\W", "", p["title"].lower())[:12]:
        report.warn(where, f"key title “{key_title}” differs from the passage title “{p['title']}”")
    explanation = normalise(_plain(" ".join(l.strip() for l in expl_lines if l.strip())))
    blocks = _blocks(p["lines"])
    paragraphs_marked = [pdf.unwrap(b) for b in blocks]
    if subskill == "SENTENCE_SIMPLIFICATION" and not any("{{mark:" in t for t in paragraphs_marked):
        # one stray mark ends the highlighted sentence: take the sentence before it
        for index, text in enumerate(paragraphs_marked):
            if STRAY in text:
                before = text.split(STRAY)[0]
                start = max((m.end() for m in SENT_END.finditer(before[:-1])), default=0)
                paragraphs_marked[index] = (before[:start] + "{{mark:" + before[start:].strip() + "}}"
                                            + text.split(STRAY, 1)[1])
                report.warn(where, "highlighted sentence taken from a single stray mark of the book")
                break
    paragraphs_marked = [t.replace(STRAY, "") for t in paragraphs_marked]
    markers = []
    paragraphs = []
    insert_positions = {}
    if subskill == "INSERT_TEXT" and not any("[[A]]" in t for t in paragraphs_marked):
        # the zero-width pair of the first square is lost at a paragraph start: "A The ..." is the marker
        for index, text in enumerate(paragraphs_marked):
            if re.match(r"^A\s+(?=[A-Z])", text):
                paragraphs_marked[index] = re.sub(r"^A\s+", "[[A]] ", text)
                break
    for index, text in enumerate(paragraphs_marked):
        for m in MARKER.finditer(text):
            before = _plain(MARKER.sub("", text[: m.start()]))
            insert_positions[m.group(1)] = {"key": m.group(1), "paragraph": index,
                                            "afterSentence": len(SENT_END.findall(before))}
        paragraphs.append(_plain(MARKER.sub("", text)))
    marks = [normalise(re.sub(r"\s+", " ", m.group(1))) for t in paragraphs_marked for m in re.finditer(r"\{\{mark:(.*?)\}\}", t, flags=re.S)]
    q_blocks = _blocks([l.replace(STRAY, "") for l in p["q"]])
    stem_blocks, option_lines = [], []
    for b in q_blocks:
        if tst.OPTION_ANY.match(b[0]) if hasattr(tst, "OPTION_ANY") else re.match(r"^\s*[a-f]\.\s", b[0]):
            option_lines += b
        else:
            stem_blocks.append(b)
    stem = re.sub(r"^Q\.\s*", "", pdf.unwrap(stem_blocks[0])) if stem_blocks else ""
    stem = _plain(stem)
    options = []
    for line in option_lines:
        m = re.match(r"^\s*([a-f])\.\s+(.*)$", line)
        if m:
            options.append(normalise(_plain(m.group(2))))
        elif options:
            options[-1] += " " + line.strip()
    extra = [pdf.unwrap(b) for b in stem_blocks[1:]]
    keys_up = [x for x in letters]
    summary_note = explanation or tst.PLACEHOLDER

    def rationales(count: int) -> list[str]:
        out = []
        if subskill == "PROSE_SUMMARY":
            return [summary_note] * count
        correct = keys_up[0]
        _, notes = tst.split_explanation(explanation, correct)
        for key in "ABCD"[:count]:
            out.append(summary_note if key == correct else notes.get(key, summary_note))
        return out

    item: dict
    if subskill == "INSERT_TEXT":
        if len(insert_positions) != 4 or not extra:
            report.warn(where, "insert-text question without four markers or a sentence")
            return
        options = [f"Position {i}" for i in range(1, 5)]
        rs = rationales(4)
        item = {"ordinal": 1, "selection": "SINGLE", "subskill": subskill, "prompt": stem,
                "options": [{"key": k, "text": t, "rationale": r} for k, t, r in zip("ABCD", options, rs, strict=True)],
                "answerKey": keys_up[0], "explanation": {"summary": summary_note},
                "insertion": {"sentence": extra[-1], "markers": [insert_positions[k] for k in "ABCD"]}}
    elif subskill == "PROSE_SUMMARY":
        if len(options) != 6 or len(keys_up) != 3 or not extra:
            report.warn(where, f"summary question with {len(options)} options / {len(keys_up)} keys")
            return
        item = {"ordinal": 1, "selection": "MULTI", "subskill": subskill, "prompt": stem, "introSentence": extra[-1],
                "options": [{"key": k, "text": t, "rationale": summary_note} for k, t in zip("ABCDEF", options, strict=True)],
                "answerKeys": keys_up, "maxPoints": 2, "explanation": {"summary": summary_note}}
    else:
        if len(options) != 4:
            report.warn(where, f"{len(options)} options")
            return
        rs = rationales(4)
        item = {"ordinal": 1, "selection": "SINGLE", "subskill": subskill, "prompt": stem,
                "options": [{"key": k, "text": t, "rationale": r} for k, t, r in zip("ABCD", options, rs, strict=True)],
                "answerKey": keys_up[0], "explanation": {"summary": summary_note}}
        if subskill in ("VOCABULARY", "REFERENCE", "SENTENCE_SIMPLIFICATION"):
            target = marks[0] if marks else None
            if subskill in ("VOCABULARY", "REFERENCE"):
                stem_mark = re.search(r"\{\{mark:(.*?)\}\}", " ".join(p["q"]), flags=re.S)
                if stem_mark:
                    wanted = normalise(re.sub(r"\s+", " ", stem_mark.group(1)))
                    if wanted not in marks:
                        report.warn(where, f"anchor “{wanted}” located by text search (no highlight in the passage)")
                    target = wanted
            anchor = _locate(paragraphs, target) if target else None
            if not anchor:
                report.warn(where, "highlighted text not found in the passage")
                return
            item["anchor"] = anchor
    report.by_subskill[subskill] = report.by_subskill.get(subskill, 0) + 1
    payload = {"stimulus": {"title": p["title"], "domain": "GENERAL", "paragraphs": paragraphs,
                            "wordCount": word_count(" ".join(paragraphs)),
                            "metrics": {"fleschKincaidSource": p["flesch"]}},
               "items": [item]}
    yield envelope(source_code=SOURCE_CODE, slug=slug, task_type="LEGACY_READING", payload=payload, topic=p["title"],
                   cefr=None, skills=[f"reading.{subskill.lower()}"], locator={"section": type_name}, report=report,
                   where=where, domain="GENERAL")
