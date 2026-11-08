"""OpenAI-compatible chat client for the offline scripts, with an on-disk cache (docs/08-content-pipeline.md §4.1, §5.2).

Calls go straight to the provider: ``ai-gateway`` does not exist at this stage and the scripts only send public book
pages. The key is read from ``infra/compose/.env`` (``GROQ_API_KEY``) and never printed. Responses are cached by
``sha256(image + prompt + model)``, so a second run costs nothing and returns the same text.
"""

import base64
import hashlib
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ENV = Path(__file__).resolve().parents[4] / "infra" / "compose" / ".env"
CACHE = Path(__file__).resolve().parents[3] / "seed" / "_cache" / "llm"
URL = "https://api.groq.com/openai/v1/chat/completions"
MODEL = "qwen/qwen3.8-27b"


def _key() -> str:
    key = os.environ.get("GROQ_API_KEY", "")
    if not key and ENV.exists():
        for line in ENV.read_text().splitlines():
            if line.startswith("GROQ_API_KEY="):
                key = line.split("=", 1)[1].strip()
    if not key:
        raise RuntimeError("GROQ_API_KEY is not set (infra/compose/.env)")
    return key


def ask(prompt: str, image: bytes | None = None, *, model: str = MODEL, max_tokens: int = 4000) -> str:
    """One deterministic (temperature 0) request; an identical request is answered from the cache."""
    digest = hashlib.sha256((model + "\0" + prompt).encode() + (b"\0" + image if image else b"")).hexdigest()
    cached = CACHE / f"{digest}.json"
    if cached.exists():
        return json.loads(cached.read_text())["text"]
    content: list[dict] = [{"type": "text", "text": prompt}]
    if image:
        content.append({"type": "image_url",
                        "image_url": {"url": "data:image/png;base64," + base64.b64encode(image).decode()}})
    body = json.dumps({"model": model, "temperature": 0, "max_tokens": max_tokens,
                       "messages": [{"role": "user", "content": content}]}).encode()
    attempts = 12  # the free tier allows 8000 tokens a minute: one page image may need a full minute of budget
    for attempt in range(attempts):
        request = urllib.request.Request(URL, body, {"Authorization": f"Bearer {_key()}",
                                                     "Content-Type": "application/json",
                                                     "User-Agent": "toefly-seed/0.1"})
        try:
            with urllib.request.urlopen(request, timeout=180) as response:
                text = json.load(response)["choices"][0]["message"]["content"]
            break
        except urllib.error.HTTPError as error:
            if error.code in (429, 500, 502, 503) and attempt < attempts - 1:
                wait = max(float(error.headers.get("retry-after") or 0), min(60.0, 5.0 * (attempt + 1)))
                detail = error.read().decode(errors="replace")[:300]
                print(f"llm: HTTP {error.code}, waiting {wait:.0f}s: {detail}", file=sys.stderr, flush=True)
                time.sleep(wait)
                continue
            raise
    CACHE.mkdir(parents=True, exist_ok=True)
    cached.write_text(json.dumps({"model": model, "text": text}, ensure_ascii=False))
    return text


def _json_from(text: str):
    text = text.strip()
    if text.startswith("```"):
        text = text.split("\n", 1)[1].rsplit("```", 1)[0]
    start = text.find("{")
    try:
        return json.loads(text[start : text.rfind("}") + 1], strict=False)
    except json.JSONDecodeError:
        return json.loads(_balanced(text[start:]), strict=False)


def _balanced(text: str) -> str:
    """Drops closing brackets that close nothing open and closes what is left open; strings are kept as they are.

    The vision model sometimes ends an answer with ``]}]`` for ``]}``; no value is changed by this repair.
    """
    out, stack, in_string, escaped = [], [], False, False
    pairs = {"}": "{", "]": "["}
    for ch in text:
        if in_string:
            out.append(ch)
            in_string = not (ch == '"' and not escaped)
            escaped = ch == "\\" and not escaped
            continue
        if ch == '"':
            in_string = True
        elif ch in "{[":
            stack.append(ch)
        elif ch in "}]":
            if not stack or stack[-1] != pairs[ch]:
                continue
            stack.pop()
            out.append(ch)
            if not stack:
                break
            continue
        out.append(ch)
    out.extend("}" if c == "{" else "]" for c in reversed(stack))
    return "".join(out)


def ask_json(prompt: str, image: bytes | None = None, **kwargs):
    """The answer must be a JSON document; a malformed answer is asked again with a stricter reminder."""
    reminders = ["", "\n\nIMPORTANT: the output must be strictly valid JSON, no trailing commas, no comments.",
                 "\n\nIMPORTANT: output strictly valid JSON only; escape every double quote inside strings."]
    last: Exception | None = None
    for reminder in reminders:
        try:
            return _json_from(ask(prompt + reminder, image, **kwargs))
        except json.JSONDecodeError as error:
            last = error
    raise last  # type: ignore[misc]
