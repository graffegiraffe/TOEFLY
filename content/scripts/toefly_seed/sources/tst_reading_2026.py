"""TST Prep, 100 Practice Questions for the TOEFL Reading Section, 2026 edition (section 4.4, first extractor)."""

import re
from collections.abc import Iterator
from pathlib import Path

from ..common import pdf, tst
from ..common.envelope import Report, envelope
from ..common.text import normalise, word_count

SOURCE_CODE = "tst-reading-2026"
HEADER = ("100 Practice Questions for the TOEFL",)
_READ = re.compile(r"^\s*Read (an? [a-z ]+)\.\s*$")
_GAP = re.compile(r"([A-Za-z'’-]*)\{([^}]+)\}")
_SENT_END = re.compile(r"[.!?][\"”’)]*(?=\s|$)")


def source_info() -> dict:
    return {
        "sourceCode": SOURCE_CODE,
        "title": "100 Practice Questions for the TOEFL Reading Section (2026 edition)",
        "publisher": "TST Prep (Vocabulary Ninja Academic Services LLC)",
        "year": 2025,
        "url": "https://tstprep.com",
        "examFormat": "TOEFL_2026",
        "license": "THIRD_PARTY_ATTRIBUTION",
        "attributionText": "TST Prep, “100 Practice Questions for the TOEFL Reading Section”, 2025, tstprep.com",
        "extra": {},
    }


def _paragraphs(lines: list[str]) -> list[str]:
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


def _options(question: tst.Question, correct: str, explanation: str, report: Report, where: str):
    summary, notes = tst.split_explanation(explanation, correct)
    options = []
    for key, text in zip(tst.LETTERS, question.options, strict=True):
        rationale = summary if key == correct else notes.get(key)
        if not rationale:
            rationale = tst.PLACEHOLDER
            report.warn(where, f"Q{question.number}: no explanation for {key}")
        options.append({"key": key, "text": text, "rationale": rationale})
    return options, summary


def _daily_subskill(stem: str) -> str:
    s = stem.lower()
    if "closest in meaning" in s:
        return "VOCABULARY"
    if re.search(r"main purpose|purpose of|why (?:did|does|is)", s):
        return "PURPOSE"
    if re.search(r"main idea|mainly about", s):
        return "MAIN_IDEA"
    if re.search(r"infer|suggest|impl(?:y|ies)|likely", s):
        return "INFERENCE"
    return "DETAIL"


def _academic_subskill(stem: str) -> str:
    s = stem.lower()
    if "closest in meaning" in s:
        return "VOCABULARY"
    if "where would the following sentence" in s or "four locations" in s:
        return "INSERT_SENTENCE"
    if re.search(r"why does the author|main purpose|function of|relationship between|organi[sz]ed", s):
        return "RHETORICAL_PURPOSE"
    if re.search(r"infer|suggest|impl(?:y|ies)|most likely", s):
        return "INFERENCE"
    return "FACTUAL"


def _sentence_index(paragraph: str, end: int) -> int:
    return len(_SENT_END.findall(paragraph[:end]))


def _insertion(question: tst.Question, paragraphs: list[str], report: Report, where: str):
    text = " ".join(question.extra)
    quote = re.search(r"“(.+?)”\s*Where would", text)
    excerpt = text.split("Where would the sentence best fit?")[-1].strip()
    if not quote or "(A)" not in excerpt:
        report.warn(where, f"Q{question.number}: insert-sentence question not understood")
        return None
    pieces = re.split(r"\(([A-D])\)", excerpt)
    markers = []
    before = pieces[0]
    for key, after in zip(pieces[1::2], pieces[2::2], strict=False):
        sentences = [s for s in re.split(r"(?<=[.!?])\s+", before.strip()) if s]
        located = None
        if sentences:
            last = sentences[-1]
            for index, paragraph in enumerate(paragraphs):
                at = paragraph.find(last[:60])
                if at >= 0:
                    located = (index, _sentence_index(paragraph, at + len(last)))
                    break
        if not located:
            report.warn(where, f"Q{question.number}: marker {key} not found in the passage")
            return None
        markers.append({"key": key, "paragraph": located[0], "afterSentence": located[1]})
        before = after
    return {"sentence": normalise(quote.group(1)), "markers": markers}


