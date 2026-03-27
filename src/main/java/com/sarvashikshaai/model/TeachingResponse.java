package com.sarvashikshaai.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TeachingResponse {

    private final String explanation;

    /** Parsed from the 💡 Explanation: section, or null if parsing fails. */
    private final String explanationSection;

    /** Parsed from the 📌 Example: section, or null if parsing fails. */
    private final String exampleSection;

    /** Parsed from the 🔑 Key Point: section, or null if parsing fails. */
    private final String keyPointSection;

    /** YouTube video ID, e.g. "dQw4w9WgXcQ". Null if not found. */
    private final String videoId;

    /** Wikipedia animated GIF URL. Null if not found. Used when videoId is null. */
    private final String wikiGifUrl;

    /** True when the query was blocked as non-educational; only {@link #explanation} is shown. */
    private final boolean nonEducational;
}
