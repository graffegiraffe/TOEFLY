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
