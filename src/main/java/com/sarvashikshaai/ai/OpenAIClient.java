package com.sarvashikshaai.ai;

import com.sarvashikshaai.model.dto.OpenAiChatRequest;
import com.sarvashikshaai.model.dto.OpenAiChatResponse;
import com.sarvashikshaai.model.dto.OpenAiMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class OpenAIClient {

    private final WebClient webClient;
    private final String model;

    public OpenAIClient(@Qualifier("openAiWebClient") WebClient webClient,
                        @Qualifier("openAiModel") String model) {
        this.webClient = webClient;
        this.model = model;
    }

    public String generateCompletion(String prompt) {
        OpenAiChatRequest request = new OpenAiChatRequest(
                model,
                List.of(new OpenAiMessage("user", prompt))
        );

        OpenAiChatResponse response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OpenAiChatResponse.class)
                .onErrorResume(ex -> Mono.error(new IllegalStateException("Failed to call OpenAI API", ex)))
                .block();

        if (response == null || response.choices == null || response.choices.isEmpty()) {
            throw new IllegalStateException("OpenAI API returned no choices");
        }

        return response.choices.get(0).message.content().trim();
    }
}
