package com.sarvashikshaai.ai;

import com.sarvashikshaai.model.TeachingRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stress-tests that the unified teaching prompt (used for typed + voice-as-text topics)
 * always embeds {@link EducationalRedirectionPolicy} and the user question, so the model
 * can classify (B) superficial queries and apply redirect logic.
 * <p>
 * Does not call OpenAI — verifies prompt wiring only.
 */
class EducationalRedirectionPromptCoverageTest {

    private static final PromptBuilder PROMPT_BUILDER = new PromptBuilder();

    static Stream<String> nonEducationalQueries() throws Exception {
        return loadQueriesFromClasspath().stream();
    }

    private static List<String> loadQueriesFromClasspath() throws Exception {
        InputStream in = EducationalRedirectionPromptCoverageTest.class.getResourceAsStream(
                "/non-educational-stress-queries.txt");
        assertThat(in).as("classpath resource /non-educational-stress-queries.txt").isNotNull();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return br.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
        }
    }

    /** Mirrors {@link PromptBuilder} sanitisation so we can assert the QUESTION line matches. */
    private static String safeOneLineForAssertion(String topic) {
        if (topic == null) {
            return "";
        }
        return topic.replace('\n', ' ').replace('\r', ' ').replace('"', '\'').strip();
    }

    @Test
    void stressFileContainsExactly100Queries() throws Exception {
        List<String> queries = loadQueriesFromClasspath();
        assertThat(queries).hasSize(100);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("nonEducationalQueries")
    void teachingPromptEmbedsRedirectionPolicyAndQuestion(String query) {
        TeachingRequest request = new TeachingRequest();
        request.setTopic(query);

        String prompt = PROMPT_BUILDER.buildUnifiedTeachingPrompt(request);

        assertThat(prompt)
                .as("Policy block must be present for query: %s", query)
                .contains("INPUT MAY COME FROM TYPED TEXT, VOICE TRANSCRIPTION");

        assertThat(prompt)
                .contains("ALLOWED BUT NON-EDUCATIONAL")
                .contains("(B) Allowed but superficial")
                .contains("Would you like to learn")
                .contains("QUESTION:");

        String embedded = safeOneLineForAssertion(query);
        assertThat(prompt.stripTrailing())
                .as("Prompt must end with the sanitised user question (trailing newline allowed)")
                .endsWith(embedded);
    }

    @Test
    void policyBlockIsStableAnchorForSubstringCheck() {
        assertThat(EducationalRedirectionPolicy.PROMPT_BLOCK)
                .contains("TRANSFORMATION")
                .contains("NOT rate people");
    }
}
