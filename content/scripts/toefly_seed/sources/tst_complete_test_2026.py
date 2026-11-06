"""TST Prep, TOEFL Practice Test #1 (2026 format): Reading, Listening, Speaking and Writing of one form."""

import re
import subprocess
from collections.abc import Iterator
from pathlib import Path

from ..common import pdf, tst
from ..common.envelope import Report, envelope
from ..common.text import normalise, word_count
from . import tst_listening_2026 as listening
from . import tst_reading_2026 as reading

SOURCE_CODE = "tst-complete-test-1-2026"
NUM = re.compile(r"^(\d{1,2})\.\s+(.*)$")
KEY = re.compile(r"^(\d{1,2})\.\s*([A-D])\b\s*(.*)$")
STOP = re.compile(r"^\s*(?:\d{1,2}\.\s*$|Listen to |NOTE TO STUDENT|Do NOT)")
READ = re.compile(r"^\s*Read (an? [a-z ]+)\.\s*$")
HEAD = re.compile(r"^(Listen and Choose|Conversation|Announcement|Listen to an Academic Talk|Listen to a Talk) #(\d+)( Transcript)?\s*$")
SECTION = re.compile(r"For the TOEFL.{0,4}\s+(Reading|Listening|Speaking|Writing) Section")
FOOTER = ("Practice Test #1", "For the TOEFL")
RAW_TO_BAND: dict[str, list] = {}


def source_info() -> dict:
    return {
        "sourceCode": SOURCE_CODE,
        "title": "TOEFL Practice Test #1 (2026 format)",
        "publisher": "TST Prep (Vocabulary Ninja Academic Services LLC)",
        "year": 2025,
        "url": "https://tstprep.com",
        "examFormat": "TOEFL_2026",
        "license": "THIRD_PARTY_ATTRIBUTION",
        "attributionText": "TST Prep, “TOEFL Practice Test #1”, 2025, tstprep.com",
        "extra": {"rawToBand": RAW_TO_BAND},
    }


def _sections(path: Path) -> dict[str, list[str]]:
    out: dict[str, list[str]] = {"Reading": [], "Listening": [], "Speaking": [], "Writing": []}
    for page in pdf.pages(path):
        lines = page.replace("​", "").splitlines()
        match = next((SECTION.search(l) for l in lines if SECTION.search(l)), None)
        if not match:
            continue
        for line in lines:
            s = line.strip()
            if s.startswith(FOOTER) or re.fullmatch(r"\d{1,3}", s):
                continue
            out[match.group(1)].append(line.rstrip())
    return out


def _rubric(lines: list[str], section: str) -> None:
    table = []
    for line in lines:
        m = re.match(r"^\s*(\d+)(?:-(\d+))?\s+([\d.]+)\s*\((\d+)(?:-(\d+))?\)\s*$", line)
        if m:
            low, high = int(m.group(1)), int(m.group(2) or m.group(1))
            table.append({"rawFrom": low, "rawTo": high, "band": float(m.group(3)),
                          "legacyFrom": int(m.group(4)), "legacyTo": int(m.group(5) or m.group(4))})
    if table:
        RAW_TO_BAND[section.lower()] = table


def extract_file(path: Path, report: Report) -> Iterator[dict]:
    RAW_TO_BAND.clear()
    sections = _sections(path)
    raw = subprocess.run(["pdftotext", "-raw", str(path), "-"], capture_output=True, text=True, check=True).stdout
    yield from _reading(sections["Reading"], report)
    yield from _listening(sections["Listening"], report)
    yield from _speaking(raw, report)
    yield from _writing(raw, report)


def _cover_split(lines: list[str]) -> tuple[list[str], list[str]]:
    """Body (before the cover page ``Answer Key``) and the rest."""
    at = next(i for i, l in enumerate(lines) if l.strip() == "Answer Key")
    return lines[:at], lines[at + 1 :]


