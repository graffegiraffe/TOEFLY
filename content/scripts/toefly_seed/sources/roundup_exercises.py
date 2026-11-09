"""New Round-Up 5: exercises, quizzes, tests and progress checks read from page images (docs/08 §5.2, §5.3, §7.1).

Questions come from the Student's Book (units, progress checks) and from the Teacher's Book (quizzes, tests); answers
come only from the keys of the Teacher's Book, never from the handwriting in the scans. Only items with one gap and a
key are kept. Every set is ``OCR_LLM`` with ``reviewed: false`` and waits for an administrator. The rule card is a
summary in the model's own words, checked for copied 8-grams against the transcription of the rule boxes (§5.4).
"""

import re

from ..common import llm
from ..common.envelope import Report, envelope
from ..common.text import normalise
from .collins_exercises import PLACEHOLDER, _choice, _options, render

# docs/03-domain-model.md §12.3: subtopics of every Round-Up unit
SUBTOPICS = {
    1: ["PRESENT_SIMPLE", "PRESENT_CONTINUOUS", "PRESENT_PERFECT", "PRESENT_PERFECT_CONT", "STATIVE_VERBS"],
    2: ["PAST_SIMPLE", "PAST_CONTINUOUS", "PAST_PERFECT", "PAST_PERFECT_CONT", "USED_TO_WOULD", "WAS_GOING_TO"],
    3: ["FUTURE_SIMPLE", "BE_GOING_TO", "FUTURE_CONTINUOUS", "FUTURE_PERFECT", "PRESENT_FOR_FUTURE"],
    4: ["INFINITIVE", "BARE_INFINITIVE", "GERUND", "VERB_PATTERN", "PARTICIPLE", "PARTICIPLE_CLAUSE"],
    5: ["ABILITY", "PERMISSION", "POSSIBILITY", "OBLIGATION", "NECESSITY", "PROHIBITION", "PERFECT_MODALS", "ADVICE"],
    6: ["PASSIVE_FORMS", "PASSIVE_MODALS", "PASSIVE_REPORTING", "AGENT_BY", "PASSIVE_INFINITIVE_GERUND"],
    7: ["CONDITIONAL_0", "CONDITIONAL_1", "CONDITIONAL_2", "CONDITIONAL_3", "MIXED_CONDITIONAL", "WISH_PRESENT",
        "WISH_PAST", "IF_ONLY"],
    8: ["TIME_CLAUSES", "PURPOSE", "CONCESSION", "RESULT", "REASON", "EXCLAMATIONS", "RELATIVE_CLAUSES",
        "LINKING_WORDS"],
    9: ["STATEMENTS", "QUESTIONS", "COMMANDS", "REPORTING_VERBS", "BACKSHIFT"],
    10: ["COMPOUND_NOUNS", "AGREEMENT", "COUNTABLE_UNCOUNTABLE", "ARTICLE_A_AN", "ARTICLE_THE", "ZERO_ARTICLE"],
    11: ["HAVE_SOMETHING_DONE", "CAUSATIVE_VERBS"],
    12: ["ADJECTIVE_ORDER", "ADVERB_FORM", "COMPARATIVE", "SUPERLATIVE", "AS_AS", "TOO_ENOUGH"],
    13: ["DEMONSTRATIVES", "PERSONAL_PRONOUNS", "REFLEXIVE", "POSSESSIVES", "QUANTIFIERS", "INDEFINITE_PRONOUNS"],
    14: ["PLACE", "MOVEMENT", "TIME", "DEPENDENT_PREPOSITION"],
    15: ["YES_NO_QUESTIONS", "WH_QUESTIONS", "SUBJECT_OBJECT_QUESTIONS", "INDIRECT_QUESTIONS", "QUESTION_TAGS",
         "SHORT_ANSWERS"],
}

