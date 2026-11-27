package com.toefly.content.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("difficulty bands, unit codes and the mistake taxonomy of the tutor searches")
class TutorSearchRulesTest {

    @Test
    @DisplayName("easier is below b − 0.5, harder above b + 0.5, the same within ±0.5 inclusive")
    void bands() {
        RelativeDifficulty.Band same = RelativeDifficulty.SAME.around(1.0);
        assertThat(same.contains(0.5)).isTrue();
        assertThat(same.contains(1.5)).isTrue();
        assertThat(same.contains(1.51)).isFalse();
        assertThat(same.target()).isEqualTo(1.0);

        RelativeDifficulty.Band easier = RelativeDifficulty.EASIER.around(1.0);
        assertThat(easier.min()).isNull();
        assertThat(easier.contains(0.49)).isTrue();
        assertThat(easier.contains(0.5)).isFalse();

        RelativeDifficulty.Band harder = RelativeDifficulty.HARDER.around(1.0);
        assertThat(harder.max()).isNull();
        assertThat(harder.contains(1.5)).isFalse();
        assertThat(harder.contains(1.51)).isTrue();
    }

    @Test
    @DisplayName("grammar unit codes of Round-Up 5 and Collins Grammar; vocabulary and other codes are not units")
    void unitCodes() {
        assertThat(GrammarUnitCode.parse("roundup5.u10")).contains(new GrammarUnitCode("ROUND_UP_5", 10));
        assertThat(GrammarUnitCode.parse("COLLINS_GR.U07"))
                .hasValueSatisfying(c -> assertThat(c.code()).isEqualTo("COLLINS_GR.U07"));
        assertThat(new GrammarUnitCode("ROUND_UP_5", 1).code()).isEqualTo("ROUNDUP5.U01");
        assertThat(GrammarUnitCode.parse("COLLINS_VOC.U08")).isEmpty();
        assertThat(GrammarUnitCode.parse("present forms")).isEmpty();
        assertThatThrownBy(() -> new GrammarUnitCode("COLLINS_VOCABULARY", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GrammarUnitCode("ROUND_UP_5", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the taxonomy keeps grammar units only and reads subcategories case-insensitively")
    void taxonomy() {
        Map<String, String> codes = new HashMap<>();
        codes.put("GRAMMAR.ARTICLES", "ROUNDUP5.U10");
        codes.put("VOCABULARY.COLLOCATION", "COLLINS_VOC.U08");
        codes.put("TASK_RESPONSE.LENGTH", null);
        MistakeTaxonomy taxonomy = new MistakeTaxonomy(codes);

        assertThat(taxonomy.unitOf(" grammar.articles ")).contains(new GrammarUnitCode("ROUND_UP_5", 10));
        assertThat(taxonomy.isSubcategory("GRAMMAR.ARTICLES")).isTrue();
        assertThat(taxonomy.unitOf("VOCABULARY.COLLOCATION")).isEmpty();
        assertThat(taxonomy.isSubcategory("TASK_RESPONSE.LENGTH")).isFalse();
    }
}