def _stimulus_daily(kind: str, lines: list[str]) -> dict:
    kind = kind.lower()
    body_lines = [l for l in lines]
    if "text chain" in kind:
        messages = []
        for line in body_lines:
            m = re.match(r"^\s*(.+?) \((\d{1,2}:\d{2} [AP]\.M\.)\)\s*(.*)$", line)
            if m:
                messages.append({"sender": m.group(1), "time": m.group(2), "text": m.group(3).strip()})
            elif line.strip() and messages:
                messages[-1]["text"] += " " + line.strip()
        words = word_count(" ".join(m["text"] for m in messages))
        return {"genre": "CHAT", "title": "Text chain", "messages": [dict(m, text=normalise(m["text"])) for m in messages],
                "wordCount": words}
    headers, rest = {}, []
    for line in body_lines:
        m = re.match(r"^\s*(Subject|From|To|Date):\s+(.+)$", line)
        if m and "email" in kind:
            headers[m.group(1)] = m.group(2).strip()
        else:
            rest.append(line)
    paragraphs = _paragraphs(rest)
    if "email" in kind:
        return {"genre": "EMAIL", "title": headers.get("Subject", "Email"), "headers": headers,
                "body": "\n\n".join(paragraphs), "wordCount": word_count(" ".join(paragraphs))}
    title = paragraphs[0] if paragraphs and len(paragraphs[0]) < 90 and len(paragraphs) > 1 else "Announcement"
    body = paragraphs[1:] if title != "Announcement" else paragraphs
    return {"genre": "NOTICE", "title": title, "body": "\n\n".join(body), "wordCount": word_count(" ".join(body))}


def _split_questions(block: list[str]) -> tuple[list[str], list[str]]:
    for index, line in enumerate(block):
        if tst.QUESTION.match(line.strip()):
            return block[:index], block[index:]
    return block, []


def extract(pdf_path: Path, report: Report) -> Iterator[dict]:
    lines = pdf.clean(pdf.pages(pdf_path), HEADER).splitlines()
    key_at = next(i for i, l in enumerate(lines) if l.strip() == "Answer Key")
    body, key_lines = lines[:key_at], lines[key_at:]
    daily_at = next(i for i, l in enumerate(body) if l.strip() == "Read in Daily Life")
    academic_at = next(i for i, l in enumerate(body) if l.strip() == "Read an Academic Passage")
    key = tst.parse_key(key_lines[key_lines.index(next(l for l in key_lines if l.strip() == "Read in Daily Life")):])
    yield from _complete_the_words(key_lines, report)
    yield from _daily_life(body[daily_at + 1 : academic_at], key, report)
    yield from _academic(body[academic_at + 1 :], key, report)


def _complete_the_words(key_lines: list[str], report: Report) -> Iterator[dict]:
    end = next(i for i, l in enumerate(key_lines) if l.strip() == "Read in Daily Life")
    start = next(i for i, l in enumerate(key_lines) if l.strip() == "Complete the Words")
    blocks: list[tuple[str, list[str]]] = []
    for line in key_lines[start + 1 : end]:
        m = re.match(r"^\s*(\d+)-(\d+)\s*$", line)
        if m:
            blocks.append((f"{m.group(1)}-{m.group(2)}", []))
        elif blocks and line.strip():
            blocks[-1][1].append(line)
    for number, (label, text_lines) in enumerate(blocks, start=1):
        slug = f"complete-the-words-{number:03d}"
        where = slug
        markup = pdf.unwrap(text_lines)
        gaps = list(_GAP.finditer(markup))
        if len(gaps) != 10:
            report.warn(where, f"{len(gaps)} gaps instead of 10")
        sentences = re.split(r"(?<=[.!?])\s+", markup)
        if "{" in sentences[0] or "{" in sentences[-1]:
            report.warn(where, "the first or the last sentence is not whole")
        items = []
        for ordinal, gap in enumerate(gaps, start=1):
            prefix, suffix = gap.group(1), gap.group(2)
            items.append({"ordinal": ordinal, "gapId": f"g{ordinal}", "prefix": prefix, "suffix": suffix,
                          "fullWord": prefix + suffix, "answerKey": gap.group(0), "acceptedAnswers": []})
        plain = _GAP.sub(lambda m: m.group(1) + m.group(2), markup)
        payload = {"stimulus": {"title": f"Complete the Words {label}", "markup": markup, "wordCount": word_count(plain)},
                   "items": items}
        yield envelope(source_code=SOURCE_CODE, slug=slug, task_type="COMPLETE_THE_WORDS", payload=payload,
                       topic=sentences[0], cefr="B2", skills=["reading.complete_the_words"],
                       locator={"keyBlock": label}, report=report, where=where)


