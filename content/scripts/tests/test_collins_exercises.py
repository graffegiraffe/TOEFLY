"""Collins exercises: shaping of model output into payloads, on synthetic sentences (no book text, no LLM calls)."""

from toefly_seed.common import llm, schema
from toefly_seed.common.envelope import Report
from toefly_seed.sources import collins_exercises as ce

VOCAB_REF = {"framework": "COLLINS_VOCAB", "unitNo": 3, "theme": "ARTS"}
GRAMMAR_REF = {"framework": "COLLINS_GRAMMAR", "unitNo": 6, "topicTitle": "Conditional sentences"}


def build(task_type, unit_ref, exercises, keys, report=None):
    return ce.build_sets(source_code="collins-vocab-grammar-2013", task_type=task_type, unit_ref=unit_ref,
                         slug_base="u-test", topic="Test topic", exercises=exercises, keys=keys,
                         report=report or Report(), locator={"pages": [1, 1]})


def test_single_blank_items_with_a_key_become_gap_fills_that_pass_the_schema():
    exercises = [{"letter": "B", "instruction": "Complete the sentences.", "wordBank": ["tide", "sand"],
                  "items": [{"n": 1, "text": "The ___ rose.", "options": None, "blanks": 1},
                            {"n": 2, "text": "The ___ and the ___.", "options": None, "blanks": 2},
                            {"n": 3, "text": "Wet ___.", "options": None, "blanks": 1}]}]
    report = Report()
    [env] = build("VOCAB_EXERCISE", VOCAB_REF, exercises, {"B": {1: "tide", 2: "a | b", 3: "sand"}}, report)

    items = env["itemSet"]["payload"]["items"]
    assert [i["answerKey"] for i in items] == ["tide", "sand"]
    assert [i["ordinal"] for i in items] == [1, 2]
    assert env["extraction"] == {**env["extraction"], "method": "OCR_LLM", "reviewed": False}
    assert any("1 of 3 items skipped" in w for w in env["extraction"]["warnings"])
    assert schema.validate_envelope(env) == []


def test_grammar_choices_become_multiple_choice_with_the_cg_topic_and_placeholders():
    exercises = [{"letter": "A", "instruction": "Choose.", "items": [
        {"n": 1, "text": "If it ___, we stay.", "options": ["rains", "rained", "will rain"], "blanks": 1},
        {"n": 2, "text": "I ___ go.", "options": ["would", "will"], "blanks": 1}]}]
    [env] = build("GRAMMAR_EXERCISE", GRAMMAR_REF, exercises, {"A": {1: "a", 2: "would"}})

    first, second = env["itemSet"]["payload"]["items"]
    assert first["exerciseType"] == "MULTIPLE_CHOICE" and first["answerKey"] == "A"
    assert first["grammarTopic"] == "CG06.CONDITIONALS"
    assert {o["rationale"] for o in first["options"]} == {ce.PLACEHOLDER}
    assert second["exerciseType"] == "GAP_FILL" and second["answerKey"] == "would" and second["hint"] == "would / will"
    assert schema.validate_envelope(env) == []


def test_a_key_that_names_no_option_is_not_guessed():
    exercises = [{"letter": "A", "instruction": "Choose.", "items": [
        {"n": 1, "text": "It ___.", "options": ["is", "are", "be"], "blanks": 1}]}]
    report = Report()

    assert build("GRAMMAR_EXERCISE", GRAMMAR_REF, exercises, {"A": {1: "was"}}, report) == []
    assert any("names none of the options" in w for w in report.warnings)


def test_an_exercise_without_a_key_is_left_out_with_a_warning():
    exercises = [{"letter": "C", "instruction": "Fill in.", "items": [{"n": 1, "text": "A ___.", "blanks": 1}]}]
    report = Report()

    assert build("VOCAB_EXERCISE", VOCAB_REF, exercises, {}, report) == []
    assert any("no key found" in w for w in report.warnings)


def test_keys_are_grouped_by_part_unit_and_letter(monkeypatch):
    pages = iter([
        {"part": "VOCABULARY", "units": [{"unit": 1, "exercises": {"a": [{"n": 1, "answer": "x"}]}}]},
        {"part": None, "units": [{"unit": 2, "exercises": {"B": [{"n": 2, "answer": "y"}, {"bad": 1}]}}]},
        {"part": "GRAMMAR", "units": [{"unit": 1, "exercises": {"A": [{"n": 3, "answer": "z"}]}}]},
    ])
    monkeypatch.setattr(ce, "render", lambda pdf, page: b"")
    monkeypatch.setattr(llm, "ask_json", lambda prompt, image: next(pages))

    keys = ce.read_keys(None, 1, 3)

    assert keys == {("VOCABULARY", 1, "A"): {1: "x"}, ("VOCABULARY", 2, "B"): {2: "y"}, ("GRAMMAR", 1, "A"): {3: "z"}}


def test_the_grammar_key_starts_where_the_unit_numbers_start_again(monkeypatch):
    pages = iter([
        {"part": "VOCABULARY", "units": [{"unit": 19, "exercises": {"A": [{"n": 1, "answer": "v19"}]}}]},
        {"part": None, "units": [{"unit": 20, "exercises": {"A": [{"n": 1, "answer": "v20"}]}},
                                 {"unit": 1, "exercises": {"A": [{"n": 1, "answer": "g1"}]}}]},
        {"part": None, "units": [{"unit": 2, "exercises": {"A": [{"n": 1, "answer": "g2"}]}}]},
    ])
    monkeypatch.setattr(ce, "render", lambda pdf, page: b"")
    monkeypatch.setattr(llm, "ask_json", lambda prompt, image: next(pages))

    keys = ce.read_keys(None, 1, 3)

    assert keys == {("VOCABULARY", 19, "A"): {1: "v19"}, ("VOCABULARY", 20, "A"): {1: "v20"},
                    ("GRAMMAR", 1, "A"): {1: "g1"}, ("GRAMMAR", 2, "A"): {1: "g2"}}
