package com.example.httpreading.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BookMetadataNormalizerTest {
    @Test
    void normalizesWidthCaseAndSpacing() {
        assertEquals("abc def", BookMetadataNormalizer.authorKey("　ＡＢＣ　 DEF "));
    }

    @Test
    void removesKnownEditionSuffixWhileKeepingVolumeAndSubtitle() {
        assertEquals("看见", BookMetadataNormalizer.titleKey(" 看见（第２版） "));
        assertEquals("看见(第2卷)", BookMetadataNormalizer.titleKey("看见（第２卷）"));
        assertEquals("看见:记录", BookMetadataNormalizer.titleKey("看见：记录"));
    }

    @Test
    void handlesMissingMetadata() {
        assertEquals("", BookMetadataNormalizer.titleKey(null));
        assertEquals("", BookMetadataNormalizer.authorKey("　"));
    }
}
