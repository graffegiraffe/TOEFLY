#!/usr/bin/env python3
"""Anonymise the author's Writing drafts into eval cases (docs/06-ai-agent.md §7.2, docs/12 phase 5b decisions 2-3).

Reads .docx files with the standard library only: the text of word/document.xml without deleted runs, the tutor's
comments of word/comments.xml with the text they are anchored to, and the task when the draft starts with it.
Names of real people are passed with --hint and never stored here; they become fixed fictional names. The result
stays outside Git (evals/data/), and a scoring sheet is written for the human rater.

Usage:
  anonymise_drafts.py --emails ~/Documents/English/Letters --discussions ~/Documents/English/academic \
      --hint "First Last" --hint "Tutor Name" --out evals/data
"""
import argparse
import csv
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
FICTIONAL = ["Alex Morgan", "Jamie Lee", "Robin Park", "Casey Reed"]
EMAIL = re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+")
PHONE = re.compile(r"\+?\d[\d\s().-]{7,}\d")
TIMING = re.compile(
    r"\(?\s*\d{1,3}\s*(min|minutes|words)\b[^)\n]*\)?|\b\d{1,2}\s*[-–]\s*\d{1,2}\s*min\b|\bwith timer\b",
    re.IGNORECASE)
TASK_END = re.compile(r"write as much as you can and in complete sentences\.?", re.IGNORECASE)
VERSION = re.compile(r"\s*(attempt|attemp)\s*(\d)|\s*\b(corrected|coreected|checked)\b.*$", re.IGNORECASE)
NOISE = re.compile(r"\b(acad(em)?|disc|informal|email|find the mistakes)\b", re.IGNORECASE)


def paragraphs(document: ElementTree.Element) -> list[str]:
    out = []
    for p in document.iter(W + "p"):
        parts = []
        for node in p.iter():
            if node.tag == W + "del":
                continue
            if node.tag == W + "t" and not any(a.tag == W + "del" for a in []):
                parts.append(node.text or "")
            elif node.tag == W + "tab":
                parts.append("\t")
        out.append("".join(parts))
    return out


def comments(archive: zipfile.ZipFile, document: ElementTree.Element) -> list[dict]:
    if "word/comments.xml" not in archive.namelist():
        return []
    notes = {}
    for c in ElementTree.fromstring(archive.read("word/comments.xml")).iter(W + "comment"):
        notes[c.get(W + "id")] = " ".join(t.text or "" for t in c.iter(W + "t")).strip()
    anchored, open_ids = {}, set()
    for node in document.iter():
        if node.tag == W + "commentRangeStart":
            open_ids.add(node.get(W + "id"))
            anchored.setdefault(node.get(W + "id"), "")
        elif node.tag == W + "commentRangeEnd":
            open_ids.discard(node.get(W + "id"))
        elif node.tag == W + "t":
            for cid in open_ids:
                anchored[cid] += node.text or ""
    return [
        {"quote": anchored.get(cid, "").strip(), "comment": text, "correction": None, "category": None, "subcategory": None}
        for cid, text in notes.items()
        if text
    ]


def read(path: Path) -> tuple[str, list[dict]]:
    with zipfile.ZipFile(path) as archive:
        document = ElementTree.fromstring(archive.read("word/document.xml"))
        text = "\n".join(p.strip() for p in paragraphs(document) if p.strip())
        return text, comments(archive, document)


def split_task(text: str) -> tuple[str | None, str]:
    match = TASK_END.search(text)
    if not match:
        return None, text
    task, response = text[: match.end()].strip(), text[match.end():].strip()
    response = re.sub(r"^(To:.*\n)?(Subject:.*\n)?", "", response)
    return task, response


def close(a: str, b: str) -> bool:
    """Levenshtein distance of at most 2: misspelt surnames are masked too."""
    a, b = a.lower(), b.lower()
    if abs(len(a) - len(b)) > 2:
        return False
    row = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        prev, row[0] = row[0], i
        for j, cb in enumerate(b, 1):
            prev, row[j] = row[j], min(row[j] + 1, row[j - 1] + 1, prev + (ca != cb))
    return row[-1] <= 2


