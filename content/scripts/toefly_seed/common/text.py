"""Normalisation and the small HTML-to-markup conversion of the HTML sources."""

import re
import unicodedata

_TAGS = [
    (re.compile(r"<mark>(.*?)</mark>", re.S), r"{{mark:\1}}"),
    (re.compile(r"<(?:i|em)>(.*?)</(?:i|em)>", re.S), r"*\1*"),
    (re.compile(r"<(?:b|strong)>(.*?)</(?:b|strong)>", re.S), r"**\1**"),
]
_BREAK = re.compile(r"<br\s*/?>")
_ANY_TAG = re.compile(r"<[^>]+>")


def normalise(text: str) -> str:
    text = unicodedata.normalize("NFC", text)
    text = text.replace("­", "").replace("​", "").replace("ﬁ", "fi").replace("ﬂ", "fl")
    return re.sub(r"[ \t]+", " ", text).strip()


def html_to_markup(html: str) -> str:
    """<mark> becomes {{mark:..}}, <i>/<em> become *..*, <b> becomes **..**, other tags are dropped."""
    for pattern, replacement in _TAGS:
        html = pattern.sub(replacement, html)
    html = _BREAK.sub("\n", html)
    return normalise(_ANY_TAG.sub("", html))


def plain(markup: str) -> str:
    """Markup without its markers."""
    text = re.sub(r"\{\{mark:(.*?)\}\}", r"\1", markup)
    text = re.sub(r"\*\*(.*?)\*\*", r"\1", text)
    return re.sub(r"\*(.*?)\*", r"\1", text)


def word_count(text: str) -> int:
    return len(re.findall(r"[A-Za-z0-9][A-Za-z0-9'’-]*", text))
