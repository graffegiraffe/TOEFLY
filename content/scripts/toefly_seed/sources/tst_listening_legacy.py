"""TST Prep, 100 TOEFL Listening Practice Questions (2020, legacy format): lectures with 2-4 questions each."""

import re
from collections.abc import Iterator
from pathlib import Path

from ..common import pdf, tst
from ..common.envelope import Report, envelope
from ..common.text import normalise, word_count

SOURCE_CODE = "tst-listening-legacy-2020"
ZW = "​"
HEADER = re.compile(r"^\s*(?:Practice Questions for the TOEFL|Practice for the TOEFL)")
NUMBER = re.compile(r"^(\d)\.\s+(.*)$")
QUESTION_KEY = re.compile(r"^(?:Question )?(\d)(?:\.| -) ?([A-D])\s*\(([^)]*)\)\s*$")
WORDS_PER_SECOND = 2.5
DOMAINS = {
    "Anatomy and Physiology": "LIFE_SCIENCE", "Astronomy": "PHYSICAL_SCIENCE", "Chemistry": "PHYSICAL_SCIENCE",
    "Psychology": "SOCIAL_SCIENCE", "Sociology": "SOCIAL_SCIENCE", "Biology": "LIFE_SCIENCE",
    "Economics": "SOCIAL_SCIENCE", "US History": "SOCIAL_SCIENCE", "World History": "SOCIAL_SCIENCE",
    "Physics": "PHYSICAL_SCIENCE",
}


def source_info() -> dict:
    return {
        "sourceCode": SOURCE_CODE,
        "title": "100 TOEFL Listening Practice Questions (2020, legacy format)",
        "publisher": "TST Prep",
        "year": 2020,
        "url": "https://tstprep.com",
        "examFormat": "LEGACY",
        "license": "THIRD_PARTY_ATTRIBUTION",
        "attributionText": "TST Prep, “100 TOEFL Listening Practice Questions”, 2020, tstprep.com",
        "extra": {},
    }


def _centered(line: str) -> bool:
    return bool(line.strip()) and len(line) - len(line.lstrip()) >= 20


def _norm(title: str) -> str:
    text = re.sub(r"\b(?:the|a|an)\b", "", title.lower().replace("&", "and"))
    return re.sub(r"[^a-z]", "", text)


def extract_file(path: Path, report: Report) -> Iterator[dict]:
    subject, mode = "", "Q"
    lectures: dict[str, list[dict]] = {}
    keys: dict[str, list[dict]] = {}
    current_q: dict | None = None
    for page in pdf.pages(path):
        lines = page.replace(ZW, "").splitlines()
        header_at = next((i for i, l in enumerate(lines) if HEADER.match(l)), None)
        if header_at is None:
            continue
        following = [l for l in lines[header_at + 1 :] if l.strip()]
        page_subject = following[0].strip() if following else ""
        if page_subject in DOMAINS and page_subject != subject:
            subject, mode, current_q = page_subject, "Q", None
            lectures[subject], keys[subject] = [], []
        if not subject:
            continue
        body = [l for l in lines[header_at + 2 :] if not re.fullmatch(r"\s*\d{1,3}\s*", l)]
        if any(l.strip() == "Answer Key" for l in body):
            mode = "KEY"
            body = body[[l.strip() for l in body].index("Answer Key") + 1 :]
        if mode == "Q":
            for at, line in enumerate(body):
                if HEADER.match(line) or line.strip() in (subject,):
                    continue
                ahead = [l.strip() for l in body[at + 1 :] if l.strip()][:3]
                if _centered(line) and any(a.startswith("Directions") for a in ahead):
                    current_q = {"title": line.strip(), "lines": [], "questions": False}
                    lectures[subject].append(current_q)
                elif current_q is not None:
                    current_q["lines"].append(line)
                    if NUMBER.match(line.strip()):
                        current_q["questions"] = True
        else:
            keys[subject].extend(body)
    for subject_name, lecture_list in lectures.items():
        lecture_list = [l for l in lecture_list if l["questions"]]
        parsed_keys = _parse_keys(keys.get(subject_name, []))
        matched: list[dict | None] = []
        for lecture in lecture_list:
            wanted = _norm(lecture["title"])
            entry = next((k for k in parsed_keys if _norm(k["title"]) == wanted and not k["used"]), None)
            if entry is not None:
                entry["used"] = True
            matched.append(entry)
        leftovers = [k for k in parsed_keys if not k["used"]]
        for position, entry in enumerate(matched):
            if entry is None and leftovers:
                matched[position] = leftovers.pop(0)
        for index, (lecture, entry) in enumerate(zip(lecture_list, matched), start=1):
            yield from _build(subject_name, index, lecture, entry, report)


