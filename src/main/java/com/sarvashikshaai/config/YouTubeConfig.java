package com.sarvashikshaai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class YouTubeConfig {

    @Value("${youtube.base-url:https://www.googleapis.com/youtube/v3}")
    private String baseUrl;

    @Value("${youtube.dataKey:}")
    private String apiKey;

    @Bean
    public WebClient youTubeWebClient() {
        return WebClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    public String youTubeApiKey() {
        return apiKey;
    }
}
