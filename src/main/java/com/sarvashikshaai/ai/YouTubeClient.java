package com.sarvashikshaai.ai;

import com.sarvashikshaai.model.dto.YoutubeSearchResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
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
     * Tries Education category (27) first; if no results, retries without category.
     */
    public String fetchVideoId(String topic) {
        if (apiKey == null || apiKey.isBlank()) return null;

        String q = buildQuery(topic);
        String id = search(q, true);
        if (id == null) {
            id = search(q, false);
        }
        return id;
    }

    private static String buildQuery(String topic) {
        if (topic == null || topic.isBlank()) return "educational video for students";
        String t = topic.trim();
        if (t.toLowerCase().contains("explained")) return t;
        return t + " explained for students";
    }

    private String search(String q, boolean educationCategory) {
        try {
            YoutubeSearchResponse response = webClient.get()
                    .uri(uri -> {
                        UriBuilder b = uri.path("/search")
                                .queryParam("part", "snippet")
                                .queryParam("q", q)
                                .queryParam("type", "video")
                                .queryParam("videoEmbeddable", "true")
                                .queryParam("safeSearch", "strict")
                                .queryParam("maxResults", 1)
                                .queryParam("key", apiKey);
                        if (educationCategory) {
                            b.queryParam("videoCategoryId", "27");
                        }
                        return b.build();
                    })
                    .retrieve()
                    .bodyToMono(YoutubeSearchResponse.class)
                    .block();

            if (response != null && response.items != null && !response.items.isEmpty()) {
                YoutubeSearchResponse.YoutubeItem item = response.items.get(0);
                if (item.id != null) return item.id.videoId;
            }
        } catch (Exception e) {
            log.warn("YouTube Data API search failed (q={}, educationCategory={}): {}", q, educationCategory, e.getMessage());
        }
        return null;
    }
}