def _parse_keys(lines: list[str]) -> list[dict]:
    """Lecture title, ``Question N - X (type)`` entries and the ``Transcript``."""
    starts = []
    stripped = [l.strip() for l in lines]
    for i, line in enumerate(stripped):
        if line and not line.startswith(("Question ", "Transcript")) and not QUESTION_KEY.match(line) and len(line) < 90:
            nxt = next((s for s in stripped[i + 1 :] if s), "")
            if nxt.startswith("Question 1 -") or re.match(r"^1\. [A-D]\s*\(", nxt):
                starts.append(i)
    entries = []
    for n, start in enumerate(starts):
        end = starts[n + 1] if n + 1 < len(starts) else len(lines)
        block = lines[start:end]
        title = block[0].strip()
        questions: dict[int, dict] = {}
        transcript: list[str] = []
        cur = None
        in_transcript = False
        for line in block[1:]:
            s = line.strip()
            q = QUESTION_KEY.match(s)
            if q:
                cur = {"letter": q.group(2), "type": q.group(3).strip(), "text": []}
                questions[int(q.group(1))] = cur
                in_transcript = False
            elif s == "Transcript":
                in_transcript = True
            elif in_transcript:
                transcript.append(line)
            elif cur is not None:
                cur["text"].append(s)
        entries.append({"title": title, "questions": questions, "transcript": transcript, "used": False})
    return entries


def _centered_text(line: str) -> bool:
    return False


def _paragraphs(lines: list[str]) -> list[str]:
    out, cur = [], []
    for line in lines:
        if line.strip():
            cur.append(line)
        elif cur:
            out.append(pdf.unwrap(cur))
            cur = []
    if cur:
        out.append(pdf.unwrap(cur))
    return out


def _build(subject: str, number: int, lecture: dict, entry, report: Report) -> Iterator[dict]:
    slug = f"{re.sub(r'[^a-z]+', '-', subject.lower()).strip('-')}-{number:03d}"
    where = slug
    if entry is None:
        report.warn(where, "no key entry")
        return
    split = next(i for i, l in enumerate(lecture["lines"]) if NUMBER.match(l.strip()))
    intro = lecture["lines"][:split]
    questions = tst.parse_questions(lecture["lines"][split:], NUMBER, None)
    directions = " ".join(p for p in _paragraphs(intro) if p.startswith("Directions"))
    notes = [p for p in _paragraphs(intro) if p.startswith("*")]
    paragraphs = []
    for p in _paragraphs(entry["transcript"]):
        paragraphs.append(p.strip('"“” '))
    items = []
    for q in questions:
        key = entry["questions"].get(q.number)
        if not q.options:
            report.warn(where, f"Q{q.number}: not a multiple-choice question (table or ordering), skipped")
            continue
        if key is None or len(q.options) != 4:
            report.warn(where, f"Q{q.number}: missing key or {len(q.options)} options")
            continue
        ordinal = len(items) + 1
        explanation = normalise(" ".join(key["text"]))
        summary, notes_by_letter = tst.split_explanation(explanation, key["letter"])
        options = []
        for letter, text in zip(tst.LETTERS, q.options, strict=True):
            rationale = summary if letter == key["letter"] else notes_by_letter.get(letter, summary)
            options.append({"key": letter, "text": text, "rationale": rationale or tst.PLACEHOLDER})
        subskill = re.sub(r"[^A-Z]+", "_", key["type"].upper()).strip("_") or "DETAIL"
        report.by_subskill[subskill] = report.by_subskill.get(subskill, 0) + 1
        items.append({"ordinal": ordinal, "subskill": subskill, "prompt": q.stem, "options": options,
                      "answerKey": key["letter"], "explanation": {"summary": summary or explanation}})
    items = [i for i in items if i]
    if not items or not paragraphs:
        report.warn(where, "no items or no transcript")
        return
    text = " ".join(paragraphs)
    stimulus = {"title": lecture["title"], "domain": DOMAINS[subject],
                "contextLine": normalise(directions.replace("Directions:", "").strip()) or "Listen to part of a talk.",
                "speakers": [{"id": "A", "role": "LECTURER"}],
                "transcript": [{"speaker": "A", "text": t} for t in paragraphs],
                "audio": {"origin": "TTS", "accent": "US", "durationEstimated": True,
                          "durationSeconds": round(max(1.0, word_count(text) / WORDS_PER_SECOND), 1)},
                "wordCount": word_count(text)}
    if notes:
        stimulus["note"] = " ".join(notes)
    payload = {"stimulus": stimulus, "items": items}
    yield envelope(source_code=SOURCE_CODE, slug=slug, task_type="LEGACY_LISTENING", payload=payload,
                   topic=lecture["title"], cefr=None, skills=sorted({f"listening.{i['subskill'].lower()}" for i in items}),
                   locator={"subject": subject}, report=report, where=where, domain=DOMAINS[subject])