def _module_keys(rest: list[str]) -> list[str]:
    first = next(i for i, l in enumerate(rest) if l.strip() == "Module 1: Answer Key")
    _rubric(rest[:first], "")  # replaced below by the caller with the proper section name
    return [l for l in rest[first:] if not re.fullmatch(r"\s*Module \d: Answer Key\s*", l)]


# ---------------------------------------------------------------- reading

def _reading(lines: list[str], report: Report) -> Iterator[dict]:
    body, rest = _cover_split(lines)
    _rubric(rest, "reading")
    key_lines = _module_keys(rest)
    RAW_TO_BAND.pop("", None)
    key = tst.parse_key(key_lines, KEY)
    module = 1
    segments: list[dict] = []
    seen_question = True
    for line in body:
        s = line.strip()
        m = re.fullmatch(r"Module (\d)", s)
        if m:
            module = int(m.group(1))
            segments.append({"kind": "module", "module": module})
            seen_question = True
            continue
        if s.startswith("Fill in the missing letters"):
            segments.append({"kind": "ctw", "module": module, "lines": []})
            seen_question = True
            continue
        read = READ.match(line)
        centered = bool(s) and len(line) - len(line.lstrip()) >= 20
        if read:
            segments.append({"kind": "daily", "module": module, "title": read.group(1), "lines": []})
            seen_question = False
        elif centered and seen_question and not NUM.match(s) and not tst.OPTION.match(line) \
                and not s.startswith("The TOEFL Reading Section") and s != "Directions":
            segments.append({"kind": "academic", "module": module, "title": s, "lines": []})
            seen_question = False
        elif segments and "lines" in segments[-1]:
            segments[-1]["lines"].append(line)
            if NUM.match(s):
                seen_question = True
    counts = {"daily": 0, "academic": 0}
    for seg in segments:
        if seg["kind"] in counts:
            stim, qs = reading._split_questions(seg["lines"], NUM)
            if not qs:
                continue
            counts[seg["kind"]] += 1
            slug = f"{'daily-life' if seg['kind'] == 'daily' else 'academic'}-m{seg['module']}-{counts[seg['kind']]:03d}"
            build = reading.daily_set if seg["kind"] == "daily" else reading.academic_set
            yield build(SOURCE_CODE, slug, seg["title"], stim, qs, key, report, {"module": seg["module"]}, NUM) \
                if seg["kind"] == "academic" else \
                reading.daily_set(SOURCE_CODE, slug, seg["title"], stim, qs, key, report, {"module": seg["module"]}, NUM)
    yield from _ctw(key_lines, report)


def _ctw(key_lines: list[str], report: Report) -> Iterator[dict]:
    blocks: list[tuple[str, list[str]]] = []
    collecting = False
    for line in key_lines:
        m = re.match(r"^\s*(\d+)-(\d+)\s*$", line)
        if m:
            blocks.append((f"{m.group(1)}-{m.group(2)}", []))
            collecting = True
        elif KEY.match(line.strip()):
            collecting = False
        elif collecting and line.strip():
            blocks[-1][1].append(line)
    for number, (label, text_lines) in enumerate(blocks, start=1):
        slug = f"complete-the-words-{number:03d}"
        # the same assembly as the 2026 reading book: gaps from prefix{suffix}
        yield from _ctw_one(slug, label, text_lines, report)


def _ctw_one(slug: str, label: str, text_lines: list[str], report: Report) -> Iterator[dict]:
    markup = pdf.unwrap(text_lines)
    gaps = list(reading._GAP.finditer(markup))
    if len(gaps) != 10:
        report.warn(slug, f"{len(gaps)} gaps instead of 10")
    sentences = re.split(r"(?<=[.!?])\s+", markup)
    items = [{"ordinal": n, "gapId": f"g{n}", "prefix": g.group(1), "suffix": g.group(2),
              "fullWord": g.group(1) + g.group(2), "answerKey": g.group(0), "acceptedAnswers": []}
             for n, g in enumerate(gaps, start=1)]
    plain = reading._GAP.sub(lambda m: m.group(1) + m.group(2), markup)
    payload = {"stimulus": {"title": f"Complete the Words {label}", "markup": markup, "wordCount": word_count(plain)},
               "items": items}
    yield envelope(source_code=SOURCE_CODE, slug=slug, task_type="COMPLETE_THE_WORDS", payload=payload,
                   topic=sentences[0], cefr="B2", skills=["reading.complete_the_words"], locator={"keyBlock": label},
                   report=report, where=slug)


