package com.toefly.vocabulary.application.port.in;

import com.toefly.vocabulary.domain.wordset.WordSet;

/** A Collins unit with the learner's progress: "20 words · 8 learned". */
public record WordSetView(WordSet set, int inDictionary, int learned) {}