PAGE_PROMPT = """You are transcribing one printed page of a grammar practice book for a database.
Ignore all handwriting: pencil or pen answers written into the book are NOT part of the page.
Return ONLY JSON: {"exercises":[{"number":"5","instruction":"...","topic":"CODE","items":[{"n":1,
"text":"sentence with ___ where the gap is","options":["a-text","b-text"]|null,"blanks":1}]}],"ruleText":"..."}.
Rules:
- number is the printed exercise number (or letter), "1" when the page is one list of numbered questions without
  exercise numbers; instruction is copied exactly.
- For a text with numbered gaps 1) 2) ..., return one item per gap: text is the sentence containing that gap with ___
  in its place (other gaps of the same sentence become "..."); keep a word given in brackets, e.g. "___ (need)".
- options are the printed choices (A, B, C or a/b) in order without their letters, or null.
- blanks is the number of gaps in the item. Copy text exactly, do not correct it.
- topic is the one code from this list that fits the exercise best: {topics}.
- Skip speaking, listening, writing and picture-only tasks.
- ruleText is the verbatim text of the grammar explanation boxes and tables of the page ("" if none).
If the page has no exercise return {"exercises":[],"ruleText":"..."}."""

KEY_PROMPT = """This is a page of the answer key of a grammar book (Teacher's Book). Return ONLY JSON:
{"sections":[{"kind":"UNIT"|"ENGLISH_IN_USE"|"PROGRESS_CHECK"|"REVISION"|"TEST"|"QUIZ","number":1,
"continued":false,"exercises":{"5":[{"n":1,"answer":"needs"}]}}]}.
The page has two columns: read the whole left column from top to bottom, then the whole right column, and list the
sections in that order. A heading such as "1 Present Forms" (kind UNIT), "English in Use 2" (kind ENGLISH_IN_USE,
a section of its own, not part of the unit), "Progress Check 1", "Revision 2", "Test 3" or "Quiz 4" starts a section;
the answers at the top of the left column before any heading belong to the section of the previous page, and the
answers at the top of the right column belong to the last section of the left column: give them "continued": true
and number null. Inside a section a bold exercise number starts an exercise; a section that is
one list of numbered answers without exercise numbers has the single exercise "1". For every numbered
answer give n and the answer exactly as printed; alternatives stay joined with " / ". Mark open answers by starting
them with "SUGGESTED:". Give answers of a gap that has several words as one string."""

RULE_PROMPT = """Write a concise rule card in your own words for B1-B2 learners about "{title}".
Do not copy sentences from the source below. Use at most 8 consecutive words from the source. Provide your own new
example sentences. Return ONLY JSON: {{"sections":[{{"heading":"...","rules":["..."],"examples":["..."],
"commonMistakes":["..."]}}]}}.
Source:
{source}"""

# pages (pdf indexes) of the Student's Book and the Teacher's Book
SB_UNIT_PAGES = {1: (4, 13), 2: (14, 27), 3: (28, 38), 4: (41, 51), 5: (52, 65), 6: (66, 77), 7: (80, 91),
                 8: (92, 109), 9: (110, 123), 10: (126, 135), 11: (136, 141), 12: (142, 157), 13: (160, 172),
                 14: (173, 179), 15: (180, 188)}
SB_PROGRESS_CHECK_PAGES = {1: (39, 40), 2: (78, 79), 3: (124, 125), 4: (158, 159), 5: (189, 190)}
TB_TEST_PAGES = {n: (33 + 3 * (n - 1), 35 + 3 * (n - 1)) for n in range(1, 6)}
TB_QUIZ_PAGES = {n: (51 + 2 * (n - 1), 52 + 2 * (n - 1)) for n in range(1, 16)}
TB_UNIT_KEY_PAGES = (6, 32)
TB_TEST_KEY_PAGES = (48, 49)
TB_QUIZ_KEY_PAGES = (82, 83)
SECTION_COUNTS = {"UNIT": len(SB_UNIT_PAGES), "QUIZ": len(TB_QUIZ_PAGES), "TEST": len(TB_TEST_PAGES),
                  "PROGRESS_CHECK": len(SB_PROGRESS_CHECK_PAGES), "REVISION": 5, "ENGLISH_IN_USE": len(SB_UNIT_PAGES)}


