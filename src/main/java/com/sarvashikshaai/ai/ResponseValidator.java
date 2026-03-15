package com.sarvashikshaai.ai;

import com.sarvashikshaai.model.TeachingRequest;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Kept for potential future use. No longer called by the main service flow.
 */
@Component
public class ResponseValidator {

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("[.!?]+\\s*");

    public ValidationResult validate(String response, TeachingRequest request, int maxSentences) {
        if (response == null || response.isBlank()) {
            return ValidationResult.invalid("Empty response.");
        }

        String trimmed = response.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        int sentenceCount = (int) Arrays.stream(SENTENCE_SPLIT.split(trimmed))
                .filter(s -> !s.isBlank())
                .count();
        if (sentenceCount > maxSentences) {
            return ValidationResult.invalid("Too many sentences: " + sentenceCount);
        }

        String topic = request.getTopic() == null ? "" : request.getTopic().toLowerCase(Locale.ROOT);
        String[] topicTokens = topic.split("\\s+");
        long matchCount = Arrays.stream(topicTokens)
                .filter(t -> t.length() > 3)
                .filter(lower::contains)
                .count();
        if (matchCount == 0 && topicTokens.length > 0) {
            return ValidationResult.invalid("Response may have drifted away from the topic.");
        }

        return ValidationResult.ok();
    }
}
