"""Collins exercises (A, B, C, ...) and their keys, read from page images by a vision model (docs/08 §5.2, §7.2).

Only single-blank items are kept; every extracted set is ``OCR_LLM`` with ``reviewed: false`` and therefore waits for
an administrator. Nothing is invented: an item without a key is skipped, and the explanation of every item and the
rationale of every option is the fixed placeholder of docs/08 §4.5, completed at runtime by ``MistakeExplainer``.
"""

import re
import subprocess
import tempfile
from pathlib import Path

from ..common import llm
from ..common.envelope import Report, envelope
from ..common.text import normalise

PLACEHOLDER = "Explanation not provided by the source."

PAGE_PROMPT = """You are transcribing one page of a TOEFL vocabulary/grammar workbook for a database.
Return ONLY JSON: {"exercises":[{"letter":"A","instruction":"...","wordBank":["..."]|null,
"items":[{"n":1,"text":"sentence with ___ where the blank is","options":["a-text","b-text"]|null,"blanks":1}]}]}.
Rules: include every lettered exercise of the Practice Exercises (A, B, C, ...) but NOT Test Practice or Extra
Practice and no reading passages. Copy the text exactly, do not correct it. Replace each blank line by ___.
options are the answer choices a, b, c(, d) in order without their letters, or null when there are none.
blanks is the number of blanks in the item. Skip speaking and writing tasks. If the page has no such exercise return
{"exercises":[]}."""

KEY_PROMPT = """This is a page of the Answer Key of a TOEFL workbook. Return ONLY JSON:
{"part":"VOCABULARY"|"GRAMMAR"|null,"units":[{"unit":8,"exercises":{"A":[{"n":1,"answer":"On"}]}}]}.
Use the headings: "UNIT n" starts a unit, "Practice Exercises" letters (A, B, C) start an exercise. For every numbered
answer give n and the answer text exactly as printed (for several blanks of one item join them with " | "). Ignore Test
Practice, Extra Practice, Writing and Speaking answers. "part" is VOCABULARY or GRAMMAR when the page says which
part of the book the key belongs to, otherwise null."""

# docs/03-domain-model.md §12.3: one topic code per Collins grammar unit
GRAMMAR_TOPICS = {
    1: "VERB_FORMS", 2: "ARTICLES", 3: "SUBJECT_VERB_AGREEMENT", 4: "COMPARATIVES", 5: "MODALS", 6: "CONDITIONALS",
    7: "RECOMMENDATIONS", 8: "REPORTED_STATEMENTS", 9: "REPORTED_QUESTIONS_COMMANDS", 10: "GERUNDS_INFINITIVES",
    11: "REFERENCE_WORDS", 12: "PASSIVE", 13: "CONJUNCTIONS", 14: "RELATIVE_CLAUSES", 15: "OFFICE_HOURS_LANGUAGE",
    16: "CAUSATIVES", 17: "INTRODUCTORY_THERE_IT", 18: "PARAPHRASING", 19: "NOMINALIZATION", 20: "COMMON_ERRORS",
}


def grammar_topic(unit_no: int) -> str:
    return f"CG{unit_no:02d}.{GRAMMAR_TOPICS[unit_no]}"


def render(pdf: Path, page_index: int, dpi: int = 110) -> bytes:
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["pdftoppm", "-r", str(dpi), "-f", str(page_index + 1), "-l", str(page_index + 1), "-png",
                        str(pdf), f"{tmp}/p"], capture_output=True, check=True)
        return next(Path(tmp).glob("p*.png")).read_bytes()


def read_keys(pdf: Path, first_page: int, last_page: int) -> dict[tuple[str, int, str], dict[int, str]]:
    """(part, unit, letter) -> {item number: answer} merged over the key pages; the part carries over pages."""
    keys: dict[tuple[str, int, str], dict[int, str]] = {}
    part = "VOCABULARY"
    for page in range(first_page, last_page + 1):
        data = llm.ask_json(KEY_PROMPT, render(pdf, page))
        part = data.get("part") or part
        for unit in data.get("units", []):
            try:
                number = int(unit["unit"])
            except (KeyError, TypeError, ValueError):
                continue
            for letter, answers in (unit.get("exercises") or {}).items():
                target = keys.setdefault((part, number, letter.upper()), {})
                for a in answers if isinstance(answers, list) else []:
                    if not isinstance(a, dict) or "n" not in a:
                        continue
                    value = a.get("answer", a.get("a", a.get("text")))
                    if value is None:
                        continue
                    try:
                        target[int(a["n"])] = str(value)
                    except (TypeError, ValueError):
                        continue
    return keys