def mask(text: str, hints: list[str]) -> str:
    text = EMAIL.sub("[EMAIL]", text)
    text = PHONE.sub("[PHONE]", text)
    for index, hint in enumerate(hints):
        fake = FICTIONAL[index % len(FICTIONAL)]
        parts = hint.split()
        text = re.sub(re.escape(hint), fake, text, flags=re.IGNORECASE)
        for part, fake_part in zip(parts, fake.split()):
            if len(part) > 2:
                text = re.sub(r"\b" + re.escape(part) + r"\b", fake_part, text, flags=re.IGNORECASE)
            if len(part) > 5:
                text = re.sub(r"\b\w{4,}\b", lambda m, p=part, f=fake_part: f if close(m.group(), p) else m.group(), text)
    return TIMING.sub("", text).strip()


def topic_and_version(path: Path) -> tuple[str, str]:
    stem = path.stem.strip()
    match = VERSION.search(stem)
    topic = stem if not match else stem[: match.start()]
    topic = re.sub(r"\s+", " ", NOISE.sub("", topic)).strip(" -_").lower()
    if not match:
        return topic, "1"
    if match.group(2):
        return topic, match.group(2)
    return topic, "CORRECTED"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--emails", type=Path, required=True)
    parser.add_argument("--discussions", type=Path, required=True)
    parser.add_argument("--hint", action="append", default=[], help="a real name to mask; repeat")
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    cases, sheet = [], []
    for task_type, folder, prefix in (
        ("WRITE_EMAIL", args.emails, "we"),
        ("WRITE_ACADEMIC_DISCUSSION", args.discussions, "wd"),
    ):
        drafts: dict[str, dict] = {}
        for path in sorted(folder.glob("*.docx")):
            topic, version = topic_and_version(path)
            text, notes = read(path)
            task, response = split_task(text)
            entry = drafts.setdefault(topic, {"versions": {}, "task": None, "errors": []})
            if task and not entry["task"]:
                entry["task"] = mask(task, args.hint)
            if version == "CORRECTED":
                entry["errors"] += [
                    {**n, "quote": mask(n["quote"], args.hint), "comment": mask(n["comment"], args.hint)} for n in notes
                ]
            else:
                entry["versions"][version] = mask(response, args.hint)
                entry["errors"] += [
                    {**n, "quote": mask(n["quote"], args.hint), "comment": mask(n["comment"], args.hint)} for n in notes
                ]
        for n, (topic, entry) in enumerate(sorted(drafts.items()), start=1):
            for version, response in sorted(entry["versions"].items()):
                case_id = f"{prefix}-{n:02d}-v{version}"
                errors = [e for e in entry["errors"] if e["quote"] and e["quote"] in response] if version == "1" else []
                cases.append({
                    "id": case_id,
                    "taskType": task_type,
                    "topic": topic,
                    "taskPrompt": entry["task"],
                    "version": int(version),
                    "text": response,
                    "tutorErrors": errors,
                    "goldScores": {},
                    "split": "eval",
                })
                words = len(re.findall(r"[\w'’-]+", response))
                sheet.append([case_id, task_type, topic, version, words, "", response[:160].replace("\n", " ")])
    with (args.out / "drafts.jsonl").open("w", encoding="utf-8") as out:
        for case in cases:
            out.write(json.dumps(case, ensure_ascii=False) + "\n")
    with (args.out / "scoring-sheet.csv").open("w", encoding="utf-8", newline="") as out:
        writer = csv.writer(out)
        writer.writerow(["id", "taskType", "topic", "version", "words", "raterA (0-5)", "beginning"])
        writer.writerows(sheet)
    digest = hashlib.sha256((args.out / "drafts.jsonl").read_bytes()).hexdigest()
    print(f"{len(cases)} cases, drafts.jsonl sha256 {digest}", file=sys.stderr)
    print("Check by hand: names, cities, schools, employers, dates and personal details (docs/06 §7.2 step 4).",
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
