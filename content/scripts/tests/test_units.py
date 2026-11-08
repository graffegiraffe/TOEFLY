"""Chart and contents parsing on synthetic coordinates (no book text)."""

from toefly_seed.sources import collins, roundup


class FakePage:
    height = 650

    def __init__(self, words):
        self._words = words

    def extract_words(self):
        return self._words


def w(text, x, top):
    return {"text": text, "x0": x, "top": top}


def test_chart_columns_become_word_definition_and_shared_example():
    page = FakePage([
        w("Word", 35, 150), w("Definition", 124, 150), w("Example", 258, 150),
        w("river", 35, 174), w("(n)", 60, 174), w("big", 124, 174), w("stream", 150, 174),
        w("The", 259, 174), w("river", 280, 174), w("is", 320, 174), w("wide", 340, 174),
        w("flow", 35, 192), w("(v)", 60, 192), w("move", 124, 192), w("smoothly", 160, 192),
        w("1", 30, 640),
    ])
    entries = collins.read_chart(page)

    assert [e["word"] for e in entries] == ["river", "flow"]
    assert entries[0]["definition"] == "big stream"
    assert entries[0]["example"] == "The river is wide"
    assert entries[1]["definition"] == "move smoothly"
    assert "example" not in entries[1]


def test_a_page_without_the_three_column_header_is_not_a_chart():
    assert collins.read_chart(FakePage([w("Comparing", 35, 100)])) == []


def test_round_up_titles_come_from_the_left_column_and_join_wrapped_lines():
    text = "\n".join([
        "B. Key" + " " * 80 + "D. Key to Tests",
        "          Present Forms ................................................. 6        E. Quizzes",
        "     2    Past Forms ...................................................... 7",
        "     11   Causative Form (Have something",
        "          done) ............................................................. 24",
    ])
    titles = roundup._titles(text)
    assert titles[1] == "Present Forms"
    assert titles[2] == "Past Forms"
    assert titles[11] == "Causative Form (Have something done)"
