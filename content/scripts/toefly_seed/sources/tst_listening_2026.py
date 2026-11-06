"""TST Prep, 100 Practice Questions for the TOEFL Listening Section, 2026 edition (section 4.4)."""

import re
from collections.abc import Iterator
from pathlib import Path

from pypdf import PdfReader

from ..common import pdf, tst
from ..common.envelope import Report, envelope
from ..common.text import normalise, word_count

SOURCE_CODE = "tst-listening-2026"
HEADER = ("100 Practice Questions for the TOEFL",)
MEDIA = "https://media.tstprep.com/freebies/2026/100-toefl-listening-questions-audio-files/"
WORDS_PER_SECOND = 2.5  # the book gives no durations; 150 words per minute is the pace of the recordings

NUMBER = re.compile(r"^(\d{2,3})\.\s+(.*)$")
KEY = re.compile(r"^(\d{2,3})\.\s*([A-D])\b\s*(.*)$")
STOP = re.compile(r"^\s*(?:\d{1,2}\.\s*$|Listen to |NOTE TO STUDENT|Do NOT)")
HEAD = re.compile(r"^(Conversation|Announcement|Listen to an Academic Talk) #(\d+)( Transcript)?\s*$")
CHOICE_ENTRY = re.compile(r"^\s*(\d{1,2})\. ([A-D]), (.*)$")
SPEAKER = re.compile(r"^(Man|Woman|Professor|Student|Speaker \w+|[A-Z][a-z]+):\s*(.*)$")

KINDS = {
    "Conversation": ("LISTEN_CONVERSATION", "listen-to-a-conversation", 4),
    "Announcement": ("LISTEN_ANNOUNCEMENT", "listen-to-an-announcement", 19),
    "Listen to an Academic Talk": ("LISTEN_ACADEMIC_TALK", "listen-to-an-academic-talk", 31),
}
FIRST_FILE = {"LISTEN_CONVERSATION": 4, "LISTEN_ANNOUNCEMENT": 19, "LISTEN_ACADEMIC_TALK": 31}


def source_info() -> dict:
    return {
        "sourceCode": SOURCE_CODE,
        "title": "100 Practice Questions for the TOEFL Listening Section (2026 edition)",
        "publisher": "TST Prep (Vocabulary Ninja Academic Services LLC)",
        "year": 2025,
        "url": "https://tstprep.com",
        "examFormat": "TOEFL_2026",
        "license": "THIRD_PARTY_ATTRIBUTION",
        "attributionText": "TST Prep, “100 Practice Questions for the TOEFL Listening Section”, 2025, tstprep.com",
        "extra": {"hostedAudioBase": MEDIA},
    }


def mp3_links(path: Path) -> set[str]:
    found: set[str] = set()
    for page in PdfReader(str(path)).pages:
        annots = page.get("/Annots")
        for annot in (annots.get_object() if annots is not None else []):
            obj = annot.get_object()
            action = obj.get("/A") if hasattr(obj, "get") else None
            action = action.get_object() if action is not None else None
            if action is not None and "/URI" in action and str(action["/URI"]).endswith(".mp3"):
                found.add(str(action["/URI"]).rsplit("/", 1)[1])
    return found


def _subskill(stem: str) -> str:
    s = stem.lower()
    if re.search(r"main (?:idea|purpose|topic)|mainly|primarily (?:about|discuss)|purpose of the (?:talk|announcement|conversation)", s):
        return "MAIN_IDEA"
    if re.search(r"why does|function|mention", s):
        return "FUNCTION"
    if re.search(r"infer|suggest|impl(?:y|ies)|likely|probably", s):
        return "INFERENCE"
    if re.search(r"feel|attitude|opinion|think of|react", s):
        return "ATTITUDE"
    return "DETAIL"


def _options(question: tst.Question, letter: str, explanation: str, report: Report, where: str):
    summary, notes = tst.split_explanation(explanation, letter)
    options = []
    for key, text in zip(tst.LETTERS, question.options, strict=True):
        rationale = summary if key == letter else notes.get(key)
        if not rationale:
            rationale = tst.PLACEHOLDER
            report.warn(where, f"Q{question.number}: no explanation for {key}")
        options.append({"key": key, "text": text, "rationale": rationale})
    return options, summary


def _turns(lines: list[str]) -> list[dict]:
    """Transcript lines ``Man: ...`` to turns; a text without speakers becomes one turn per paragraph."""
    turns: list[dict] = []
    ids: dict[str, str] = {}
    for line in lines:
        m = SPEAKER.match(line.strip())
        if m:
            label = m.group(1)
            ids.setdefault(label, "ABCDEFGH"[len(ids)])
            turns.append({"speaker": ids[label], "label": label, "text": m.group(2).strip()})
        elif line.strip() and turns:
            turns[-1]["text"] += " " + line.strip()
    return turns


