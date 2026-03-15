package com.sarvashikshaai.ai;

import com.sarvashikshaai.model.dto.WikiMediaResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class WikipediaClient {

    private final WebClient webClient;

    public WikipediaClient(@Qualifier("wikipediaWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Fetches the first animated GIF from the Wikipedia article matching the topic.
     * Returns the GIF URL, or null if none found.
     */
    public String fetchAnimatedGif(String topic) {
        if (topic == null || topic.isBlank()) return null;

        // Normalise: capitalise first letter, replace spaces with underscores
        String slug = topic.trim();
        slug = Character.toUpperCase(slug.charAt(0)) + slug.substring(1).replace(' ', '_');

        try {
            WikiMediaResponse response = webClient.get()
                    .uri("/page/media-list/" + slug)
                    .retrieve()
                    .bodyToMono(WikiMediaResponse.class)
                    .block();

            if (response != null && response.items != null) {
                return response.items.stream()
                        .filter(item -> item.original != null
                                && item.original.source != null
                                && item.original.source.toLowerCase().endsWith(".gif"))
                        .map(item -> item.original.source)
                        .findFirst()
                        .orElse(null);
            }
        } catch (Exception ignored) {
            // GIF is optional — graceful fallback to CSS scene
        }
        return null;
    }
}
