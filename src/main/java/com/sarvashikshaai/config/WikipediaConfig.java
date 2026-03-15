package com.sarvashikshaai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WikipediaConfig {

    @Value("${wikipedia.base-url:https://en.wikipedia.org/api/rest_v1}")
    private String baseUrl;

    @Bean
    public WebClient wikipediaWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "SarvashikshaAI/1.0")
                .build();
    }
}
