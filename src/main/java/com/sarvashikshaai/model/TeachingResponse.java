package com.sarvashikshaai.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class TeachingResponse {

    private String explanation;

    /** Parsed from the 💡 Explanation: section, or null if parsing fails. */
    private String explanationSection;

    /** Parsed from the 📌 Example: section, or null if parsing fails. */
    private String exampleSection;

    /** Parsed from the 🔑 Key Point: section, or null if parsing fails. */
    private String keyPointSection;

    /** YouTube video ID, e.g. "dQw4w9WgXcQ". Null if not found. */
    private String videoId;

    /** Wikipedia animated GIF URL. Null if not found. Used when videoId is null. */
    private String wikiGifUrl;

    /**
     * LLM-suggested phrase for YouTube search when the user clicks “Load video” (quiz / lazy path).
     * Null when not applicable or when served from cache without this field.
     */
    private String youtubeSearchQuery;

    /** True when the query was blocked as non-educational; only {@link #explanation} is shown. */
    private boolean nonEducational;

    /** Learning conversation id (when learningMode). */
    private Long conversationId;

    /** Context-aware follow-up chip labels. */
    private List<String> followUpSuggestions = new ArrayList<>();

    /** Lightweight source line for UI, e.g. "Based on your textbook · 4 sections used". */
    private String sourceNote;

    private int sourcesUsed;

    public TeachingResponse() {}

    public TeachingResponse(
            String explanation,
            String explanationSection,
            String exampleSection,
            String keyPointSection,
            String videoId,
            String wikiGifUrl,
            String youtubeSearchQuery,
            boolean nonEducational
    ) {
        this.explanation = explanation;
        this.explanationSection = explanationSection;
        this.exampleSection = exampleSection;
        this.keyPointSection = keyPointSection;
        this.videoId = videoId;
        this.wikiGifUrl = wikiGifUrl;
        this.youtubeSearchQuery = youtubeSearchQuery;
        this.nonEducational = nonEducational;
    }
}
