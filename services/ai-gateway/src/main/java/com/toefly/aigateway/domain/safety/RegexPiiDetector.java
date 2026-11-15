package com.toefly.aigateway.domain.safety;

import com.google.i18n.phonenumbers.PhoneNumberMatch;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stage 1 of masking (§3.2): precompiled patterns and checks (libphonenumber, Luhn). */
public final class RegexPiiDetector {

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern URL_WITH_QUERY =
            Pattern.compile("\\b(?:https?://|www\\.)[^\\s<>\"]+\\?[^\\s<>\"]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern IBAN =
            Pattern.compile("\\b[A-Z]{2}\\d{2}(?:\\s?[A-Z0-9]{4}){3,7}(?:\\s?[A-Z0-9]{1,4})?\\b");
    private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]?){12,18}\\d\\b");
    private static final Pattern PASSPORT_BY = Pattern.compile("\\b[A-Z]{2}\\d{7}\\b");
    private static final Pattern PASSPORT_RU = Pattern.compile("\\b\\d{4}\\s?\\d{6}\\b");
    private static final Pattern IPV4 =
            Pattern.compile("\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\b");
    private static final Pattern HANDLE = Pattern.compile("(?<![\\w.@])@[A-Za-z0-9_]{3,30}\\b");
    private static final Pattern BIRTH_DATE = Pattern.compile(
            "(?:born|birthday|DOB|date\\s+of\\s+birth|родил(?:ся|ась)|дата\\s+рождения)\\D{0,20}"
                    + "(\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final List<String> PHONE_REGIONS = List.of("BY", "RU", "US", "GB");

    private RegexPiiDetector() {}

    public static List<PiiSpan> detect(String text) {
        List<PiiSpan> spans = new ArrayList<>();
        add(spans, EMAIL.matcher(text), PiiType.EMAIL);
        add(spans, URL_WITH_QUERY.matcher(text), PiiType.URL);
        add(spans, IBAN.matcher(text), PiiType.IBAN);
        Matcher card = CARD.matcher(text);
        while (card.find()) {
            if (luhn(card.group().replaceAll("[ -]", ""))) {
                spans.add(span(card.start(), card.end(), PiiType.CARD));
            }
        }
        add(spans, PASSPORT_BY.matcher(text), PiiType.DOC_ID);
        add(spans, PASSPORT_RU.matcher(text), PiiType.DOC_ID);
        add(spans, IPV4.matcher(text), PiiType.IP);
        add(spans, HANDLE.matcher(text), PiiType.HANDLE);
        Matcher birth = BIRTH_DATE.matcher(text);
        while (birth.find()) {
            spans.add(span(birth.start(1), birth.end(1), PiiType.DATE));
        }
        PhoneNumberUtil phones = PhoneNumberUtil.getInstance();
        for (String region : PHONE_REGIONS) {
            for (PhoneNumberMatch match : phones.findNumbers(text, region)) {
                spans.add(span(match.start(), match.end(), PiiType.PHONE));
            }
        }
        return spans;
    }

    static boolean luhn(String digits) {
        int sum = 0;
        boolean twice = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (d < 0 || d > 9) {
                return false;
            }
            if (twice) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            twice = !twice;
        }
        return digits.length() >= 13 && sum % 10 == 0;
    }

    private static void add(List<PiiSpan> spans, Matcher matcher, PiiType type) {
        while (matcher.find()) {
            spans.add(span(matcher.start(), matcher.end(), type));
        }
    }

    private static PiiSpan span(int start, int end, PiiType type) {
        return new PiiSpan(start, end, type, PiiStage.REGEX, null);
    }
}
