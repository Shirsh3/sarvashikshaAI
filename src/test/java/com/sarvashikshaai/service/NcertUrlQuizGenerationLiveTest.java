package com.sarvashikshaai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("openai-live")
class NcertUrlQuizGenerationLiveTest {

    private static final String NCERT_URL = "https://ncert.nic.in/textbook.php?leph1=2-8";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private UrlContentService urlContentService;

    @Autowired
    private QuizService quizService;

    @Test
    void generateAndPrintQuestionsFromNcertUrl() throws Exception {
        Assumptions.assumeTrue(hasOpenAiKey(), "Skipping live test: OpenAI key not configured");

        String extracted = urlContentService.extractContextFromUrl(NCERT_URL);
        assertThat(extracted)
                .as("Expected extracted content from NCERT URL")
                .isNotBlank();

        String questionsJson = quizService.generateQuestionsJson(
                "NCERT textbook passage",
                extracted,
                5,
                "MCQ",
                "AUTO");
        JsonNode arr = MAPPER.readTree(questionsJson);
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isGreaterThan(0);

        System.out.println("\n=== NCERT URL ===");
        System.out.println(NCERT_URL);
        System.out.println("=== GENERATED QUESTIONS (" + arr.size() + ") ===");
        for (int i = 0; i < arr.size(); i++) {
            JsonNode q = arr.get(i);
            String text = q.path("text").asText("");
            String answer = q.path("answer").asText("");
            System.out.println((i + 1) + ". " + text);
            JsonNode options = q.path("options");
            if (options.isArray() && !options.isEmpty()) {
                for (int j = 0; j < options.size(); j++) {
                    System.out.println("   - " + options.get(j).asText(""));
                }
            }
            System.out.println("   Correct: " + answer);
        }
        System.out.println("=== END ===\n");
    }

    private static boolean hasOpenAiKey() {
        String fromEnv = System.getenv("OPENAI_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) return true;
        String fromProp = System.getProperty("openai.api.key");
        if (fromProp != null && !fromProp.isBlank()) return true;
        try (InputStream in = NcertUrlQuizGenerationLiveTest.class.getClassLoader()
                .getResourceAsStream("application-local.properties")) {
            if (in == null) return false;
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            String key = p.getProperty("openai.api-key", "");
            return key != null && !key.isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }
}
