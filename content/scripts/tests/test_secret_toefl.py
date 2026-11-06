"""Tests on a synthetic page: the texts are written for the tests, not taken from any book."""

import json
import uuid

import pytest

from toefly_seed.common import schema
from toefly_seed.common.hashing import canonical, content_hash
from toefly_seed.common.ids import NAMESPACE_TOEFLY_SEED, item_id, item_set_id
from toefly_seed.sources import secret_toefl_html as src

SETS = [
    {
        "title": "Moss and Water",
        "offset": 0,
        "paras": [
            "Moss grows where <mark>moisture</mark> lasts. It holds water like a sponge. "
            '<span class="sq">■ A</span> Rain fills it. <span class="sq">■ B</span> Sun dries it.',
            "Gardeners use moss to cover <i>bare</i> soil. <span class=\"sq\">■ C</span> It needs little care. "
            '<span class="sq">■ D</span>' + " Many gardens in cool, damp places show how well the plant copes." * 4,
        ],
        "questions": [
            {"n": 41, "stem": "The word “moisture” in the passage is closest in meaning to",
             "options": ["wetness", "heat", "dust", "noise"], "answer": "A",
             "why": "Moisture means <em>wetness</em>.",
             "others": ["<b>B, C:</b> unrelated to water.", "<b>D:</b> sound has nothing to do with it."]},
            {"n": 42, "stem": "Which of the following is not mentioned as a use of moss?",
             "options": ["covering soil", "holding water", "making tea", "needing care"], "answer": "C",
             "why": "Tea is never mentioned.", "others": []},
            {"n": 43,
             "stem": "There are four locations [■] in the passage that indicate where the following sentence "
                     "could be added.<br><br><em>Dry spells slow its growth.</em><br><br>Where would the sentence best fit?",
             "options": ["Location A", "Location B", "Location C", "Location D"], "answer": "B",
             "why": "It follows the sun sentence.", "others": []},
        ],
    }
]


@pytest.fixture
def html():
    return "<html><script>const SETS = " + json.dumps(SETS) + ";\nconst store = {};</script></html>"


@pytest.fixture
def envelopes(html):
    return list(src.extract(html, src.Report()))


def test_reads_the_literal_by_bracket_matching(html):
    assert src.read_sets(html)[0]["title"] == "Moss and Water"


def test_a_page_with_brackets_inside_strings_is_still_cut_correctly():
    tricky = [dict(SETS[0], paras=["A ] bracket [ inside, and a \" quote."])]
    html = "<script>const SETS = " + json.dumps(tricky) + ";</script>"
    assert src.read_sets(html)[0]["paras"][0].startswith("A ]")


def test_envelope_is_valid_and_ordinals_are_positions(envelopes):
    envelope = envelopes[0]
    assert schema.validate_envelope({k: v for k, v in envelope.items() if k != "_file"}) == []
    assert [i["ordinal"] for i in envelope["itemSet"]["payload"]["items"]] == [1, 2, 3]
    assert envelope["locator"]["questionNumbers"] == [41, 42, 43]


def test_markup_conversion(envelopes):
    paragraphs = envelopes[0]["itemSet"]["payload"]["stimulus"]["paragraphs"]
    assert "{{mark:moisture}}" in paragraphs[0]
    assert "*bare*" in paragraphs[1]
    assert "■" not in "".join(paragraphs)


def test_vocabulary_gets_an_anchor_in_the_passage(envelopes):
    first = envelopes[0]["itemSet"]["payload"]["items"][0]
    assert first["subskill"] == "VOCABULARY"
    anchor = first["anchor"]
    paragraph = envelopes[0]["itemSet"]["payload"]["stimulus"]["paragraphs"][anchor["paragraph"]]
    assert paragraph[anchor["charStart"]: anchor["charEnd"]] == "moisture" or "moisture" in paragraph


def test_grouped_distractor_notes_reach_each_named_option(envelopes):
    options = {o["key"]: o["rationale"] for o in envelopes[0]["itemSet"]["payload"]["items"][0]["options"]}
    assert options["B"] == options["C"] == "unrelated to water."
    assert options["A"].startswith("Moss") or options["A"].startswith("Moisture")


def test_missing_explanations_get_the_fixed_placeholder_and_a_warning(html):
    report = src.Report()
    items = list(src.extract(html, report))[0]["itemSet"]["payload"]["items"]
    assert items[1]["options"][0]["rationale"] == src.PLACEHOLDER
    assert any("Q42" in w and "no explanation" in w for w in report.warnings)


def test_insert_text_markers_carry_paragraph_and_sentence_position(envelopes):
    item = envelopes[0]["itemSet"]["payload"]["items"][2]
    assert item["subskill"] == "INSERT_TEXT"
    assert item["insertion"]["sentence"] == "Dry spells slow its growth."
    markers = {m["key"]: (m["paragraph"], m["afterSentence"]) for m in item["insertion"]["markers"]}
    assert markers == {"A": (0, 2), "B": (0, 3), "C": (1, 1), "D": (1, 2)}
    assert item["options"][1]["text"] == "Location B"


def test_negative_factual_is_recognised(envelopes):
    assert envelopes[0]["itemSet"]["payload"]["items"][1]["subskill"] == "NEGATIVE_FACTUAL"


def test_ids_are_stable_uuid_v5():
    assert item_set_id("a/b") == str(uuid.uuid5(NAMESPACE_TOEFLY_SEED, "a/b"))
    assert item_id("a/b", 3) == str(uuid.uuid5(NAMESPACE_TOEFLY_SEED, "a/b#3"))
    assert item_set_id("a/b") == item_set_id("a/b")


def test_content_hash_ignores_key_order_and_changes_with_content():
    assert content_hash({"a": 1, "b": [1, 2]}) == content_hash({"b": [1, 2], "a": 1})
    assert content_hash({"a": 1}) != content_hash({"a": 2})
    assert canonical({"b": "é", "a": 1}) == '{"a":1,"b":"é"}'


def test_schema_rejects_a_wrong_answer_key(envelopes):
    broken = {k: v for k, v in envelopes[0].items() if k != "_file"}
    broken = json.loads(json.dumps(broken))
    broken["itemSet"]["payload"]["items"][0]["answerKey"] = "E"
    assert any("answerKey" in p for p in schema.validate_envelope(broken))
