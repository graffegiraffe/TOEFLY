package com.toefly.aigateway.domain.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parsed answer of Llama Guard 3: {@code safe}, or {@code unsafe} and a line of categories such as {@code S1,S15}. */
public record GuardModelVerdict(GuardVerdict verdict, List<String> categories, String model) {

    public GuardModelVerdict {
        categories = List.copyOf(categories);
    }

    public static GuardModelVerdict parseLlamaGuard(String answer, String model) {
        String[] lines = answer.strip().split("\\R");
        String first = lines.length == 0 ? "" : lines[0].strip().toLowerCase(Locale.ROOT);
        if (first.startsWith("safe")) {
            return new GuardModelVerdict(GuardVerdict.SAFE, List.of(), model);
        }
        List<String> categories = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            for (String part : lines[i].split("[,\\s]+")) {
                String category = part.strip().toUpperCase(Locale.ROOT);
                if (category.matches("S\\d{1,2}")) {
                    categories.add(category);
                }
            }
        }
        GuardVerdict verdict;
        if (categories.contains(GuardAssessment.INJECTION_CATEGORY)) {
            verdict = GuardVerdict.INJECTION;
        } else if (categories.contains(GuardAssessment.DISHONESTY_CATEGORY)) {
            verdict = GuardVerdict.DISHONESTY;
        } else {
            verdict = GuardVerdict.UNSAFE;
        }
        return new GuardModelVerdict(verdict, categories, model);
    }
}
