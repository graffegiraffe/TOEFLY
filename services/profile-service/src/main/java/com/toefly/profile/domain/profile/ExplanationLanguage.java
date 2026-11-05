package com.toefly.profile.domain.profile;

import java.util.Arrays;
import java.util.Locale;

/** Language of AI explanations (AC-02.03.2). */
public enum ExplanationLanguage {
    EN,
    RU;

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ExplanationLanguage ofCode(String code) {
        return Arrays.stream(values())
                .filter(language -> language.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new InvalidProfileValueException("explanation-language", "Use en or ru."));
    }
}
