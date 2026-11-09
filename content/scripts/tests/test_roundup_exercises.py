"""Round-Up exercises, keys and rule cards on synthetic data (no book text, no LLM calls)."""

from toefly_seed.common import llm, schema
from toefly_seed.common.envelope import Report
from toefly_seed.sources import roundup_exercises as rx

REF = {"framework": "ROUND_UP_5", "unitNo": 1, "topicTitle": "Present Forms"}
TOPICS = rx.topics_for([1])


def build(exercises, keys, report=None):
    return rx.build_sets(exercises=exercises, keys=keys, topics=TOPICS, unit_ref=REF, slug_base="u01",
                         topic_title="Present Forms", role="EXERCISE", report=report or Report(),
                         locator={"pages": [4, 13]}, source_code="new-roundup-5")


def test_gaps_with_alternatives_and_choices_pass_the_schema():
    exercises = [{"number": "5", "instruction": "Complete.", "topic": "RU01.PRESENT_SIMPLE", "items": [
        {"n": 1, "text": "The society ___ (need) your help.", "blanks": 1},
        {"n": 2, "text": "It ___ rain.", "blanks": 1},
        {"n": 3, "text": "She ___ every day.", "options": ["swims", "is swimming", "has swum"], "blanks": 1},
        {"n": 4, "text": "Tell me about ___.", "blanks": 1}]}]
    keys = {"5": {1: "needs", 2: "could / may / might", 3: "A", 4: "SUGGESTED: your day"}}
    report = Report()

    [env] = build(exercises, keys, report)

    first, second, third = env["itemSet"]["payload"]["items"]
    assert first["answerKey"] == "needs" and first["hint"] == "need"
    assert second["answerKey"] == "could" and second["acceptedAnswers"] == ["may", "might"]
    assert third["exerciseType"] == "MULTIPLE_CHOICE" and third["answerKey"] == "A"
    assert env["_role"] == "EXERCISE" and env["extraction"]["reviewed"] is False
    assert any("1 of 4 items skipped" in w for w in report.warnings)
    env.pop("_role")
    assert schema.validate_envelope(env) == []


def test_an_exercise_with_a_topic_of_another_unit_or_without_a_key_is_left_out():
    report = Report()
    exercises = [{"number": "1", "topic": "RU07.WISH_PAST", "items": [{"n": 1, "text": "A ___.", "blanks": 1}]},
                 {"number": "2", "topic": "RU01.STATIVE_VERBS", "items": [{"n": 1, "text": "A ___.", "blanks": 1}]}]

    assert build(exercises, {"1": {1: "x"}}, report) == []
    assert any("not one of the unit" in w for w in report.warnings)
    assert any("no key in the Teacher's Book" in w for w in report.warnings)


def test_a_key_section_continued_on_the_next_page_keeps_its_address(monkeypatch):
    pages = iter([
        {"sections": [{"kind": "UNIT", "number": 1, "exercises": {"5": [{"n": 1, "answer": "needs"}]}}]},
        {"sections": [{"continued": True, "number": None, "exercises": {"5": [{"n": 2, "answer": "has"}]}},
                      {"kind": "QUIZ", "number": 1, "exercises": {"1": [{"n": 1, "answer": "C"}]}}]},
    ])
    monkeypatch.setattr(rx, "render", lambda pdf, page: b"")
    monkeypatch.setattr(llm, "ask_json", lambda prompt, image=None: next(pages))

    keys = rx.read_keys(None, 1, 2)

    assert keys == {("UNIT", 1, "5"): {1: "needs", 2: "has"}, ("QUIZ", 1, "1"): {1: "C"}}


def test_a_rule_card_that_copies_the_book_is_regenerated_then_dropped(monkeypatch):
    source = "the present simple is used for permanent states and habits that happen every single day " * 3
    copied = {"sections": [{"heading": "Use", "rules": [source], "examples": [], "commonMistakes": []}]}
    monkeypatch.setattr(llm, "ask_json", lambda prompt, image=None: copied)
    report = Report()

    assert rx.rule_card("Present Forms", source, report, "u01") is None
    assert sum("shares" in w for w in report.warnings) == 3


def test_an_own_words_rule_card_is_kept(monkeypatch):
    source = "the present simple is used for permanent states and habits that happen every single day " * 3
    own = {"sections": [{"heading": "Habits", "rules": ["Use it for routines."], "examples": ["I cycle to work."],
                         "commonMistakes": ["He go -> He goes"]}]}
    monkeypatch.setattr(llm, "ask_json", lambda prompt, image=None: own)

    card = rx.rule_card("Present Forms", source, Report(), "u01")

    assert card == {**own, "generatedBy": "SUMMARY_OF_SOURCE"}


def test_a_whole_sentence_as_the_key_is_an_open_answer():
    assert rx.build_item({"n": 1, "text": "never ___", "blanks": 1}, "She has never read it before.", "T") is None
    assert rx.build_item({"n": 2, "text": "B: ___ (be there / twice).", "blanks": 1},
                         "B: I have been there twice.", "T") is None
    assert rx.build_item({"n": 3, "text": "She ___ to school.", "blanks": 1}, "has never been", "T") is not None
