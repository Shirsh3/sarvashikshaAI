package com.sarvashikshaai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/**
 * Verifies that a YouTube video exists and allows embedding by calling YouTube's
 * oEmbed endpoint (HTTP GET — same public metadata embeds use).
 */
@Service
@Slf4j
public class YouTubeEmbedCheckService {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://www.youtube.com")
            .build();

    /**
     * @return {@code null} if the video appears embeddable, or a short user-facing message if not.
     *         On network/timeout errors, returns {@code null} so the page still tries the iframe.
     */
    public String verifyEmbeddable(String videoId) {
        if (videoId == null || !videoId.matches("[a-zA-Z0-9_-]{11}")) {
            return "This does not look like a valid YouTube video link.";
        }
        String watchUrl = "https://www.youtube.com/watch?v=" + videoId;
        try {
            webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/oembed")
                            .queryParam("format", "json")
                            .queryParam("url", watchUrl)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
            return null;
        } catch (WebClientResponseException e) {
            int code = e.getStatusCode().value();
            log.debug("YouTube oEmbed check failed for {}: HTTP {}", videoId, code);
            if (code == 404) {
                return "This video is unavailable or cannot be embedded. Open it on YouTube to confirm, then update the link under Students.";
            }
            if (code == 401 || code == 403) {
                return "YouTube did not allow checking this video. It may be private or restricted.";
            }
            return null;
        } catch (Exception e) {
            log.debug("YouTube oEmbed check skipped for {}: {}", videoId, e.getMessage());
            return null;
        }
    }
}
