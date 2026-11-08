"""Parsing of model answers: a stray closing bracket is dropped, values are never changed."""

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
