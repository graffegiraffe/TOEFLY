"""Shared parsing of TST Prep books: numbered questions, answer keys and explanations (section 4.4 and 4.5)."""

import re
from dataclasses import dataclass, field

from .text import normalise

QUESTION = re.compile(r"^(\d{3})\.\s+(.*)$")
OPTION = re.compile(r"^\s*([a-d])\.\s+(.*)$")
LETTERS = "ABCD"
PLACEHOLDER = "Explanation not provided by the source."


@dataclass
class Question:
    number: int
    stem: str
    options: list[str] = field(default_factory=list)
    extra: list[str] = field(default_factory=list)  # lines between the stem and the options (quotes, excerpts)


def parse_questions(lines: list[str], number: re.Pattern = QUESTION, stop: re.Pattern | None = None) -> list[Question]:
    """Questions ``101. stem`` followed by options ``a. ...`` (continuation lines are joined)."""
    questions: list[Question] = []
    current: Question | None = None
    target: list[str] | None = None
    for line in lines:
        question = number.match(line.strip())
        option = OPTION.match(line)
        if stop and stop.match(line) and not question:
            current, target = None, None
            continue
        if question:
            current = Question(int(question.group(1)), question.group(2).strip())
            questions.append(current)
            target = None
        elif current is None:
            continue
        elif option and len(current.options) < 4 and option.group(1) == "abcd"[len(current.options)]:
            current.options.append(option.group(2).strip())
            target = None
        elif not line.strip():
            continue
        elif current.options:
            current.options[-1] += " " + line.strip()
        elif current.extra or line.startswith("   ") and False:
            current.extra.append(line.strip())
        else:
            current.extra.append(line.strip())
    for q in questions:
        q.options = [normalise(o) for o in q.options]
        q.stem = normalise(q.stem)
    return questions


_KEY = re.compile(r"^(\d{3})\.\s*([A-D])\b\s*(.*)$")


def parse_key(lines: list[str], pattern: re.Pattern = _KEY) -> dict[int, tuple[str, str]]:
    """``101. D`` followed by the explanation paragraph until the next number."""
    key: dict[int, tuple[str, str]] = {}
    number, letter, body = None, None, []
    for line in lines:
        match = pattern.match(line.strip())
        if match:
            if number is not None:
                key[number] = (letter, normalise(" ".join(body)))
            number, letter, body = int(match.group(1)), match.group(2), [match.group(3)]
        elif number is not None:
            body.append(line.strip())
    if number is not None:
        key[number] = (letter, normalise(" ".join(body)))
    return key


_SENTENCE = re.compile(r"(?:(?<=[.!?][\"”’)])|(?<=[.!?]))\s+(?=[A-Z“\"(])")
_LETTER_REFS = [
    re.compile(r"\bOptions?\s+([A-D])(?:\s*(?:,|and|or)\s*(?:and\s*)?([A-D]))*"),
    re.compile(r"^(?:Distractors?\s+)?([A-D])\b(?!\.)"),
    re.compile(r"\b(?:in|of|to|for|at)\s+(?:position\s+|option\s+)?([A-D])\b(?=[ ,.]| is| does| doesn|$)"),
    re.compile(r"\(([A-D])\)"),
]


def _named(sentence: str, correct: str) -> list[str]:
    found: list[str] = []
    for match in re.finditer(r"\bOptions?\s+((?:[A-D](?:\s*(?:,|and|or)\s*)?)+)", sentence):
        found += re.findall(r"[A-D]", match.group(1))
    if not found:
        lead = re.match(r"^(?:Distractors?\s+)?([A-D])\b(?![.\w])", sentence)
        if lead:
            found.append(lead.group(1))
    if not found:
        ref = re.search(r"\b(?:statement|idea|claim|choice|answer|suggestion|phrase|word)s?\s+(?:in|of)\s+([A-D])\b", sentence)
        if ref:
            found.append(ref.group(1))
    if not found:
        inline = re.findall(r"\b([A-D])\s+(?:is|does|doesn|reverses|confuses|misinterprets|overstates|describes|refers|suggests|mentions|focuses|names|has|would|goes|only|also|requires|adds|states|presents)\b", sentence)
        found += inline
    ordered = []
    for letter in found:
        if letter != correct and letter not in ordered:
            ordered.append(letter)
    return ordered


def split_explanation(text: str, correct: str) -> tuple[str, dict[str, str]]:
    """Section 4.5 step 3: the summary of the correct answer and one rationale for every named distractor."""
    sentences = [s.strip() for s in _SENTENCE.split(text) if s.strip()]
    summary: list[str] = []
    rationales: dict[str, str] = {}
    last: list[str] = []
    for sentence in sentences:
        letters = _named(sentence, correct)
        if letters:
            for letter in letters:
                rationales[letter] = sentence
            last = letters
        elif last:
            for letter in last:
                rationales[letter] += " " + sentence
        else:
            summary.append(sentence)
    return " ".join(summary) or text, rationales