# -------------------------------------------------------------- listening

def _listening(lines: list[str], report: Report) -> Iterator[dict]:
    body, rest = _cover_split(lines)
    _rubric(rest, "listening")
    first = next(i for i, l in enumerate(rest) if l.strip() == "Module 1: Answer Key")
    key_lines = [l for l in rest[first:] if not re.fullmatch(r"\s*Module \d: Answer Key\s*", l)]
    RAW_TO_BAND.pop("", None)
    questions = {q.number: q for q in tst.parse_questions(body, NUM, STOP)}
    choose_options = _choose_options(body)
    buckets: dict[tuple[str, int], dict] = {}
    order: list[tuple[str, int]] = []
    current = None
    for line in key_lines:
        m = HEAD.match(line.strip())
        if m:
            ident = (m.group(1), int(m.group(2)))
            if ident not in buckets:
                buckets[ident] = {"key": [], "transcript": []}
                order.append(ident)
            current = buckets[ident]["transcript" if m.group(3) else "key"]
        elif current is not None:
            current.append(line)
    for kind, index in order:
        data = buckets[(kind, index)]
        if kind == "Listen and Choose":
            yield from _choose_set(index, data["key"], choose_options, report)
        else:
            yield from _passage(kind, index, data, questions, report)


def _choose_options(body: list[str]) -> dict[int, list[str]]:
    out: dict[int, list[str]] = {}
    current = None
    for line in body:
        m = re.match(r"^\s*(\d{1,2})\.\s*$", line)
        if m:
            current = int(m.group(1))
            out[current] = []
            continue
        o = tst.OPTION.match(line)
        if current and o and len(out[current]) < 4:
            out[current].append(normalise(o.group(2)))
        elif current and line.strip() and out[current] and len(out[current]) < 4 and not o:
            if line.strip().startswith(("Listen to", "NOTE", "Module")):
                current = None
            else:
                out[current][-1] += " " + line.strip()
    return {n: o for n, o in out.items() if len(o) == 4}


def _estimated_audio(text: str) -> dict:
    return {"origin": "TTS", "accent": "US", "durationEstimated": True,
            "durationSeconds": round(max(1.0, word_count(text) / listening.WORDS_PER_SECOND), 1)}


def _choose_set(index: int, key_lines: list[str], options: dict[int, list[str]], report: Report) -> Iterator[dict]:
    slug = f"choose-{index:03d}"
    entries: dict[int, dict] = {}
    current = None
    for line in key_lines:
        m = listening.CHOICE_ENTRY.match(line)
        if m:
            current = int(m.group(1))
            entries[current] = {"letter": m.group(2), "answer": normalise(m.group(3)), "lines": []}
        elif current:
            entries[current]["lines"].append(line)
    items = []
    for number in sorted(entries):
        entry = entries[number]
        paragraphs = listening._paragraph_text(entry["lines"])
        opts = options.get(number)
        if len(paragraphs) < 2 or not opts:
            report.warn(slug, f"Q{number}: incomplete")
            continue
        statement, explanation = paragraphs[-1], " ".join(paragraphs[:-1])
        if opts["ABCD".index(entry["letter"])] != entry["answer"]:
            report.warn(slug, f"Q{number}: KEY_SHAPE_MISMATCH")
        built, summary = listening._options(tst.Question(number, statement, opts), entry["letter"], explanation,
                                            report, slug)
        items.append({"ordinal": len(items) + 1, "prompt": "Choose the best response.", "options": built,
                      "answerKey": entry["letter"], "explanation": {"summary": summary}, "transcript": statement,
                      "audio": _estimated_audio(statement)})
    if not items:
        return
    payload = {"stimulus": {"contextLine": "Listen to the statement and choose the best response."}, "items": items}
    yield envelope(source_code=SOURCE_CODE, slug=slug, task_type="LISTEN_CHOOSE_RESPONSE", payload=payload,
                   topic=items[0]["transcript"], cefr="B2", skills=["listening.choose_response"],
                   locator={"questionNumbers": sorted(entries)}, report=report, where=slug)


