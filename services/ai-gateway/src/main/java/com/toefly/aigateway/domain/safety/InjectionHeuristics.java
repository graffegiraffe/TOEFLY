package com.toefly.aigateway.domain.safety;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Cheap patterns of prompt injection in English and Russian, run before the guard model (§3.5.2). */
public final class InjectionHeuristics {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Map<HeuristicHit, List<Pattern>> PATTERNS = new LinkedHashMap<>();

    static {
        PATTERNS.put(
                HeuristicHit.ROLE_OVERRIDE,
                List.of(
                        // "ignore the instructions on the label" is English; "ignore all/your/previous ..." is not.
                        Pattern.compile(
                                "\\b(ignore|disregard|forget|override|bypass|skip)\\s+"
                                        + "((all|any|your|these|my)\\s+(\\w+\\s+)?|(the\\s+)?(previous|prior|above|earlier|preceding|original|system)\\s+)"
                                        + "(instructions?|rules|prompts?|guidelines|directions|constraints)",
                                FLAGS),
                        Pattern.compile("\\byou\\s+are\\s+(now|no\\s+longer)\\b", FLAGS),
                        Pattern.compile("\\bfrom\\s+now\\s+on,?\\s+you\\b", FLAGS),
                        Pattern.compile("\\bnew\\s+(system\\s+)?instructions?\\s*:", FLAGS),
                        Pattern.compile(
                                "(игнорируй|забудь|отмени|не\\s+обращай\\s+внимания\\s+на)\\s+(все\\s+)?(предыдущие\\s+|прошлые\\s+|свои\\s+)?(инструкции|правила|указания)",
                                FLAGS),
                        Pattern.compile("\\bты\\s+теперь\\b", FLAGS)));
        PATTERNS.put(
                HeuristicHit.PROMPT_EXTRACTION,
                List.of(
                        Pattern.compile("\\bsystem\\s+(prompt|message|instructions?)\\b", FLAGS),
                        Pattern.compile(
                                "\\b(reveal|show|print|repeat|output|tell\\s+me|display|leak|dump)\\s+(me\\s+)?(your|the)\\s+"
                                        + "(hidden\\s+|initial\\s+|original\\s+|secret\\s+)?(prompt|instructions|rules|configuration)",
                                FLAGS),
                        Pattern.compile(
                                "\\bwhat\\s+(were|are)\\s+your\\s+(initial\\s+|original\\s+)?instructions\\b", FLAGS),
                        Pattern.compile("системн(ый|ого|ому)\\s+(промпт|подсказк|инструкц)", FLAGS)));
        PATTERNS.put(
                HeuristicHit.SCORE_MANIPULATION,
                List.of(
                        Pattern.compile(
                                "\\b(give|award|assign|grant)\\s+(me|this(\\s+(essay|response|answer|text))?|it|my\\s+\\w+)?\\s*(a\\s+|the\\s+)?"
                                        + "(5|five|5/5|full\\s+(score|marks|points)|perfect\\s+score|top\\s+score|maximum\\s+score|highest\\s+score)",
                                FLAGS),
                        Pattern.compile(
                                "\\b(score|grade|rate|mark)\\s+(this|it|me|my\\s+\\w+)\\s+(as\\s+|with\\s+)?(a\\s+)?(5|five|perfect|full)\\b",
                                FLAGS),
                        Pattern.compile(
                                "\\b(rater|grader|evaluator|examiner)s?\\s*[:,]\\s*(please\\s+)?(give|score|assign|award)\\b",
                                FLAGS),
                        Pattern.compile("(поставь|поставьте|дай|дайте)\\s+(мне\\s+)?(5|пять|максимальн|высш)", FLAGS)));
        PATTERNS.put(
                HeuristicHit.DEVELOPER_MODE,
                List.of(
                        Pattern.compile("\\b(developer|debug|god|admin)\\s+mode\\b", FLAGS),
                        Pattern.compile("\\bDAN\\b|\\bdo\\s+anything\\s+now\\b|\\bjailbr(eak|oken)\\b", FLAGS)));
        PATTERNS.put(
                HeuristicHit.ROLE_TAGS,
                List.of(
                        Pattern.compile("</?\\s*(system|assistant|tool|developer)\\s*>", FLAGS),
                        Pattern.compile("<\\|(im_start|im_end|system|assistant)\\|>|\\[/?INST]|<<SYS>>", FLAGS)));
        PATTERNS.put(
                HeuristicHit.ROLE_PLAY,
                List.of(Pattern.compile(
                        "\\b(act\\s+as|pretend\\s+(to\\s+be|you\\s+are)|role-?play\\s+as|play\\s+the\\s+role\\s+of)\\b",
                        FLAGS)));
        PATTERNS.put(HeuristicHit.BASE64_BLOB, List.of(Pattern.compile("[A-Za-z0-9+/]{200,}={0,2}")));
    }

    private InjectionHeuristics() {}

    /** Hits in a text; invisible characters are reported here and must be stripped by {@link TextSanitizer}. */
    public static List<HeuristicHit> scan(String text) {
        List<HeuristicHit> hits = new ArrayList<>();
        if (TextSanitizer.hasInvisible(text)) {
            hits.add(HeuristicHit.INVISIBLE_CHARS);
        }
        String visible = TextSanitizer.stripInvisible(text);
        PATTERNS.forEach((hit, patterns) -> {
            if (patterns.stream().anyMatch(p -> p.matcher(visible).find())) {
                hits.add(hit);
            }
        });
        return hits;
    }
}
