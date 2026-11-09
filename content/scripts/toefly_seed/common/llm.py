"""OpenAI-compatible chat client for the offline scripts, with an on-disk cache (docs/08-content-pipeline.md §4.1, §5.2).

Calls go straight to the providers: ``ai-gateway`` does not exist at this stage and the scripts only send public book
pages. Every provider whose key is set in ``infra/compose/.env`` is used, in the order of ``TOEFLY_LLM_PROVIDERS``
(default: groq, gemini, mistral, openrouter); when one runs out of its free quota the request moves to the next one
and the first is left alone until its wait is over. Keys are never printed. Responses are cached by
``sha256(model + prompt + image)``; an answer cached by any configured model is reused, so a run resumes for free.
"""

import base64
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

ENV = Path(__file__).resolve().parents[4] / "infra" / "compose" / ".env"
CACHE = Path(__file__).resolve().parents[3] / "seed" / "_cache" / "llm"
SWITCH_AFTER = 20.0  # a provider that asks to wait longer than this is set aside and the next one is asked


@dataclass(frozen=True)
class Provider:
    name: str
    url: str
    key_var: str
    model: str | None  # comma-separated models, tried in order; None: they must be named in <NAME>_MODEL
    extra: dict = field(default_factory=dict)


PROVIDERS = {
    "groq": Provider("groq", "https://api.groq.com/openai/v1/chat/completions", "GROQ_API_KEY", "qwen/qwen3.8-27b"),
    "gemini": Provider("gemini", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                       "GEMINI_API_KEY",
                       # the free tier allows 20 requests a day for each model, so several models are used
                       "gemini-3.5-flash,gemini-3.8-flash,gemini-3.7-flash,gemini-3.6-flash,gemini-3.5-flash-lite",
                       {"reasoning_effort": "low", "max_tokens": 16000}),
    "mistral": Provider("mistral", "https://api.mistral.ai/v1/chat/completions", "MISTRAL_API_KEY",
                        "mistral-small-latest"),
    "openrouter": Provider("openrouter", "https://openrouter.ai/api/v1/chat/completions", "OPENROUTER_API_KEY", None),
}
_resting: dict[str, float] = {}  # provider name -> time.time() when it may be asked again


def _env(name: str) -> str:
    value = os.environ.get(name, "")
    if not value and ENV.exists():
        for line in ENV.read_text().splitlines():
            if line.startswith(name + "="):
                value = line.split("=", 1)[1].strip()
    return value


@dataclass(frozen=True)
class Endpoint:
    provider: Provider
    model: str
    key: str

    @property
    def name(self) -> str:
        return f"{self.provider.name}/{self.model}"


def endpoints() -> list[Endpoint]:
    """The configured providers in order of preference; one without a key (or without a model) is left out."""
    names = [n.strip() for n in (_env("TOEFLY_LLM_PROVIDERS") or ",".join(PROVIDERS)).split(",") if n.strip()]
    out = []
    for name in names:
        provider = PROVIDERS.get(name)
        if provider is None:
            raise RuntimeError(f"unknown LLM provider {name!r}; known: {', '.join(PROVIDERS)}")
        key, models = _env(provider.key_var), _env(f"{name.upper()}_MODEL") or provider.model or ""
        if key:
            out += [Endpoint(provider, m.strip(), key) for m in models.split(",") if m.strip()]
    if not out:
        raise RuntimeError("no LLM key is set in infra/compose/.env (GROQ_API_KEY, GEMINI_API_KEY, ...)")
    return out


def _cache_file(model: str, prompt: str, image: bytes | None) -> Path:
    digest = hashlib.sha256((model + "\0" + prompt).encode() + (b"\0" + image if image else b"")).hexdigest()
    return CACHE / f"{digest}.json"


def _post(endpoint: Endpoint, prompt: str, image: bytes | None, max_tokens: int) -> str:
    content: list[dict] = [{"type": "text", "text": prompt}]
    if image:
        content.append({"type": "image_url",
                        "image_url": {"url": "data:image/png;base64," + base64.b64encode(image).decode()}})
    body = json.dumps({"model": endpoint.model, "temperature": 0, "max_tokens": max_tokens,
                       "messages": [{"role": "user", "content": content}], **endpoint.provider.extra}).encode()
    request = urllib.request.Request(endpoint.provider.url, body, {"Authorization": f"Bearer {endpoint.key}",
                                                                   "Content-Type": "application/json",
                                                                   "User-Agent": "toefly-seed/0.1"})
    with urllib.request.urlopen(request, timeout=180) as response:
        choice = json.load(response)["choices"][0]
    text = choice.get("message", {}).get("content")
    if not text:  # e.g. a thinking model that spent every token on its reasoning
        raise EmptyAnswer(f"no text in the answer (finish_reason {choice.get('finish_reason')})")
    return text


class EmptyAnswer(Exception):
    pass


def _retry_after(header: str | None, body: str) -> float:
    """Seconds to wait: the Retry-After header, or Google's ``"retryDelay": "59182s"`` in the error body."""
    found = re.search(r'"retryDelay":\s*"(\d+(?:\.\d+)?)s"', body)
    return max(float(header or 0), float(found.group(1)) if found else 0.0)


def ask(prompt: str, image: bytes | None = None, *, max_tokens: int = 4000) -> str:
    """One deterministic (temperature 0) request; an identical request is answered from the cache."""
    configured = endpoints()
    for endpoint in configured:
        cached = _cache_file(endpoint.model, prompt, image)
        if cached.exists():
            return json.loads(cached.read_text())["text"]
    tries: dict[str, int] = {}
    while True:
        now = time.time()
        ready = [e for e in configured if _resting.get(e.name, 0) <= now]
        if not ready:
            wait = min(_resting[e.name] for e in configured) - now
            print(f"llm: every provider is resting, waiting {wait:.0f}s", file=sys.stderr, flush=True)
            time.sleep(max(wait, 1.0))
            continue
        endpoint = ready[0]
        name = endpoint.name
        try:
            text = _post(endpoint, prompt, image, max_tokens)
            break
        except urllib.error.HTTPError as error:
            if error.code not in (429, 500, 502, 503, 504):
                raise RuntimeError(f"{name} answered HTTP {error.code}: "
                                   f"{error.read().decode(errors='replace')[:300]}") from error
            tries[name] = tries.get(name, 0) + 1
            body = error.read().decode(errors="replace")
            wait = max(_retry_after(error.headers.get("retry-after"), body), min(60.0, 5.0 * tries[name]))
            detail, code = body[:200], error.code
        except (TimeoutError, urllib.error.URLError, EmptyAnswer) as error:  # an overloaded provider may hold the request open
            tries[name] = tries.get(name, 0) + 1
            wait, detail, code = min(60.0, 5.0 * tries[name]), str(error)[:200], type(error).__name__
        if len(configured) > 1 and (wait > SWITCH_AFTER or tries[name] >= 3):
            _resting[name] = time.time() + max(wait, 60.0)
            tries[name] = 0
            print(f"llm: {name} {code}, resting {max(wait, 60.0):.0f}s, asking the next provider: "
                  f"{detail}", file=sys.stderr, flush=True)
            continue
        if tries[name] > 12:
            raise RuntimeError(f"{name} kept failing: {detail}")
        print(f"llm: {name} {code}, waiting {wait:.0f}s: {detail}", file=sys.stderr, flush=True)
        time.sleep(wait)
    CACHE.mkdir(parents=True, exist_ok=True)
    _cache_file(endpoint.model, prompt, image).write_text(
        json.dumps({"provider": endpoint.provider.name, "model": endpoint.model, "text": text}, ensure_ascii=False))
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