_KINDS = {
    "Conversation": ("LISTEN_CONVERSATION", "conversation", "Listen to a conversation."),
    "Announcement": ("LISTEN_ANNOUNCEMENT", "announcement", "Listen to an announcement."),
    "Listen to an Academic Talk": ("LISTEN_ACADEMIC_TALK", "academic-talk", "Listen to a talk."),
    "Listen to a Talk": ("LISTEN_ACADEMIC_TALK", "academic-talk", "Listen to a talk."),
}


def _passage(kind: str, index: int, data: dict, questions, report: Report) -> Iterator[dict]:
    task_type, name, default_context = _KINDS[kind]
    slug = f"{name}-{index:03d}"
    key = tst.parse_key(data["key"], KEY)
    lines = list(data["transcript"])
    while lines and not lines[0].strip():
        lines = lines[1:]
    context = ""
    if lines and lines[0].strip().startswith("Listen to"):
        context = normalise(lines[0])
        lines = lines[1:]
    turns = listening._turns(lines)
    if turns:
        speakers = [{"id": t["speaker"], "role": t["label"]} for t in {t["speaker"]: t for t in turns}.values()]
        transcript = [{"speaker": t["speaker"], "text": normalise(t["text"])} for t in turns]
    else:
        role = "PROFESSOR" if task_type == "LISTEN_ACADEMIC_TALK" else "ANNOUNCER"
        speakers = [{"id": "A", "role": role}]
        transcript = [{"speaker": "A", "text": p} for p in listening._paragraph_text(lines)]
    text = " ".join(t["text"] for t in transcript)
    items = []
    for number in sorted(key):
        q = questions.get(number)
        if q is None or len(q.options) != 4:
            report.warn(slug, f"Q{number}: question text not found")
            continue
        letter, explanation = key[number]
        options, summary = listening._options(q, letter, explanation, report, slug)
        sub = listening._subskill(q.stem)
        report.by_subskill[sub] = report.by_subskill.get(sub, 0) + 1
        items.append({"ordinal": len(items) + 1, "subskill": sub, "prompt": q.stem, "options": options,
                      "answerKey": letter, "explanation": {"summary": summary}})
    payload = {"stimulus": {"contextLine": context or default_context, "speakers": speakers, "transcript": transcript,
                            "audio": _estimated_audio(text), "wordCount": word_count(text)}, "items": items}
    yield envelope(source_code=SOURCE_CODE, slug=slug, task_type=task_type, payload=payload,
                   topic=transcript[0]["text"] if transcript else slug, cefr="B2",
                   skills=sorted({f"listening.{i['subskill'].lower()}" for i in items}) or ["listening.detail"],
                   locator={"questionNumbers": sorted(key)}, report=report, where=slug)


# --------------------------------------------------------------- speaking

def _between(raw: str, start: str, end: str | None, after: int = 0) -> str:
    i = raw.index(start, after)
    j = raw.index(end, i + len(start)) if end else len(raw)
    return raw[i + len(start) : j]