def topics_for(units: list[int]) -> list[str]:
    return [f"RU{u:02d}.{s}" for u in units for s in SUBTOPICS[u]]


def read_pages(pdf, first: int, last: int, topics: list[str]) -> tuple[list[dict], str]:
    """Exercises (merged when one continues on the next page) and the rule text of a page range."""
    merged: dict[str, dict] = {}
    rules: list[str] = []
    prompt = PAGE_PROMPT.replace("{topics}", ", ".join(topics))
    for page in range(first, last + 1):
        data = llm.ask_json(prompt, render(pdf, page))
        if data.get("ruleText"):
            rules.append(str(data["ruleText"]))
        for ex in data.get("exercises", []):
            number = str(ex.get("number", "")).strip()
            if not number:
                continue
            if number in merged:
                merged[number]["items"] += ex.get("items", [])
            else:
                merged[number] = {**ex, "number": number, "items": list(ex.get("items", []))}
    return list(merged.values()), "\n".join(rules)


def read_keys(pdf, first: int, last: int) -> dict[tuple[str, int, str], dict[int, str]]:
    """(kind, number, exercise) -> {item: answer}; a section continued from the previous page keeps its address.

    A heading the book does not have (Unit 16 of 15 units, an unknown kind) drops its answers and those continued
    after it, so that they never become the key of another exercise.
    """
    keys: dict[tuple[str, int, str], dict[int, str]] = {}
    current: tuple[str, int] | None = None
    for page in range(first, last + 1):
        data = llm.ask_json(KEY_PROMPT, render(pdf, page))
        for section in data.get("sections", []):
            if not section.get("continued") and section.get("kind") and section.get("number") is not None:
                try:
                    current = (str(section["kind"]), int(section["number"]))
                except (TypeError, ValueError):
                    current = None
                    continue
                if not 1 <= current[1] <= SECTION_COUNTS.get(current[0], 0):  # a misread heading: its answers
                    current = None                                             # would land on another section
            if current is None:
                continue
            for exercise, answers in (section.get("exercises") or {}).items():
                target = keys.setdefault((*current, str(exercise).strip()), {})
                for a in answers if isinstance(answers, list) else []:
                    try:
                        target[int(a["n"])] = str(a["answer"])
                    except (KeyError, TypeError, ValueError):
                        continue
    return keys


MAX_GAP_WORDS = 5  # a longer key is a rewritten sentence (an open answer), which a gap cannot check


def _answers(answer: str) -> list[str] | None:
    """Alternatives of a closed answer, None for an open (suggested) or multi-gap answer."""
    if answer.upper().startswith("SUGGESTED") or "|" in answer:
        return None
    parts = [normalise(p) for p in answer.split(" / ")]
    if not all(parts) or any(len(p.split()) > MAX_GAP_WORDS for p in parts):
        return None
    return list(dict.fromkeys(parts))


def build_item(raw: dict, answer: str, topic: str) -> dict | None:
    sentence = normalise(str(raw.get("text", "")))
    if int(raw.get("blanks", 1) or 1) != 1 or sentence.count("___") != 1:
        return None
    explanation = {"summary": PLACEHOLDER}
    options = _options(raw.get("options"))
    if options:
        chosen = _choice(options, answer)
        if chosen is None:
            return None
        if 3 <= len(options) <= 4:
            return {"exerciseType": "MULTIPLE_CHOICE", "sentence": sentence, "options": options,
                    "answerKey": chosen["key"], "grammarTopic": topic, "explanation": explanation}
        return {"exerciseType": "GAP_FILL", "sentence": sentence, "hint": " / ".join(o["text"] for o in options),
                "answerKey": chosen["text"], "acceptedAnswers": [], "grammarTopic": topic, "explanation": explanation}
    accepted = _answers(answer)
    if not accepted:
        return None
    hint = re.search(r"___\s*\(([^)]+)\)", sentence)
    item = {"exerciseType": "GAP_FILL", "sentence": sentence, "answerKey": accepted[0],
            "acceptedAnswers": accepted[1:], "grammarTopic": topic, "explanation": explanation}
    if hint:
        item["hint"] = hint.group(1)
    return item


