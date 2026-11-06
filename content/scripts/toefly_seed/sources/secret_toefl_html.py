"""The Secret TOEFL Society "TOEFL Reading Sets" (docs/08-content-pipeline.md section 4.4, last extractor).

The data is a JSON literal ``const SETS = [...]`` in a ``<script>`` of the page; it is cut out by bracket matching and
read with ``json.loads`` (never ``eval``).
"""

import json
import re
from collections.abc import Iterator

from .. import SCRIPT_VERSION
from ..common.hashing import content_hash
from ..common.ids import item_set_id, slugify
from ..common.text import html_to_markup, normalise, plain, word_count

SOURCE_CODE = "secret-toefl-society-reading"
PLACEHOLDER = "Explanation not provided by the source."

# Reading domain of each passage; assigned by the author of the pipeline, the page does not say.
DOMAINS = {
    "The Domestication of Maize": "LIFE_SCIENCE",
    "The Second World War and American Women in the Workforce": "SOCIAL_SCIENCE",
    "Optical Astronomy": "PHYSICAL_SCIENCE",
    "Thermodynamics and Ecosystems": "LIFE_SCIENCE",
    "The Effects of Urbanization on Bird Populations": "LIFE_SCIENCE",
    "Mysterious Viking Sunstones": "PHYSICAL_SCIENCE",
    "Understanding Ecological Systems Theory": "SOCIAL_SCIENCE",
    "Cybernetic Prosthetics": "LIFE_SCIENCE",
    "Ancient Egyptian Medicine": "SOCIAL_SCIENCE",
    "Wind Ensemble Conducting": "ARTS",
    "The Shifting Art of Playwriting": "ARTS",
    "Smart Textiles": "PHYSICAL_SCIENCE",
    "The Mysteries of Opal": "PHYSICAL_SCIENCE",
    "Radio Astronomy: A New Perspective": "PHYSICAL_SCIENCE",
    "Video Evidence in U.S. Courts": "SOCIAL_SCIENCE",
    "Twin Stars": "PHYSICAL_SCIENCE",
}

_QUOTE = re.compile(r"“([^”]+)”")
_MARKER = re.compile(r'<span class="sq">■ ([A-D])</span>')
_SENTENCE_END = re.compile(r"[.!?][\"”’)]*\s")


from ..common.envelope import Report  # noqa: E402


def read_sets(html: str) -> list[dict]:
    """Cuts the ``SETS`` array out of the page by bracket matching that respects string literals."""
    start = html.index("[", html.index("const SETS"))
    depth, quote, escaped = 0, None, False
    for pos in range(start, len(html)):
        ch = html[pos]
        if quote:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == quote:
                quote = None
        elif ch in "\"'`":
            quote = ch
        elif ch == "[":
            depth += 1
        elif ch == "]":
            depth -= 1
            if depth == 0:
                return json.loads(html[start : pos + 1])
    raise ValueError("unterminated SETS literal")


def source_info() -> dict:
    return {
        "sourceCode": SOURCE_CODE,
        "title": "TOEFL Reading Sets",
        "publisher": "The Secret TOEFL Society",
        "year": None,
        "url": None,
        "examFormat": "LEGACY",
        "license": "THIRD_PARTY_ATTRIBUTION",
        "attributionText": "The Secret TOEFL Society, “TOEFL Reading Sets”",
        "extra": {},
    }


def classify(stem_plain: str, has_marker: bool) -> tuple[str | None, str | None]:
    """Returns (subskill, quoted text) by the wording of the stem (section 4.4, step 3)."""
    quoted = _QUOTE.search(stem_plain)
    text = quoted.group(1) if quoted else None
    if has_marker:
        return "INSERT_TEXT", None
    lowered = stem_plain.lower()
    if re.match(r"the (?:word|phrase) “", lowered) and "closest in meaning" in lowered:
        return "VOCABULARY", text
    if re.search(r"\b(?:refer to|refers to)\b", lowered) and text:
        return "REFERENCE", text
    if re.search(r"\bexcept\b|\bnot mentioned\b", lowered):
        return "NEGATIVE_FACTUAL", None
    if re.match(r"why does the author|.*(?:main purpose|function) of paragraph|.*relationship between", lowered):
        return "RHETORICAL_PURPOSE", None
    if re.search(r"inferred|suggests?|indicates?", lowered):
        return "INFERENCE", None
    if lowered.startswith(("according to", "what is one", "what are", "what does", "what role", "how do", "the author mentions")):
        return "FACTUAL", None
    return None, None


def _anchor(paragraphs: list[str], text: str) -> dict | None:
    for index, paragraph in enumerate(paragraphs):
        at = paragraph.find(text)
        if at < 0:
            at = paragraph.lower().find(text.lower())
        if at >= 0:
            return {"text": paragraph[at : at + len(text)], "paragraph": index, "charStart": at, "charEnd": at + len(text)}
    return None


