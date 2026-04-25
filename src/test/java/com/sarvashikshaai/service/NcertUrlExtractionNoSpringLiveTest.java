package com.sarvashikshaai.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live extraction smoke test that does NOT start Spring.
 * Run manually when needed:
 * mvn -q test -Dtest=NcertUrlExtractionNoSpringLiveTest -DrunNcertLive=true
 */
class NcertUrlExtractionNoSpringLiveTest {

    private static final String DEFAULT_NCERT_URL = "https://ncert.nic.in/textbook.php?leph1=3-8";

    @Test
    void extractAndPrintPreview() {
        Assumptions.assumeTrue(Boolean.getBoolean("runNcertLive"), "Skipping live test: set -DrunNcertLive=true");

        String ncertUrl = System.getProperty("ncertUrl", DEFAULT_NCERT_URL).trim();
        int previewChars = Integer.getInteger("previewChars", 2500);

        FileExtractionService fileExtractionService = new FileExtractionService();
        UrlContentService urlContentService = new UrlContentService(fileExtractionService);

        String extracted = urlContentService.extractContextFromUrl(ncertUrl);
        assertThat(extracted).isNotBlank();

        String preview = extracted.length() > previewChars ? extracted.substring(0, previewChars) + "\n...[truncated preview]" : extracted;
        System.out.println("\n=== NCERT URL (EXTRACTION PREVIEW) ===");
        System.out.println(ncertUrl);
        System.out.println("=== EXTRACTED TEXT (first ~" + previewChars + " chars) ===");
        System.out.println(preview);
        System.out.println("=== END ===\n");
    }
}