def build_sets(*, exercises: list[dict], keys: dict[str, dict[int, str]], topics: list[str], unit_ref: dict,
               slug_base: str, topic_title: str, role: str, report: Report, locator: dict,
               source_code: str) -> list[dict]:
    """One GRAMMAR_EXERCISE set per numbered exercise; ``keys`` maps the exercise number to {item: answer}."""
    out = []
    for ex in exercises:
        number = str(ex["number"])
        slug = f"{slug_base}-ex-{re.sub(r'[^a-z0-9]+', '-', number.lower()).strip('-')}"
        answers = keys.get(number, {})
        topic = str(ex.get("topic", ""))
        if not answers:
            report.warn(slug, f"exercise {number}: no key in the Teacher's Book; not extracted")
            continue
        if topic not in topics:
            report.warn(slug, f"exercise {number}: topic “{topic}” is not one of the unit; not extracted")
            continue
        items = []
        for raw in ex.get("items", []):
            try:
                n = int(raw["n"])
            except (KeyError, TypeError, ValueError):
                continue
            if n not in answers:
                continue
            item = build_item(raw, answers[n], topic)
            if item is not None:
                items.append({"ordinal": len(items) + 1, **item})
        total = len(ex.get("items", []))
        if not items:
            report.warn(slug, f"exercise {number}: none of {total} items has one gap and a closed key; "
                              "not extracted")
            continue
        if len(items) < total:
            report.warn(slug, f"exercise {number}: {total - len(items)} of {total} items skipped "
                              "(several gaps, open answers, no key or a key matching no option)")
        if len(items) > 20:
            report.warn(slug, f"exercise {number}: {len(items) - 20} items above the limit of 20 left out")
        stimulus = {"instructions": normalise(ex.get("instruction") or "Complete the sentences."),
                    "unitRef": unit_ref}
        set_envelope = envelope(source_code=source_code, slug=slug, task_type="GRAMMAR_EXERCISE",
                                payload={"stimulus": stimulus, "items": items[:20]}, topic=topic_title, cefr=None,
                                skills=["grammar.practice"], locator={**locator, "exercise": number}, report=report,
                                where=slug, method="OCR_LLM")
        set_envelope["_role"] = role
        out.append(set_envelope)
    return out


def ngrams(text: str, n: int = 8) -> set[tuple[str, ...]]:
    words = re.findall(r"[a-z']+", text.lower())
    return {tuple(words[i : i + n]) for i in range(len(words) - n + 1)}


def copied_share(card: dict, source: str) -> float:
    """Share of the card's words that sit in an 8-gram shared with the source (§5.4: at most 2%)."""
    text = " ".join(s for sec in card.get("sections", []) for key in ("heading", "rules", "examples",
                    "commonMistakes") for s in ([sec.get(key)] if isinstance(sec.get(key), str)
                                                else sec.get(key) or []))
    words = re.findall(r"[a-z']+", text.lower())
    shared = ngrams(source)
    covered = set()
    for i in range(len(words) - 7):
        if tuple(words[i : i + 8]) in shared:
            covered.update(range(i, i + 8))
    return len(covered) / len(words) if words else 0.0


def rule_card(title: str, source: str, report: Report, where: str) -> dict | None:
    if len(source.split()) < 30:
        report.warn(where, "too little rule text on the unit pages for a rule card")
        return None
    for attempt in range(3):
        prompt = RULE_PROMPT.format(title=title, source=source[:6000]) + ("" if not attempt else f"\n(attempt {attempt + 1})")
        card = llm.ask_json(prompt)
        if not card.get("sections"):
            continue
        share = copied_share(card, source)
        if share <= 0.02:
            return {"sections": card["sections"], "generatedBy": "SUMMARY_OF_SOURCE"}
        report.warn(where, f"rule card attempt {attempt + 1} shares {share:.0%} of its words with the book")
    report.warn(where, "no rule card within the copying limit; to be written by hand")
    return None
