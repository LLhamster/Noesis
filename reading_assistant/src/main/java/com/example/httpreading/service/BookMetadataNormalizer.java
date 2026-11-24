package com.example.httpreading.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class BookMetadataNormalizer {
    private static final Pattern EDITION_SUFFIX = Pattern.compile(
            "\\s*[（(]\\s*(?:新版|修订版|增订版|第[0-9一二三四五六七八九十]+版)\\s*[）)]$");
    private static final Pattern SPACING = Pattern.compile("[\\s\\p{Z}]+");

    private BookMetadataNormalizer() {}

    public static String titleKey(String value) {
        return key(EDITION_SUFFIX.matcher(key(value)).replaceFirst(""));
    }

    public static String authorKey(String value) {
        return key(value);
    }

    private static String key(String value) {
        if (value == null) {
            return "";
        }
        return SPACING.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC))
                .replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