def read_exercises(pdf: Path, page_indexes: list[int]) -> list[dict]:
    merged: dict[str, dict] = {}
    for page in page_indexes:
        data = llm.ask_json(PAGE_PROMPT, render(pdf, page))
        for ex in data.get("exercises", []):
            letter = str(ex.get("letter", "")).upper()
            if not letter:
                continue
            if letter in merged:  # an exercise continued on the next page
                merged[letter]["items"] += ex.get("items", [])
            else:
                merged[letter] = {**ex, "items": list(ex.get("items", []))}
    return [merged[k] for k in sorted(merged)]


def _options(options) -> list[dict] | None:
    if not options or not isinstance(options, list):
        return None
    texts = [normalise(str(t)) for t in options if str(t).strip()]
    return [{"key": "ABCD"[i], "text": t, "rationale": PLACEHOLDER} for i, t in enumerate(texts[:4])]


def _choice(options: list[dict], answer: str) -> dict | None:
    """The option the key names, by its letter (a-d) or by its text; None when the key matches no option."""
    a = answer.strip().rstrip(".")
    if re.fullmatch(r"[a-dA-D]", a):
        index = "abcd".index(a.lower())
        return options[index] if index < len(options) else None
    return next((o for o in options if o["text"].lower() == a.lower()), None)


def _item(raw: dict, answer: str, *, task_type: str, topic: str | None) -> dict | None:
    sentence = normalise(str(raw["text"]))
    explanation = {"summary": PLACEHOLDER}
    options = _options(raw.get("options"))
    if options:
        chosen = _choice(options, answer)
        if chosen is None:
            return None
        if task_type == "GRAMMAR_EXERCISE" and 3 <= len(options) <= 4:
            return {"exerciseType": "MULTIPLE_CHOICE", "sentence": sentence, "options": options,
                    "answerKey": chosen["key"], "grammarTopic": topic, "explanation": explanation}
        # two choices (or a vocabulary choice): an input gap that shows the printed choices
        choices = " / ".join(o["text"] for o in options)
        answer = chosen["text"]
        if task_type == "GRAMMAR_EXERCISE":
            return {"exerciseType": "GAP_FILL", "sentence": sentence, "hint": choices, "answerKey": answer,
                    "acceptedAnswers": [], "grammarTopic": topic, "explanation": explanation}
        sentence = f"{sentence} ({choices})"
    item = {"exerciseType": "GAP_FILL", "sentence": sentence, "answerKey": normalise(answer), "acceptedAnswers": [],
            "explanation": explanation}
    if task_type == "GRAMMAR_EXERCISE":
        item["grammarTopic"] = topic
    return item


def build_sets(*, source_code: str, task_type: str, unit_ref: dict, slug_base: str, topic: str,
               exercises: list[dict], keys: dict[str, dict[int, str]], report: Report,
               locator: dict) -> list[dict]:
    """One ItemSet per lettered exercise; ``keys`` maps the exercise letter to {item number: answer}."""
    grammar = grammar_topic(unit_ref["unitNo"]) if task_type == "GRAMMAR_EXERCISE" else None
    out = []
    for ex in exercises:
        letter = str(ex.get("letter", "")).upper()
        answers = keys.get(letter, {})
        slug = f"{slug_base}-ex-{letter.lower()}"
        raw_items = ex.get("items", [])
        items = []
        for raw in raw_items:
            if int(raw.get("blanks", 1) or 1) != 1 or str(raw.get("text", "")).count("___") != 1:
                continue
            try:
                n = int(raw["n"])
            except (KeyError, TypeError, ValueError):
                continue
            answer = answers.get(n)
            if not answer or "|" in answer or "vary" in answer.lower():
                continue
            item = _item(raw, answer, task_type=task_type, topic=grammar)
            if item is None:
                report.warn(slug, f"exercise {letter} item {n}: the key “{answer}” names none of the options")
                continue
            items.append({"ordinal": len(items) + 1, **item})
        if not answers:
            report.warn(slug, f"exercise {letter}: no key found in the Answer key; not extracted")
            continue
        if not items:
            report.warn(slug, f"exercise {letter}: no single-blank items with a key; not extracted")
            continue
        if len(items) < len(raw_items):
            report.warn(slug, f"exercise {letter}: {len(raw_items) - len(items)} of {len(raw_items)} items skipped "
                              "(several blanks, open answers or no key)")
        items = items[:20]
        stimulus = {"instructions": normalise(ex.get("instruction") or "Complete the sentences."),
                    "unitRef": unit_ref}
        if task_type == "VOCAB_EXERCISE" and ex.get("wordBank"):
            stimulus["wordBank"] = sorted({normalise(str(w)) for w in ex["wordBank"] if str(w).strip()})
        set_envelope = envelope(source_code=source_code, slug=slug, task_type=task_type,
                                payload={"stimulus": stimulus, "items": items}, topic=topic, cefr=None,
                                skills=[f"{'vocab' if task_type == 'VOCAB_EXERCISE' else 'grammar'}.practice"],
                                locator={**locator, "exercise": letter}, report=report, where=slug,
                                method="OCR_LLM")
        out.append(set_envelope)
    return out