def _speaking(raw: str, report: Report) -> Iterator[dict]:
    key_at = raw.index("Speaking Section\nAnswer Key") if "Speaking Section\nAnswer Key" in raw else raw.index("Answer Key", raw.index("Take an Interview"))
    key = raw[key_at:]
    key = key[: key.index("Writing Section")] if "Writing Section" in key else key
    key = re.sub(r"\d* ?\d*Practice Test #1\nFor the TOEFL® Speaking Section\n", "\n", key)
    repeat = key[key.index("Listen and Repeat") : key.index("Take an Interview")]
    phrases = re.findall(r"^(\d)\. (.+)$", repeat, flags=re.M)
    scenario_r = re.search(r"(You are being trained[^\n]*)\n(Repeat only once\.)?", raw)
    scenario = normalise(scenario_r.group(1)) if scenario_r else "Listen and repeat."
    items = []
    for n, text in phrases:
        words = word_count(text)
        items.append({"ordinal": int(n), "text": normalise(text), "wordCount": words, "audio": _estimated_audio(text)})
    if len(items) == 7:
        payload = {"stimulus": {"scenario": scenario}, "items": items}
        yield envelope(source_code=SOURCE_CODE, slug="listen-and-repeat-001", task_type="LISTEN_AND_REPEAT",
                       payload=payload, topic=scenario, cefr="B2", skills=["speaking.listen_and_repeat"],
                       locator={}, report=report, where="listen-and-repeat-001")
    else:
        report.warn("listen-and-repeat-001", f"{len(items)} phrases instead of 7")
    interview = key[key.index("Take an Interview") :]
    interview = re.sub(r"G e t 1 0.*", "", interview, flags=re.S)
    scenario_i = re.search(r"(You have volunteered[^\n]*(?:\n[^\n]*){0,2}?The researcher will ask you some questions\.)", raw)
    parts = re.split(r"^(\d)\. ", interview, flags=re.M)[1:]
    types = ["PERSONAL_EXPERIENCE", "PREFERENCE", "FUTURE_OPINION", "ABSTRACT_OPINION"]
    questions = []
    for n, body in zip(parts[0::2], parts[1::2], strict=False):
        question, _, answer = body.partition("Sample Answer")
        questions.append({"ordinal": int(n), "questionType": types[int(n) - 1],
                          "text": normalise(question.replace("\n", " ")), "responseSeconds": 45,
                          "modelAnswer": {"text": normalise(answer.replace("\n", " ")), "source": "BOOK"},
                          "audio": _estimated_audio(question)})
    if len(questions) == 4:
        scen = normalise(scenario_i.group(1).replace("\n", " ")) if scenario_i else "A short online interview."
        payload = {"stimulus": {"scenario": scen}, "items": questions}
        yield envelope(source_code=SOURCE_CODE, slug="take-an-interview-001", task_type="TAKE_AN_INTERVIEW",
                       payload=payload, topic=scen, cefr="B2", skills=["speaking.interview"], locator={},
                       report=report, where="take-an-interview-001")
    else:
        report.warn("take-an-interview-001", f"{len(questions)} questions instead of 4")


# ---------------------------------------------------------------- writing

_PUNCT = "\"'.,;:!?()"


def _split_punct(word: str) -> list[str]:
    """``students.`` -> ``students`` and ``.``: end marks are separate tokens."""
    m = re.match(r"^(.+?)([.?!,]+)$", word)
    return [m.group(1), m.group(2)] if m else [word]


def _same(a: str, b: str) -> bool:
    return a.replace("’", "'").casefold() == b.replace("’", "'").casefold()


def build_sentence(template_line: str, chunk_line: str, sentence: str):
    """Aligns the key sentence with the template and the chunks (section 4.4, step 2 of Complete Test)."""
    chunks = [c.strip() for c in chunk_line.split(" / ") if c.strip()]
    template: list[tuple[str, str]] = []
    for token in template_line.split():
        if token.startswith("_____"):
            template.append(("SLOT", ""))
            for extra in _split_punct(token[5:]) if token[5:] else []:
                template.append(("FIXED", extra))
        else:
            for part in _split_punct(token):
                template.append(("FIXED", part))
    words = [part for w in sentence.split() for part in _split_punct(w)]
    chunk_words = [c.split() for c in chunks]

    def solve(ti: int, wi: int, used: frozenset[int]):
        if ti == len(template):
            return [] if wi == len(words) else None
        kind, text = template[ti]
        if kind == "FIXED":
            if wi < len(words) and _same(words[wi], text):
                return solve(ti + 1, wi + 1, used)
            return None
        for ci, cw in enumerate(chunk_words):
            if ci in used or words[wi : wi + len(cw)] == [] or len(cw) > len(words) - wi:
                continue
            if all(_same(a, b) for a, b in zip(words[wi : wi + len(cw)], cw, strict=True)):
                rest = solve(ti + 1, wi + len(cw), used | {ci})
                if rest is not None:
                    return [ci] + rest
        return None

    order = solve(0, 0, frozenset())
    return template, chunks, order


