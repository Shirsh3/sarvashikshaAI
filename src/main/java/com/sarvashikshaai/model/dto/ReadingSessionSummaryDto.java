package com.sarvashikshaai.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * JSON for teacher “reading history” on the Search Passage tab (metrics + short narrative).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadingSessionSummaryDto(
        long id,
        String studentName,
        String articleTitle,
        /** Combined feedback excerpt, at most ~80 words */
        String summaryWords,
        Integer fluencyScore,
        Integer pronunciationScore,
        Integer paceScore,
        Integer accuracyScore,
        Integer confidenceScore,
        Integer accuracyPercent,
        Integer originalWordCount,
        Integer spokenWordCount,
        String createdAt
) {}
