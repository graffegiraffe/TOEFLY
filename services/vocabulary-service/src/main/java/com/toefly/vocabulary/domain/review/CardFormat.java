package com.toefly.vocabulary.domain.review;

/** AC-15.03.2: the side shown first alternates between three formats. */
public enum CardFormat {
    WORD_TO_DEFINITION,
    DEFINITION_TO_WORD,
    CLOZE;

    /**
     * Format of the card at {@code position} of the queue; a format the entry cannot show (no definition, no sentence
     * with the word) falls back to word → definition.
     */
    public static CardFormat choose(int position, int reps, boolean hasDefinition, boolean hasCloze) {
        CardFormat wanted = values()[Math.floorMod(position + reps, values().length)];
        return switch (wanted) {
            case DEFINITION_TO_WORD -> hasDefinition ? wanted : WORD_TO_DEFINITION;
            case CLOZE -> hasCloze ? wanted : (hasDefinition ? DEFINITION_TO_WORD : WORD_TO_DEFINITION);
            case WORD_TO_DEFINITION -> wanted;
        };
    }
}