def _paragraph_text(lines: list[str]) -> list[str]:
    paragraphs, current = [], []
    for line in lines:
        if line.strip():
            current.append(line)
        elif current:
            paragraphs.append(pdf.unwrap(current))
            current = []
    if current:
        paragraphs.append(pdf.unwrap(current))
    return paragraphs


def extract_file(path: Path, report: Report) -> Iterator[dict]:
    lines = pdf.clean(pdf.pages(path), HEADER).splitlines()
    key_at = next(i for i, l in enumerate(lines) if l.strip() == "Answer Key")
    body, key_lines = lines[:key_at], lines[key_at + 1 :]
    questions = {q.number: q for q in tst.parse_questions(body, NUMBER, STOP)}
    linked = mp3_links(path)
    yield from _choose(body, key_lines, linked, report)
    yield from _passages(key_lines, questions, linked, report)


def _choose_questions(body: list[str]) -> dict[int, list[str]]:
    """``7.`` alone on a line followed by options a-d."""
    out: dict[int, list[str]] = {}
    current = None
    for line in body:
        m = re.match(r"^\s*(\d{1,2})\.\s*$", line)
        if m and int(m.group(1)) <= 30:
            current = int(m.group(1))
            out[current] = []
            continue
        o = tst.OPTION.match(line)
        if current and o and len(out[current]) < 4:
            out[current].append(normalise(o.group(2)))
        elif current and line.strip() and out[current] and len(out[current]) < 4 + 0 and not o:
            if line.strip().startswith(("Listen to", "NOTE", "Set ")):
                current = None
            else:
                out[current][-1] += " " + line.strip()
    return out


def _choose(body: list[str], key_lines: list[str], linked: set[str], report: Report) -> Iterator[dict]:
    cut = next((i for i, l in enumerate(body) if l.strip() == "Listen to a Conversation"), len(body))
    options = _choose_questions(body[:cut])
    start = next(i for i, l in enumerate(key_lines) if l.strip() == "Listen and Choose")
    end = next(i for i, l in enumerate(key_lines) if l.strip() == "Listen to a Conversation")
    entries: dict[int, dict] = {}
    current = None
    for line in key_lines[start:end]:
        m = CHOICE_ENTRY.match(line)
        if m:
            current = int(m.group(1))
            entries[current] = {"letter": m.group(2), "answer": normalise(m.group(3)), "lines": []}
        elif current and not re.match(r"^\s*Set \d", line):
            entries[current]["lines"].append(line)
    items_by_number = {}
    for number, entry in entries.items():
        paragraphs = _paragraph_text(entry["lines"])
        if len(paragraphs) < 2:
            report.warn(f"choose-{number}", "no explanation or no statement text")
            continue
        statement, explanation = paragraphs[-1], " ".join(paragraphs[:-1])
        opts = options.get(number, [])
        if len(opts) != 4:
            report.warn(f"choose-{number}", f"{len(opts)} options found")
            continue
        if opts["ABCD".index(entry["letter"])] != entry["answer"]:
            report.warn(f"choose-{number}", "KEY_SHAPE_MISMATCH: option text differs from the key")
        question = tst.Question(number, statement, opts)
        built, summary = _options(question, entry["letter"], explanation, report, f"choose-{number}")
        items_by_number[number] = (built, entry["letter"], summary, statement)
    for group, first in enumerate(range(1, 31, 5), start=1):
        numbers = list(range(first, first + 5))
        if not all(n in items_by_number for n in numbers):
            report.warn(f"choose-{group:03d}", "set skipped: incomplete items")
            continue
        set_no = (first - 1) // 10 + 1
        lo = (set_no - 1) * 10 + 1
        items = []
        for ordinal, n in enumerate(numbers, start=1):
            built, letter, summary, statement = items_by_number[n]
            file = f"100.{set_no:02d}-listening-listen-and-choose-sets-{lo}-{lo + 9}-statement-{n - lo + 1:03d}.mp3"
            if file not in linked:
                report.warn(f"choose-{group:03d}", f"Q{n}: audio link rebuilt from the file name pattern")
            items.append({
                "ordinal": ordinal, "prompt": "Choose the best response.", "options": built, "answerKey": letter,
                "explanation": {"summary": summary}, "transcript": statement,
                "audio": {"url": MEDIA + file, "durationSeconds": round(max(1.0, word_count(statement) / WORDS_PER_SECOND), 1),
                          "durationEstimated": True, "origin": "HOSTED_MP3", "accent": "US"},
            })
        payload = {"stimulus": {"contextLine": "Listen to the statement and choose the best response."}, "items": items}
        yield envelope(source_code=SOURCE_CODE, slug=f"choose-{group:03d}", task_type="LISTEN_CHOOSE_RESPONSE",
                       payload=payload, topic=items[0]["transcript"], cefr="B2",
                       skills=["listening.choose_response"], locator={"questionNumbers": numbers}, report=report,
                       where=f"choose-{group:03d}")