def _writing(raw: str, report: Report) -> Iterator[dict]:
    start = raw.index("Build a Sentence\nDirections")
    end = raw.index("Write an Email", start)
    region = raw[start:end]
    key_at = raw.rindex("Answer Key")
    key = raw[key_at:]
    sentences = dict(re.findall(r"^(\d{1,2})\. (.+)$", key[key.index("Build a Sentence") : key.index("Write an Email")], flags=re.M))
    items = []
    blocks = re.split(r"^(\d{1,2})\.\s*$", region, flags=re.M)
    for n, body in zip(blocks[1::2], blocks[2::2], strict=False):
        lines = [l.strip() for l in body.splitlines() if l.strip() and not l.startswith(("Practice Test", "For the TOEFL"))]
        lines = [l for l in lines if not re.fullmatch(r"[\d ]+", l)]
        if len(lines) < 3:
            report.warn(f"build-a-sentence-{n}", "block too short")
            continue
        context, template_line, chunk_line = lines[0], lines[1], lines[2]
        sentence = sentences.get(n)
        if not sentence or " / " not in chunk_line or "_____" not in template_line:
            report.warn(f"build-a-sentence-{n}", "template, chunks or key sentence not found")
            continue
        template, chunks, order = build_sentence(template_line, chunk_line, sentence)
        if order is None:
            report.warn(f"build-a-sentence-{n}", "the key sentence cannot be assembled from the chunks")
            continue
        used = set(order)
        distractors = [i for i in range(len(chunks)) if i not in used]
        slot = 0
        tpl = []
        merged = []
        for kind, text in template:
            if kind == "FIXED":
                if merged and text in {".", "?", "!", ","}:
                    merged[-1] += text
                elif not merged and text in {".", "?", "!", ","} and tpl and tpl[-1]["kind"] == "FIXED":
                    tpl[-1]["text"] += text
                else:
                    merged.append(text)
            else:
                if merged:
                    tpl.append({"kind": "FIXED", "text": " ".join(merged)})
                    merged = []
                slot += 1
                tpl.append({"kind": "SLOT", "slotId": f"s{slot}"})
        if merged:
            tpl.append({"kind": "FIXED", "text": " ".join(merged)})
        items.append({"ordinal": int(n), "contextLine": normalise(context), "template": tpl,
                      "chunks": [{"id": f"c{i + 1}", "text": c} for i, c in enumerate(chunks)],
                      "answerKey": [f"c{i + 1}" for i in order], "acceptedKeys": [],
                      "distractorChunkId": f"c{distractors[0] + 1}" if len(distractors) == 1 else None,
                      "sentence": normalise(sentence)})
        if len(distractors) > 1:
            report.warn(f"build-a-sentence-{n}", f"{len(distractors)} chunks unused")
    if items:
        yield envelope(source_code=SOURCE_CODE, slug="build-a-sentence-001", task_type="BUILD_A_SENTENCE",
                       payload={"stimulus": {}, "items": items}, topic=items[0]["sentence"], cefr="B2",
                       skills=["writing.build_a_sentence"], locator={"questionNumbers": [i["ordinal"] for i in items]},
                       report=report, where="build-a-sentence-001")
    yield from _email_and_discussion(raw, key, report)


