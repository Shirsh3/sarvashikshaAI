package com.sarvashikshaai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class OpenAiConfig {

    @Value("${openai.api-base-url:https://api.openai.com/v1}")
    private String apiBaseUrl;

    @Value("${openai.model.assembly:${openai.model:gpt-4o-mini}}")
    private String assemblyModel;

    @Value("${openai.model.quiz:${openai.model:gpt-4o-mini}}")
    private String quizModel;

    @Value("${openai.model.teaching:${openai.model:gpt-4o-mini}}")
    private String teachingModel;

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

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(25));

        return WebClient.builder()
                .baseUrl(apiBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .filter(logReqRes())
                .filter(logOnError())
                .build();
    }

    @Bean
    public String openAiTeachingModel() {
        return teachingModel;
    }

    @Bean
    public String openAiAssemblyModel() {
        return assemblyModel;
    }

    @Bean
    public String openAiQuizModel() {
        return quizModel;
    }

    private ExchangeFilterFunction logOnError() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (clientResponse.statusCode().isError()) {
                return clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            String b = body == null ? "" : body.replaceAll("\\s+", " ").trim();
                            if (b.length() > 150) b = b.substring(0, 150) + "…";
                            String message = "OpenAI API error: " + clientResponse.statusCode() + " - " + b;
                            return clientResponse.createException().flatMap(ex -> {
                                ex.addSuppressed(new RuntimeException(message));
                                return reactor.core.publisher.Mono.error(ex);
                            });
                        });
            }
            return reactor.core.publisher.Mono.just(clientResponse);
        });
    }

    private ExchangeFilterFunction logReqRes() {
        return (request, next) -> {
            long t0 = System.currentTimeMillis();
            ClientRequest req = request;
            return next.exchange(req).flatMap(resp -> {
                long dt = System.currentTimeMillis() - t0;
                // Keep logs short; bodies are logged only on error by logOnError()
                String uri = req.url() != null ? req.url().toString() : "";
                if (uri.length() > 150) uri = uri.substring(0, 150) + "…";
                org.slf4j.LoggerFactory.getLogger(OpenAiConfig.class)
                        .info("OpenAI {} {} -> {} ({} ms)", req.method(), uri, resp.statusCode().value(), dt);
                return reactor.core.publisher.Mono.just(resp);
            });
        };
    }
}

