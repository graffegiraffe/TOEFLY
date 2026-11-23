package com.toefly.tutoragent.domain.chat;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cheap checks of a student message before any AI call (docs/06 §3.5): attempts to override the role or the rules,
 * to reveal the prompt, or to get another student's data. ai-gateway runs the guard model on top of this.
 */
public final class InjectionHeuristics {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("ignore (all |any |the )?(previous|prior|above|earlier) (instructions|rules|prompts?)"),
            Pattern.compile("(disregard|forget) (all |your |the )?(instructions|rules|guidelines)"),
            Pattern.compile(
                    "(reveal|show|print|repeat) (me )?(your (system )?(prompt|instructions)|the system prompt)"),
            Pattern.compile("you are (now )?(no longer|not) (a |an )?(tutor|toefly)"),
            Pattern.compile("(act|pretend|roleplay) as (an? )?(dan|unrestricted|jailbroken|developer mode)"),
            Pattern.compile("</?\\s*(system|student_message|assistant)\\b"),
            Pattern.compile("(игнорируй|забудь) (все )?(предыдущие )?(инструкции|правила)"));

    private InjectionHeuristics() {}

    public static boolean suspicious(String text) {
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return PATTERNS.stream().anyMatch(p -> p.matcher(normalized).find());
    }
}