def _passages(key_lines: list[str], questions, linked: set[str], report: Report) -> Iterator[dict]:
    start = next(i for i, l in enumerate(key_lines) if l.strip() == "Listen to a Conversation")
    buckets: dict[tuple[str, int], dict] = {}
    order: list[tuple[str, int]] = []
    current = None
    for line in key_lines[start:]:
        m = HEAD.match(line.strip())
        if m:
            ident = (m.group(1), int(m.group(2)))
            if ident not in buckets:
                buckets[ident] = {"key": [], "transcript": []}
                order.append(ident)
            current = buckets[ident]["transcript" if m.group(3) else "key"]
        elif re.match(r"^\s*Listen to (?:a |an )?(?:Conversation|Announcement|Academic Talk)\s*$", line):
            continue
        elif current is not None:
            current.append(line)
    counters: dict[str, int] = {}
    for kind, index in order:
        task_type, _, _ = KINDS[kind]
        data = buckets[(kind, index)]
        slug = f"{ {'LISTEN_CONVERSATION': 'conversation', 'LISTEN_ANNOUNCEMENT': 'announcement', 'LISTEN_ACADEMIC_TALK': 'academic-talk'}[task_type] }-{index:03d}"
        key = tst.parse_key(data["key"], KEY)
        transcript_lines = data["transcript"]
        context = ""
        while transcript_lines and not transcript_lines[0].strip():
            transcript_lines = transcript_lines[1:]
        if transcript_lines and transcript_lines[0].strip().startswith("Listen to"):
            context = normalise(transcript_lines[0])
            transcript_lines = transcript_lines[1:]
        turns = _turns(transcript_lines)
        if turns:
            speakers = [{"id": t["speaker"], "role": t["label"]} for t in {t["speaker"]: t for t in turns}.values()]
            transcript = [{"speaker": t["speaker"], "text": normalise(t["text"])} for t in turns]
        else:
            role = "PROFESSOR" if task_type == "LISTEN_ACADEMIC_TALK" else "ANNOUNCER"
            speakers = [{"id": "A", "role": role}]
            transcript = [{"speaker": "A", "text": p} for p in _paragraph_text(transcript_lines)]
        text = " ".join(t["text"] for t in transcript)
        words = word_count(text)
        file = f"100.{FIRST_FILE[task_type] + index - 1:02d}-{KINDS[kind][1]}-{index}.mp3"
        if file not in linked:
            report.warn(slug, "audio link rebuilt from the file name pattern")
        items = []
        for ordinal, number in enumerate(sorted(key), start=1):
            q = questions.get(number)
            if q is None or len(q.options) != 4:
                report.warn(slug, f"Q{number}: question text not found")
                continue
            letter, explanation = key[number]
            options, summary = _options(q, letter, explanation, report, slug)
            sub = _subskill(q.stem)
            report.by_subskill[sub] = report.by_subskill.get(sub, 0) + 1
            items.append({"ordinal": ordinal, "subskill": sub, "prompt": q.stem, "options": options,
                          "answerKey": letter, "explanation": {"summary": summary}})
        payload = {
            "stimulus": {"contextLine": context or {"LISTEN_CONVERSATION": "Listen to a conversation.", "LISTEN_ANNOUNCEMENT": "Listen to an announcement.", "LISTEN_ACADEMIC_TALK": "Listen to a talk."}[task_type], "speakers": speakers, "transcript": transcript,
                         "audio": {"url": MEDIA + file, "durationSeconds": round(max(1.0, words / WORDS_PER_SECOND), 1),
                                   "durationEstimated": True, "origin": "HOSTED_MP3", "accent": "US"},
                         "wordCount": words},
            "items": items,
        }
        counters[task_type] = counters.get(task_type, 0) + 1
        yield envelope(source_code=SOURCE_CODE, slug=slug, task_type=task_type, payload=payload,
                       topic=transcript[0]["text"] if transcript else slug, cefr="B2",
                       skills=sorted({f"listening.{i['subskill'].lower()}" for i in items}) or ["listening.detail"],
                       locator={"questionNumbers": sorted(key)}, report=report, where=slug)
