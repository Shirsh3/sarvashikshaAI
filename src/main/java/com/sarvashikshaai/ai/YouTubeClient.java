package com.sarvashikshaai.ai;

import com.sarvashikshaai.model.dto.YoutubeSearchResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class YouTubeClient {

    private final WebClient webClient;
    private final String apiKey;

    public YouTubeClient(@Qualifier("youTubeWebClient") WebClient webClient,
                         @Qualifier("youTubeApiKey") String apiKey) {
        this.webClient = webClient;
        this.apiKey    = apiKey;
    }

    /**
     * Searches YouTube for an educational video matching the topic.
     * Returns the video ID of the first result, or null if unavailable.
     */
    public String fetchVideoId(String topic) {
        if (apiKey == null || apiKey.isBlank()) return null;

        try {
            YoutubeSearchResponse response = webClient.get()
                    .uri(uri -> uri
                            .path("/search")
                            .queryParam("part", "snippet")
                            .queryParam("q", topic + " explained for students")
                            .queryParam("type", "video")
                            .queryParam("videoEmbeddable", "true")
                            .queryParam("safeSearch", "strict")
                            .queryParam("maxResults", 1)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(YoutubeSearchResponse.class)
                    .block();

            if (response != null && response.items != null && !response.items.isEmpty()) {
                YoutubeSearchResponse.YoutubeItem item = response.items.get(0);
                if (item.id != null) return item.id.videoId;
            }
        } catch (Exception ignored) {
            // Video is optional — never fail the main response because of this
        }
        return null;
    }
}