def _mcq_items(questions, key, report, where, subskill_of, extra=None):
    items = []
    for ordinal, q in enumerate(questions, start=1):
        if q.number not in key:
            report.warn(where, f"Q{q.number}: no key entry")
            continue
        letter, explanation = key[q.number]
        options, summary = _options(q, letter, explanation, report, where)
        item = {"ordinal": ordinal, "subskill": subskill_of(q.stem), "prompt": q.stem, "options": options,
                "answerKey": letter, "explanation": {"summary": summary}}
        if extra:
            extra(item, q)
        report.by_subskill[item["subskill"]] = report.by_subskill.get(item["subskill"], 0) + 1
        items.append(item)
    return items


def _daily_life(body: list[str], key, report: Report) -> Iterator[dict]:
    starts = [i for i, l in enumerate(body) if _READ.match(l)]
    for number, start in enumerate(starts, start=1):
        end = starts[number] if number < len(starts) else len(body)
        kind = _READ.match(body[start]).group(1)
        stimulus_lines, question_lines = _split_questions(body[start + 1 : end])
        questions = tst.parse_questions(question_lines)
        slug = f"daily-life-{number:03d}"
        stimulus = _stimulus_daily(kind, stimulus_lines)
        items = _mcq_items(questions, key, report, slug, _daily_subskill)
        payload = {"stimulus": stimulus, "items": items}
        yield envelope(source_code=SOURCE_CODE, slug=slug, task_type="READ_DAILY_LIFE", payload=payload,
                       topic=stimulus["title"], cefr="B2",
                       skills=sorted({f"reading.{i['subskill'].lower()}" for i in items}),
                       locator={"questionNumbers": [q.number for q in questions]}, report=report, where=slug)


def _academic(body: list[str], key, report: Report) -> Iterator[dict]:
    passages: list[list[str]] = []
    seen_question = True
    for line in body:
        stripped = line.strip()
        is_title = (bool(stripped) and len(line) - len(line.lstrip()) >= 20 and not tst.QUESTION.match(stripped)
                    and not tst.OPTION.match(line))
        if is_title and seen_question:
            passages.append([line])
            seen_question = False
        elif passages:
            passages[-1].append(line)
            if tst.QUESTION.match(stripped):
                seen_question = True
    passages = [b for b in passages if any(tst.QUESTION.match(l.strip()) for l in b)]
    for number, block in enumerate(passages, start=1):
        title = block[0].strip()
        stimulus_lines, question_lines = _split_questions(block[1:])
        paragraphs = _paragraphs(stimulus_lines)
        questions = tst.parse_questions(question_lines)
        slug = f"academic-{number:03d}"

        def extra(item, q, paragraphs=paragraphs, slug=slug):
            if item["subskill"] == "INSERT_SENTENCE":
                item["options"] = [dict(o, text=f"Position {n}") for n, o in enumerate(item["options"], start=1)]
                insertion = _insertion(q, paragraphs, report, slug)
                if insertion:
                    item["insertion"] = insertion
            if item["subskill"] == "VOCABULARY":
                word = re.search(r"“([^”]+)”", q.stem)
                found = None
                for index, paragraph in enumerate(paragraphs):
                    at = paragraph.find(word.group(1)) if word else -1
                    if at >= 0:
                        found = {"text": word.group(1), "paragraph": index, "charStart": at,
                                 "charEnd": at + len(word.group(1))}
                        break
                if found:
                    item["targetWord"] = found
                else:
                    report.warn(slug, f"Q{q.number}: target word not found in the passage")

        # insert-sentence questions have no a-d options: positions are the markers
        for q in questions:
            if "four locations" in q.stem and not q.options:
                q.options = ["Position 1", "Position 2", "Position 3", "Position 4"]
        items = _mcq_items(questions, key, report, slug, _academic_subskill, extra)
        payload = {"stimulus": {"title": title, "domain": "GENERAL", "paragraphs": paragraphs,
                                "wordCount": word_count(" ".join(paragraphs))}, "items": items}
        yield envelope(source_code=SOURCE_CODE, slug=slug, task_type="READ_ACADEMIC_PASSAGE", payload=payload,
                       topic=title, cefr="B2", skills=sorted({f"reading.{i['subskill'].lower()}" for i in items}),
                       locator={"questionNumbers": [q.number for q in questions]}, report=report, where=slug,
                       domain="GENERAL")


def extract_file(path: Path, report: Report) -> Iterator[dict]:
    return extract(path, report)