def _email_and_discussion(raw: str, key: str, report: Report) -> Iterator[dict]:
    email = raw[raw.index("Write an Email\n", raw.index("Build a Sentence\nDirections")) :]
    email = email[: email.index("Write for an Academic Discussion")]
    body = email.split("\n", 1)[1]
    scenario_part = body.split("Your Response:")[0]
    bullets = [normalise(b) for b in re.split(r"\n?●\s*", scenario_part)[1:]]
    scenario_lines = re.split(r"●", scenario_part)[0]
    scenario = normalise(scenario_lines.replace("\n", " "))
    scenario = re.sub(r"Write an email to .*$", "", scenario).strip() or scenario
    to = re.search(r"To:\s*(.+)", body)
    subject = re.search(r"Subject:\s*(.+)", body)
    bullet_items = []
    for n, text in enumerate(bullets, start=1):
        text = re.sub(r"\s*Write as much as you can.*$", "", text).strip()
        bullet_items.append({"id": f"b{n}", "text": text})
    sample = key[key.index("Write an Email - Sample Response") + len("Write an Email - Sample Response") :]
    sample = sample[: sample.index("Write for an Academic Discussion")]
    sample = re.sub(r"\n\d+ \d+Practice Test #1\nFor the TOEFL® Writing Section\n?", "\n", sample)
    model = normalise(sample.replace("\n", " "))
    if scenario and subject and bullet_items:
        payload = {"stimulus": {"scenario": scenario, "recipient": {"name": normalise(to.group(1)) if to else "Recipient"},
                                "subject": normalise(subject.group(1)), "bullets": bullet_items, "timeLimitSeconds": 420},
                   "items": [{"ordinal": 1, "rubricId": "ETS_EMAIL_V1", "maxScore": 5,
                              "keyPoints": [b["text"] for b in bullet_items],
                              "modelAnswers": [{"text": model, "source": "BOOK"}]}]}
        yield envelope(source_code=SOURCE_CODE, slug="write-email-001", task_type="WRITE_EMAIL", payload=payload,
                       topic=subject.group(1), cefr="B2", skills=["writing.email"], locator={}, report=report,
                       where="write-email-001")
    else:
        report.warn("write-email-001", "scenario, subject or bullets not found")
    disc = raw[raw.index("Write for an Academic Discussion\n", raw.index("Build a Sentence\nDirections")) :]
    disc = disc[: disc.index("Space for typing answers")]
    course = re.search(r"teaching a class on ([a-z ]+)\.", disc)
    names = re.findall(r"^([A-Z][a-z]+)$", disc, flags=re.M)
    prof = re.search(r"^(Dr\. [A-Z][a-z]+)\n(.+)$", disc, flags=re.S | re.M)
    prof_post = normalise(prof.group(2).replace("\n", " ")) if prof else ""
    students_text = disc[disc.index(names[-1]) + len(names[-1]) :] if names else ""
    student_block = students_text[: students_text.index(prof.group(1))] if prof else students_text
    posts = [normalise(p.replace("\n", " ")) for p in re.split(r"\n(?=I (?:think|believe))", student_block.strip())]
    if prof and len(names) >= 2 and len(posts) >= 2:
        sample = key[key.index("Write for an Academic Discussion") :]
        sample = sample[: sample.index("Get 10%") if "Get 10%" in sample else len(sample)]
        sample = re.sub(r"\n\d+ \d+.*$", "", sample, flags=re.S)
        lines = sample.split("\n", 2)
        model_post = normalise(lines[2].replace("\n", " ")) if len(lines) > 2 else ""
        payload = {"stimulus": {"course": course.group(1) if course else "", "professor":
                                {"name": prof.group(1), "post": prof_post},
                                "students": [{"name": names[0], "post": posts[0]}, {"name": names[1], "post": posts[1]}],
                                "minWords": 100, "timeLimitSeconds": 600},
                   "items": [{"ordinal": 1, "rubricId": "ETS_DISCUSSION_V1", "maxScore": 5,
                              "keyPoints": [normalise(lines[1].strip("“” "))] if len(lines) > 1 else ["Express and support an opinion."],
                              "modelAnswers": [{"text": model_post, "source": "BOOK"}]}]}
        yield envelope(source_code=SOURCE_CODE, slug="write-discussion-001", task_type="WRITE_ACADEMIC_DISCUSSION",
                       payload=payload, topic=prof_post[:100], cefr="B2", skills=["writing.academic_discussion"],
                       locator={}, report=report, where="write-discussion-001")
    else:
        report.warn("write-discussion-001", "professor, students or posts not found")
