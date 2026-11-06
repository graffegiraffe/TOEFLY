"""TST parsing helpers on synthetic text (written for the tests)."""

from toefly_seed.common import tst
from toefly_seed.sources import tst_reading_2026 as reading

BODY = """\
101. What colour is the roof?
   a. Red
   b. Blue and
      green
   c. Grey
   d. White
102. Which is not a fruit?
   a. Apple
   b. Pear
   c. Table
   d. Plum
""".splitlines()

KEY = """\
101. B
B is correct because the text says “the roof is blue.” Option A is incorrect since red is never named.
Option C is wrong because grey is a wall colour. Option D is also incorrect as white is the door.
102. C
C is correct because a table is furniture. The other three are fruits.
""".splitlines()


def test_questions_with_wrapped_options():
    questions = tst.parse_questions(BODY)
    assert [q.number for q in questions] == [101, 102]
    assert questions[0].options[1] == "Blue and green"
    assert all(len(q.options) == 4 for q in questions)


def test_key_entries():
    key = tst.parse_key(KEY)
    assert key[101][0] == "B" and key[102][0] == "C"


def test_explanation_goes_to_the_named_distractors_and_keeps_closing_quotes():
    summary, notes = tst.split_explanation(tst.parse_key(KEY)[101][1], "B")
    assert summary.endswith("blue.”")
    assert set(notes) == {"A", "C", "D"}
    assert notes["A"].startswith("Option A is incorrect")


def test_unnamed_distractors_stay_unexplained_instead_of_invented():
    summary, notes = tst.split_explanation(tst.parse_key(KEY)[102][1], "C")
    assert notes == {}
    assert "other three" in summary


def test_subskills():
    assert reading._daily_subskill("What is the main purpose of this email?") == "PURPOSE"
    assert reading._daily_subskill("What can be inferred about the policy?") == "INFERENCE"
    assert reading._academic_subskill("The word “x” in the passage is closest in meaning to") == "VOCABULARY"
    assert reading._academic_subskill("Why does the author mention the sea?") == "RHETORICAL_PURPOSE"


def test_listening_turns_keep_speaker_ids_in_order_of_appearance():
    from toefly_seed.sources import tst_listening_2026 as listening

    turns = listening._turns(["Man: Hello there.", "Woman: Hi. How are", "you today?", "Man: Fine."])
    assert [t["speaker"] for t in turns] == ["A", "B", "A"]
    assert turns[1]["text"] == "Hi. How are you today?"


def test_listening_subskills():
    from toefly_seed.sources import tst_listening_2026 as listening

    assert listening._subskill("What is the main idea of the talk?") == "MAIN_IDEA"
    assert listening._subskill("Why does the man mention the car?") == "FUNCTION"
    assert listening._subskill("What will the woman do next?") == "DETAIL"


def test_questions_stop_at_page_furniture():
    import re

    lines = ["31. Why?", "   a. One", "   b. Two", "   c. Three", "   d. Four", "2.", "Listen to a conversation."]
    q = tst.parse_questions(lines, re.compile(r"^(\d{2,3})\.\s+(.*)$"), re.compile(r"^\s*(?:\d{1,2}\.\s*$|Listen to )"))
    assert q[0].options[3] == "Four"


def test_legacy_title_matching_ignores_articles_plurals_and_ampersands():
    from toefly_seed.sources import tst_reading_legacy as legacy

    assert legacy._norm("Tissues and Aging") == legacy._norm("Tissue & Aging")
    assert legacy._norm("The Periodic Table") == legacy._norm("Periodic Table")


def test_legacy_marks_and_squares_are_read_before_zero_width_marks_are_dropped():
    from toefly_seed.sources import tst_reading_legacy as legacy

    lines = legacy._prepare("A ​ B ​ text ​key word​ here\n\nstray​ end")
    assert "[[B]]" in lines[0] and "{{mark:key word}}" in lines[0]
    assert legacy.STRAY in lines[-1]


def test_build_a_sentence_is_assembled_from_template_chunks_and_key():
    from toefly_seed.sources import tst_complete_test_2026 as ct

    template, chunks, order = ct.build_sentence(
        "I’d love to hear _____ _____ _____ _____ _____ _____ _____.",
        "speaker / the / articulate / on / someday / present / topic / another",
        "I’d love to hear the speaker present on another topic someday.",
    )
    assert [chunks[i] for i in order] == ["the", "speaker", "present", "on", "another", "topic", "someday"]
    assert chunks[2] == "articulate" and 2 not in order  # the unused chunk is the distractor
    assert template[-1] == ("FIXED", ".")


def test_build_a_sentence_reports_an_unassemblable_key():
    from toefly_seed.sources import tst_complete_test_2026 as ct

    _, _, order = ct.build_sentence("_____ a _____", "x / y", "x b y")
    assert order is None


def test_score_table_rows_are_read():
    from toefly_seed.sources import tst_complete_test_2026 as ct

    ct.RAW_TO_BAND.clear()
    ct._rubric(["   47-48     6 (29-30)", "     3      1.5 (2)"], "reading")
    assert ct.RAW_TO_BAND["reading"][0] == {"rawFrom": 47, "rawTo": 48, "band": 6.0, "legacyFrom": 29, "legacyTo": 30}
    assert ct.RAW_TO_BAND["reading"][1]["rawFrom"] == 3
