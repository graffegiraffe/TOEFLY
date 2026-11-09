"""Parsing of model answers: a stray closing bracket is dropped, values are never changed."""

import email.message
import io
import urllib.error

import pytest

from toefly_seed.common import llm


def test_valid_json_in_a_code_fence_is_read_as_is():
    assert llm._json_from('```json\n{"a": [1, 2]}\n```') == {"a": [1, 2]}


def test_a_stray_closing_bracket_at_the_end_is_dropped():
    text = '```json\n{"exercises":[{"letter":"A","items":[{"n":1,"text":"x ] }"}]}]}]\n```'
    assert llm._json_from(text) == {"exercises": [{"letter": "A", "items": [{"n": 1, "text": "x ] }"}]}]}


def test_brackets_inside_strings_and_escaped_quotes_are_kept():
    assert llm._balanced('{"a":"say \\"]\\" now"]}') == '{"a":"say \\"]\\" now"}'


def test_text_without_json_still_fails():
    with pytest.raises(Exception):
        llm._json_from("no json here")


class _Quota(urllib.error.HTTPError):
    def __init__(self, retry_after: str):
        super().__init__("u", 429, "Too Many Requests", email.message.Message(), io.BytesIO(b"rate limit"))
        self.headers["retry-after"] = retry_after


@pytest.fixture
def providers(monkeypatch, tmp_path):
    keys = {"GROQ_API_KEY": "g", "GEMINI_API_KEY": "m", "TOEFLY_LLM_PROVIDERS": "groq,gemini,openrouter"}
    monkeypatch.setattr(llm, "_env", lambda name: keys.get(name, ""))
    monkeypatch.setattr(llm, "CACHE", tmp_path)
    monkeypatch.setattr(llm, "_resting", {})
    monkeypatch.setattr(llm.time, "sleep", lambda s: None)
    return keys


def test_only_providers_with_a_key_and_a_model_are_used(providers):
    assert [e.provider.name for e in llm.endpoints()] == ["groq", "gemini"]  # openrouter needs OPENROUTER_MODEL


def test_a_provider_out_of_quota_rests_and_the_next_one_answers(providers, monkeypatch):
    asked = []

    def post(endpoint, prompt, image, max_tokens):
        asked.append(endpoint.provider.name)
        if endpoint.provider.name == "groq":
            raise _Quota("600")
        return '{"ok": true}'

    monkeypatch.setattr(llm, "_post", post)

    assert llm.ask_json("p", b"img") == {"ok": True}
    assert llm.ask_json("q", b"img") == {"ok": True}
    assert asked == ["groq", "gemini", "gemini"]  # groq is not asked again while it rests


def test_an_answer_cached_by_another_provider_is_reused(providers, monkeypatch):
    monkeypatch.setattr(llm, "_post", lambda endpoint, *a: "from gemini")
    providers["TOEFLY_LLM_PROVIDERS"] = "gemini"
    assert llm.ask("p") == "from gemini"

    providers["TOEFLY_LLM_PROVIDERS"] = "groq,gemini"
    monkeypatch.setattr(llm, "_post", lambda *a: pytest.fail("the cache should answer"))
    assert llm.ask("p") == "from gemini"


def test_a_provider_that_holds_the_request_open_is_set_aside(providers, monkeypatch):
    asked = []

    def post(endpoint, prompt, image, max_tokens):
        asked.append(endpoint.provider.name)
        if endpoint.provider.name == "groq":
            raise TimeoutError("The read operation timed out")
        return "ok"

    providers["TOEFLY_LLM_PROVIDERS"] = "groq,gemini"
    monkeypatch.setattr(llm, "_post", post)

    assert llm.ask("p") == "ok"
    assert asked == ["groq", "groq", "groq", "gemini"]
