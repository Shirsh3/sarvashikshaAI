package com.sarvashikshaai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class OpenAiConfig {

    @Value("${openai.api-base-url:https://api.openai.com/v1}")
    private String apiBaseUrl;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Value("${openai.api-key:}")
    private String apiKeyProperty;

    @Bean
    public WebClient openAiWebClient() {
        String apiKey = (apiKeyProperty != null && !apiKeyProperty.isBlank())
                ? apiKeyProperty.trim()
                : System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is not configured. Set openai.api-key in application.properties or the OPENAI_API_KEY environment variable.");
        }

        return WebClient.builder()
                .baseUrl(apiBaseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .filter(logOnError())
                .build();
    }

    @Bean
    public String openAiModel() {
        return model;
    }

    private ExchangeFilterFunction logOnError() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (clientResponse.statusCode().isError()) {
                return clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            String message = "OpenAI API error: " + clientResponse.statusCode() + " - " + body;
                            return clientResponse.createException().flatMap(ex -> {
                                ex.addSuppressed(new RuntimeException(message));
                                return reactor.core.publisher.Mono.error(ex);
                            });
                        });
            }
            return reactor.core.publisher.Mono.just(clientResponse);
        });
    }
}

