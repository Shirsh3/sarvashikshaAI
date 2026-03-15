package com.sarvashikshaai.service;

import com.sarvashikshaai.ai.OpenAIClient;
import com.sarvashikshaai.ai.PromptBuilder;
import com.sarvashikshaai.ai.WikipediaClient;
import com.sarvashikshaai.ai.YouTubeClient;
import com.sarvashikshaai.model.TeachingRequest;
import com.sarvashikshaai.model.TeachingResponse;
import org.springframework.stereotype.Service;

@Service
public class TeachingService {

    private final PromptBuilder promptBuilder;
    private final OpenAIClient openAIClient;
    private final YouTubeClient youTubeClient;
    private final WikipediaClient wikipediaClient;

    public TeachingService(PromptBuilder promptBuilder,
                           OpenAIClient openAIClient,
                           YouTubeClient youTubeClient,
                           WikipediaClient wikipediaClient) {
        this.promptBuilder = promptBuilder;
        this.openAIClient = openAIClient;
        this.youTubeClient = youTubeClient;
        this.wikipediaClient = wikipediaClient;
    }

    public TeachingResponse generateExplanation(TeachingRequest request) {
        String prompt = promptBuilder.buildPrompt(request);
        String raw = openAIClient.generateCompletion(prompt);

        String explanationSection = parseSection(raw, "💡 Explanation:");
        String exampleSection     = parseSection(raw, "📌 Example:");
        String keyPointSection    = parseSection(raw, "🔑 Key Point:");

        // Tier 1: YouTube video
        String videoId = youTubeClient.fetchVideoId(request.getTopic());

        // Tier 2: Wikipedia animated GIF (only fetched when no video found)
        String wikiGifUrl = (videoId == null) ? wikipediaClient.fetchAnimatedGif(request.getTopic()) : null;

        return new TeachingResponse(raw, explanationSection, exampleSection, keyPointSection, videoId, wikiGifUrl);
    }

    /**
     * Extracts the text after a given label up to the next emoji-labelled section or end of string.
     */
    private String parseSection(String text, String label) {
        int start = text.indexOf(label);
        if (start == -1) return null;
        start += label.length();

        int nextSection = Integer.MAX_VALUE;
        for (String marker : new String[]{"💡", "📌", "🔑"}) {
            int pos = text.indexOf(marker, start);
            if (pos != -1 && pos < nextSection) {
                nextSection = pos;
            }
        }

        String section = nextSection == Integer.MAX_VALUE
                ? text.substring(start)
                : text.substring(start, nextSection);

        return section.strip();
    }
}