def _rationales(question: dict, correct: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for note in question.get("others", []):
        match = re.match(r"\s*<b>([^<]*):</b>\s*(.*)", note, re.S)
        if not match:
            continue
        for letter in re.findall(r"[A-D]", match.group(1)):
            out[letter] = html_to_markup(match.group(2))
    out[correct] = html_to_markup(question["why"])
    return out


def _insertion(set_data: dict, stem_html: str, report: Report, where: str) -> tuple[dict | None, list[str]]:
    sentence = re.search(r"<em>(.*?)</em>", stem_html, re.S)
    markers, paragraphs = [], []
    for index, raw in enumerate(set_data["paras"]):
        cursor = 0
        for found in _MARKER.finditer(raw):
            before = html_to_markup(_MARKER.sub("", raw[: found.start()])) + " "
            markers.append(
                {"key": found.group(1), "paragraph": index, "afterSentence": len(_SENTENCE_END.findall(before))}
            )
            cursor = found.end()
        paragraphs.append(raw)
    if not sentence or len(markers) != 4:
        report.warnings.append(f"{where}: insert-text question without a sentence or four markers")
        return None, []
    return {"sentence": html_to_markup(sentence.group(1)), "markers": markers}, paragraphs


def extract(html: str, report: Report | None = None) -> Iterator[dict]:
    report = report or Report()
    for number, data in enumerate(read_sets(html), start=1):
        title = normalise(data["title"])
        seed_id = f"{SOURCE_CODE}/reading-set-{number:02d}"
        where = f"set {number} ({title})"
        paragraphs = [html_to_markup(_MARKER.sub("", p)) for p in data["paras"]]
        domain = DOMAINS.get(title)
        if domain is None:
            report.warnings.append(f"{where}: no domain assigned, using GENERAL")
        items, subskills = [], set()
        for position, question in enumerate(data["questions"], start=1):
            prompt_html = question["stem"]
            has_marker = "■" in prompt_html
            stem = html_to_markup(prompt_html.split("<br>")[0] if has_marker else prompt_html)
            subskill, quoted = classify(plain(stem), has_marker)
            letters = "ABCD"
            correct = question["answer"]
            notes = _rationales(question, correct)
            item = {
                "ordinal": position,
                "selection": "SINGLE",
                "prompt": "Where would the sentence best fit?" if has_marker else stem,
                "options": [
                    {"key": key, "text": html_to_markup(text), "rationale": notes.get(key, PLACEHOLDER)}
                    for key, text in zip(letters, question["options"], strict=True)
                ],
                "answerKey": correct,
                "explanation": {"summary": html_to_markup(question["why"])},
            }
            missing = [o["key"] for o in item["options"] if o["rationale"] == PLACEHOLDER and o["key"] != correct]
            if missing:
                report.warnings.append(f"{where} Q{question['n']}: no explanation for {','.join(missing)}")
            if subskill == "INSERT_TEXT":
                insertion, _ = _insertion(data, prompt_html, report, f"{where} Q{question['n']}")
                if insertion:
                    item["insertion"] = insertion
                else:
                    subskill = None
            elif subskill in ("VOCABULARY", "REFERENCE"):
                anchor = _anchor(paragraphs, quoted) if quoted else None
                if anchor:
                    item["anchor"] = anchor
                else:
                    report.warnings.append(f"{where} Q{question['n']}: anchor {quoted!r} not found in the passage")
                    subskill = None
            if subskill:
                item["subskill"] = subskill
                subskills.add(subskill)
                report.by_subskill[subskill] = report.by_subskill.get(subskill, 0) + 1
            else:
                report.warnings.append(f"{where} Q{question['n']}: subskill not recognised")
            items.append(item)
        payload = {
            "stimulus": {
                "title": title,
                "domain": domain or "GENERAL",
                "paragraphs": paragraphs,
                "wordCount": word_count(" ".join(plain(p) for p in paragraphs)),
            },
            "items": items,
        }
        report.sets += 1
        report.items += len(items)
        yield {
            "schemaVersion": "1.0",
            "kind": "ITEM_SET",
            "seedId": seed_id,
            "id": item_set_id(seed_id),
            "sourceCode": SOURCE_CODE,
            "locator": {"set": number, "questionNumbers": [q["n"] for q in data["questions"]]},
            "extraction": {
                "scriptVersion": SCRIPT_VERSION,
                "method": "HTML_DATA",
                "ocrCleaned": False,
                "reviewed": True,
                "overrides": [],
                "warnings": [w for w in report.warnings if w.startswith(where)],
            },
            "contentHash": content_hash(payload),
            "itemSet": {
                "taskType": "LEGACY_READING",
                "examFormat": "LEGACY",
                "section": "READING",
                "provenance": "EXTRACTED",
                "cefr": None,
                "topic": title,
                "domain": domain or "GENERAL",
                "skills": sorted(f"reading.{s.lower()}" for s in subskills) or ["reading.detail"],
                "payload": payload,
            },
            "_file": f"item-sets/reading-set-{number:02d}-{slugify(title)}.json",
        }


def extract_file(path, report: Report) -> Iterator[dict]:
    from pathlib import Path

    return extract(Path(path).read_text(encoding="utf-8"), report)
