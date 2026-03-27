package com.sarvashikshaai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarvashikshaai.model.TeachingRequest;
import com.sarvashikshaai.model.dto.OpenAiChatRequest;
import com.sarvashikshaai.model.dto.OpenAiChatResponse;
import com.sarvashikshaai.model.dto.OpenAiMessage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Calls the real OpenAI Chat Completions API using the same prompt as production
 * ({@link PromptBuilder#buildUnifiedTeachingPrompt}) for 10 representative
 * &quot;allowed but non-educational&quot; queries.</p>
 *
 * <p><b>Skipped</b> when no key is available (CI-safe).</p>
 *
 * <p>API key resolution (first non-blank): {@code OPENAI_API_KEY} env, {@code -Dopenai.api.key=...},
 * then {@code openai.api-key} from {@code classpath:application-local.properties}
 * (i.e. {@code src/main/resources/application-local.properties} after compile).</p>
 *
 * <p>Run locally (either set env or put the key in {@code application-local.properties}):</p>
 * <pre>
 *   mvn test -Popenai-live -Dtest=OpenAiTeachingRedirectionLiveTest
 * </pre>
 * <p>Optional: {@code -Dopenai.model.teaching=gpt-4o-mini}</p>
 */
@Tag("openai-live")
class OpenAiTeachingRedirectionLiveTest {

    private static final PromptBuilder PROMPT_BUILDER = new PromptBuilder();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static WebClient webClient;
    private static String model;

    @BeforeAll
    static void requireCredentialsAndClient() {
        Assumptions.assumeTrue(
                OpenAiCredentials.present(),
                "Set OPENAI_API_KEY, -Dopenai.api.key=..., or openai.api-key in src/main/resources/application-local.properties");

        webClient = WebClient.builder()
                .baseUrl(OpenAiCredentials.apiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + OpenAiCredentials.key())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        model = OpenAiCredentials.teachingModel();
    }

    static Stream<Arguments> superficialQueries() {
        return Stream.of(
                Arguments.of("Best hairstyle of actors", "style"),
                Arguments.of("Rate Indian actresses out of 10", "rating"),
                Arguments.of("Who is the most beautiful actress in Bollywood?", "beauty"),
                Arguments.of("Who is dating whom in Bollywood?", "gossip"),
                Arguments.of("Who has the most Instagram followers?", "fame"),
                Arguments.of("Tell me a random joke", "joke"),
                Arguments.of("Roast a celebrity", "roast"),
                Arguments.of("Who is the king of Bollywood?", "popularity"),
                Arguments.of("Which actor has the best red carpet look?", "fashion"),
                Arguments.of("Who is more famous, cricketers or actors?", "comparison")
        );
    }

    @ParameterizedTest(name = "[{1}] {0}")
    @MethodSource("superficialQueries")
    void openAiReturnsEducationalPivotNotDirectSuperficialAnswer(String query, String category) throws Exception {
        TeachingRequest request = new TeachingRequest();
        request.setTopic(query);
        String prompt = PROMPT_BUILDER.buildUnifiedTeachingPrompt(request);

        String raw = callChatCompletions(prompt);
        JsonNode n = MAPPER.readTree(stripCodeFence(raw));

        boolean educational = n.path("educational").asBoolean(false);
        String refusal = n.path("refusal").asText("").strip();
        String explanation = n.path("explanation").asText("").strip();
        String example = n.path("example").asText("").strip();
        String keyPoint = n.path("keyPoint").asText("").strip();

        assertThat(educational)
                .as("Prompt class (B) expects educational=true for superficial queries [%s]", category)
                .isTrue();

        assertThat(refusal)
                .as("Refusal should be empty when educational=true [%s]", category)
                .isEmpty();

        assertThat(explanation.length())
                .as("Explanation should carry the pivot [%s]", category)
                .isGreaterThan(40);
        assertThat(example.length())
                .as("Example should add educational angle [%s]", category)
                .isGreaterThan(15);
        assertThat(keyPoint.length())
                .as("keyPoint should include redirect-style closing [%s]", category)
                .isGreaterThan(20);

        String combined = (explanation + " " + example + " " + keyPoint).toLowerCase();
        assertThat(combined)
                .as("Response should steer toward learning, not numeric looks ratings [%s]", category)
                .doesNotContain("8/10")
                .doesNotContain("9/10")
                .doesNotContain("10/10");

        String kpLower = keyPoint.toLowerCase();
        boolean redirectCue = kpLower.contains("would you like")
                || kpLower.contains("good question")
                || kpLower.contains("could be")
                || keyPoint.contains("क्या आप")
                || keyPoint.contains("जानना चाह");
        assertThat(redirectCue)
                .as("keyPoint should include a redirect cue (English or Hindi) [%s]: %s", category, keyPoint)
                .isTrue();
    }

    private static String callChatCompletions(String prompt) {
        OpenAiChatRequest req = new OpenAiChatRequest(
                model,
                List.of(new OpenAiMessage("user", prompt)));

        OpenAiChatResponse response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(req)
                .retrieve()
                .bodyToMono(OpenAiChatResponse.class)
                .block(Duration.ofSeconds(120));

        assertThat(response).isNotNull();
        assertThat(response.choices).isNotNull().isNotEmpty();
        assertThat(response.choices.get(0).message).isNotNull();
        return response.choices.get(0).message.content().trim();
    }

    private static String stripCodeFence(String raw) {
        if (raw == null) {
            return "{}";
        }
        String s = raw.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
        return s.isEmpty() ? "{}" : s;
    }
}
