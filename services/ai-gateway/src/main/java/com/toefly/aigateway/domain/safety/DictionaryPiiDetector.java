package com.toefly.aigateway.domain.safety;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage 2 of masking (§3.2): the names a caller knows (the learner and the tutor from profile-service), in either
 * script (BGN/PCGN transliteration) and, for Russian forms, with case endings.
 */
public final class DictionaryPiiDetector {

    private static final Map<Character, String> CYR_TO_LAT = Map.ofEntries(
            Map.entry('а', "a"),
            Map.entry('б', "b"),
            Map.entry('в', "v"),
            Map.entry('г', "g"),
            Map.entry('д', "d"),
            Map.entry('е', "e"),
            Map.entry('ё', "ye"),
            Map.entry('ж', "zh"),
            Map.entry('з', "z"),
            Map.entry('и', "i"),
            Map.entry('й', "y"),
            Map.entry('к', "k"),
            Map.entry('л', "l"),
            Map.entry('м', "m"),
            Map.entry('н', "n"),
            Map.entry('о', "o"),
            Map.entry('п', "p"),
            Map.entry('р', "r"),
            Map.entry('с', "s"),
            Map.entry('т', "t"),
            Map.entry('у', "u"),
            Map.entry('ф', "f"),
            Map.entry('х', "kh"),
            Map.entry('ц', "ts"),
            Map.entry('ч', "ch"),
            Map.entry('ш', "sh"),
            Map.entry('щ', "shch"),
            Map.entry('ъ', ""),
            Map.entry('ы', "y"),
            Map.entry('ь', ""),
            Map.entry('э', "e"),
            Map.entry('ю', "yu"),
            Map.entry('я', "ya"),
            Map.entry('і', "i"),
            Map.entry('ў', "w"));
    private static final String[][] LAT_TO_CYR = {
        {"shch", "щ"}, {"zh", "ж"}, {"kh", "х"}, {"ts", "ц"}, {"ch", "ч"}, {"sh", "ш"}, {"yu", "ю"}, {"ya", "я"},
        {"ye", "е"}, {"yo", "ё"}, {"a", "а"}, {"b", "б"}, {"v", "в"}, {"g", "г"}, {"d", "д"}, {"e", "е"},
        {"z", "з"}, {"i", "и"}, {"y", "й"}, {"k", "к"}, {"l", "л"}, {"m", "м"}, {"n", "н"}, {"o", "о"},
        {"p", "п"}, {"r", "р"}, {"s", "с"}, {"t", "т"}, {"u", "у"}, {"f", "ф"}, {"c", "к"}, {"h", "х"},
        {"w", "в"}, {"x", "кс"}, {"j", "дж"}, {"q", "к"}
    };
    private static final String RU_ENDINGS =
            "(?:о|а|я|ы|и|е|у|ю|ой|ей|ою|ею|ом|ем|ым|им|ов|ев|ых|их|ую|ая|ий|ый|ого|его|ому|ему|ое|ые|ие|ью|ье|ьи|ь)?";
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private DictionaryPiiDetector() {}

    public static List<PiiSpan> detect(String text, List<String> hints) {
        List<PiiSpan> spans = new ArrayList<>();
        for (String hint : hints) {
            String name = hint.strip();
            if (name.length() < 2) {
                continue;
            }
            String group = "hint:" + name.toLowerCase(Locale.ROOT);
            for (Pattern pattern : patterns(name)) {
                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                    spans.add(new PiiSpan(matcher.start(), matcher.end(), PiiType.PERSON, PiiStage.DICTIONARY, group));
                }
            }
        }
        return spans;
    }

    static List<Pattern> patterns(String name) {
        Set<String> latin = new LinkedHashSet<>();
        Set<String> cyrillic = new LinkedHashSet<>();
        if (cyrillic(name)) {
            cyrillic.add(name);
            latin.add(toLatin(name));
        } else {
            latin.add(name);
            String russian = toCyrillic(name);
            cyrillic.add(russian);
            // "Daria" and "Maria" are written Дарья, Мария: both readings of "-ia".
            if (russian.endsWith("иа")) {
                cyrillic.add(russian.substring(0, russian.length() - 2) + "ья");
                cyrillic.add(russian.substring(0, russian.length() - 2) + "ия");
            }
        }
        List<Pattern> patterns = new ArrayList<>();
        for (String form : latin) {
            patterns.add(Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(form) + "(?![\\p{L}\\p{N}])", FLAGS));
        }
        for (String form : cyrillic) {
            String stem = stem(form);
            patterns.add(Pattern.compile(
                    "(?<![\\p{L}\\p{N}])" + Pattern.quote(stem) + RU_ENDINGS + "(?![\\p{L}\\p{N}])", FLAGS));
        }
        return patterns;
    }

    static boolean cyrillic(String text) {
        return text.codePoints().anyMatch(cp -> Character.UnicodeBlock.of(cp) == Character.UnicodeBlock.CYRILLIC);
    }

    static String toLatin(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            char lower = Character.toLowerCase(c);
            String mapped = CYR_TO_LAT.get(lower);
            if (mapped == null) {
                out.append(c);
            } else if (Character.isUpperCase(c) && !mapped.isEmpty()) {
                out.append(Character.toUpperCase(mapped.charAt(0))).append(mapped.substring(1));
            } else {
                out.append(mapped);
            }
        }
        return out.toString();
    }

    static String toCyrillic(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        int i = 0;
        outer:
        while (i < lower.length()) {
            for (String[] pair : LAT_TO_CYR) {
                if (lower.startsWith(pair[0], i)) {
                    out.append(pair[1]);
                    i += pair[0].length();
                    continue outer;
                }
            }
            out.append(lower.charAt(i++));
        }
        // "-skaya" reads as "-ская", not "-скайа".
        return out.toString().replace("айа", "ая").replace("ийа", "ия");
    }

    /** Drops a final vowel or soft sign, and the adjectival endings of surnames, keeping at least three letters. */
    static String stem(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        for (String ending : List.of("ая", "ий", "ый", "ой", "яя")) {
            if (lower.endsWith(ending) && lower.length() - ending.length() >= 3) {
                return word.substring(0, word.length() - ending.length());
            }
        }
        char last = lower.charAt(lower.length() - 1);
        if ("аяыиеоуюьй".indexOf(last) >= 0 && lower.length() > 3) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }
}
