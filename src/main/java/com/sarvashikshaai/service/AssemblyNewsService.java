package com.sarvashikshaai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.sarvashikshaai.ai.OpenAIClient;
import com.sarvashikshaai.model.AssemblyNews;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Fetches and filters student-friendly news from The Hindu RSS feed.
 * Evaluates student reading attempts via OpenAI.
 *
 * News is cached once per day (ConcurrentHashMap keyed by date string).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssemblyNewsService {

    private static final String RSS_URL    = "https://www.thehindu.com/news/national/feeder/default.rss";
    private static final int    MAX_NEWS   = 5;
    private static final int    RSS_TIMEOUT_MS = 8000;

    private static final Set<String> REJECT_KEYWORDS = Set.of(
        "crime", "murder", "politics", "political", "violence", "war",
        "riot", "election", "elections", "minister", "terror", "terrorism",
        "bomb", "attack", "rape", "assault", "corruption", "scam", "scandal",
        "arrested", "killed", "dead", "death", "shooting", "blast"
    );

    private static final Set<String> PREFER_KEYWORDS = Set.of(
        "science", "education", "technology", "environment", "space", "sports",
        "health", "school", "student", "students", "research", "discovery",
        "invention", "nature", "wildlife", "book", "art", "culture", "isro",
        "nasa", "satellite", "climate", "forest", "ocean", "animal", "plant",
        "medicine", "vaccine", "award", "achievement", "innovation", "robot",
        "computer", "internet", "solar", "energy", "water", "clean"
    );

    private static final Pattern HTML_TAG   = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s{2,}");

    /** Only tech, space, science, innovation — for long-form fetched news. */
    private static final Set<String> TECH_SCIENCE_KEYWORDS = Set.of(
        "technology", "space", "science", "innovation", "research", "isro", "nasa",
        "satellite", "discovery", "invention", "robot", "computer", "climate",
        "medicine", "vaccine", "solar", "energy", "environment"
    );

    private static final int MAX_LONG_NEWS = 5;

    private final ObjectMapper  mapper    = new ObjectMapper();
    private final OpenAIClient  openAIClient;

    /** Daily news cache: date-string → list of AssemblyNews */
    private final Map<String, List<AssemblyNews>> newsCache = new ConcurrentHashMap<>();

    /** Override: when set, GET /reading uses this instead of getDailyNews(). */
    private volatile List<AssemblyNews> readingOverride = null;

    // ── Records ───────────────────────────────────────────────────────────────

    public record ReadingRequest(
        String studentName,
        String articleTitle,
        String originalText,
        String spokenText
    ) {}

    public record ReadingFeedback(
        // Scores (1-10)
        int    fluencyScore,
        int    pronunciationScore,
        int    paceScore,
        int    accuracyScore,
        int    confidenceScore,
        // Word counts
        int    originalWordCount,
        int    spokenWordCount,
        int    accuracyPercent,
        // Qualitative
        String hindiFeedback,
        String englishFeedback,
        String comprehensionQuestion,
        String difficultWords,
        String goodWords,
        String improvementTip
    ) {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns today's filtered news list.
     * Fetched once per day; subsequent calls return cached result.
     */
    public List<AssemblyNews> getDailyNews() {
        String today = LocalDate.now().toString();
        return newsCache.computeIfAbsent(today, d -> fetchAndFilter());
    }

    /**
     * Returns reading content to show on the Reading page.
     * Uses teacher-set override (generated or fetched long news) if present, else getDailyNews().
     */
    public List<AssemblyNews> getReadingContent() {
        return readingOverride != null ? readingOverride : getDailyNews();
    }

    /**
     * Clears the reading override so the page falls back to default daily news.
     */
    public void clearReadingOverride() {
        readingOverride = null;
    }

    /**
     * Generates a single reading article from the teacher's prompt (80–100 lines, student-friendly).
     * Sets readingOverride and returns the new list.
     */
    public List<AssemblyNews> generateFromPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt cannot be empty");
        }
        String escaped = prompt.replace("\"", "\\\"").replace("\n", " ");
        String aiPrompt = """
            You are an experienced school teacher in India. Generate ONE student-friendly reading article for ages 8-16.

            Teacher's request: "%s"

            Respond with ONLY valid JSON (no markdown, no extra text):
            {
              "title": "<catchy title for the article>",
              "body": "<full article body, 80 to 100 lines of text, roughly 800-1200 words. Use newlines between paragraphs. Suitable for reading aloud in class. English only.>"
            }
            """.formatted(escaped);

        try {
            String raw = openAIClient.generateCompletion(aiPrompt);
            raw = raw.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            JsonNode n = mapper.readTree(raw);
            String title = n.path("title").asText("Generated reading");
            String body = n.path("body").asText("");
            AssemblyNews item = new AssemblyNews(title, body, "");
            readingOverride = List.of(item);
            log.info("Generated reading from prompt, title: {}", title);
            return readingOverride;
        } catch (Exception e) {
            log.error("Generate from prompt failed: {}", e.getMessage());
            throw new IllegalStateException("Could not generate reading content. Please try again.", e);
        }
    }

    /**
     * Fetches RSS, keeps only tech/space/science/innovation items, expands each to 80–100 lines via AI.
     * Sets readingOverride and returns the new list.
     */
    public List<AssemblyNews> getTechScienceNewsLong() {
        List<SyndEntry> techEntries = fetchTechScienceEntries();
        if (techEntries.isEmpty()) {
            readingOverride = fallback();
            return readingOverride;
        }
        List<AssemblyNews> result = new ArrayList<>();
        for (int i = 0; i < Math.min(techEntries.size(), MAX_LONG_NEWS); i++) {
            SyndEntry entry = techEntries.get(i);
            String title = entry.getTitle() == null ? "" : entry.getTitle().trim();
            String shortDesc = entry.getDescription() == null ? "" : entry.getDescription().getValue();
            String shortSummary = cleanSummary(shortDesc);
            String link = entry.getLink() == null ? "" : entry.getLink();
            String longBody = expandToLongArticle(title, shortSummary);
            result.add(new AssemblyNews(title, longBody, link));
        }
        readingOverride = result;
        log.info("Fetched {} tech/science long-form news items", result.size());
        return readingOverride;
    }

    /**
     * Fetches RSS and returns only entries that match tech/space/science/innovation (and pass reject filter).
     */
    private List<SyndEntry> fetchTechScienceEntries() {
        try {
            URL url = new URL(RSS_URL);
            var conn = url.openConnection();
            conn.setConnectTimeout(RSS_TIMEOUT_MS);
            conn.setReadTimeout(RSS_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "SarvashikshaAI/1.0");

            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed;
            try (XmlReader reader = new XmlReader(conn.getInputStream())) {
                feed = input.build(reader);
            }
            List<SyndEntry> entries = feed.getEntries();
            List<SyndEntry> out = new ArrayList<>();
            for (SyndEntry entry : entries) {
                if (out.size() >= MAX_LONG_NEWS * 2) break;
                String title = entry.getTitle() == null ? "" : entry.getTitle().trim();
                String desc = entry.getDescription() == null ? "" : entry.getDescription().getValue();
                String combined = (title + " " + desc).toLowerCase();
                if (shouldReject(combined)) continue;
                if (!matchesTechScience(combined)) continue;
                out.add(entry);
            }
            return out;
        } catch (Exception e) {
            log.error("RSS fetch for tech news failed: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean matchesTechScience(String text) {
        for (String kw : TECH_SCIENCE_KEYWORDS) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private String expandToLongArticle(String title, String shortSummary) {
        String escapedTitle = title.replace("\"", "\\\"");
        String escapedSummary = (shortSummary != null ? shortSummary : "").replace("\"", "\\\"").replace("\n", " ");
        String prompt = """
            You are an experienced school teacher. Expand this news snippet into a single student-friendly reading article.

            Title: "%s"
            Short summary: "%s"

            Write ONE article body that is 80 to 100 lines long (roughly 800-1200 words). Use newlines between paragraphs.
            Keep it suitable for ages 8-16, in English. Do not include the title in the body.
            Respond with ONLY the article body text, no title, no JSON, no quotes.
            """.formatted(escapedTitle, escapedSummary);

        try {
            return openAIClient.generateCompletion(prompt).trim();
        } catch (Exception e) {
            log.warn("AI expand failed for '{}', using short summary", title);
            return shortSummary != null && !shortSummary.isBlank() ? shortSummary : "Content not available.";
        }
    }

    /**
     * Calls OpenAI to evaluate a student's oral reading of a news article.
     * Returns structured feedback with Hindi, English, score, and comprehension question.
     */
    public ReadingFeedback evaluateReading(ReadingRequest req) {
        int origWords  = countWords(req.originalText());
        int spokenWords = countWords(req.spokenText());

        String prompt = """
            You are a warm, encouraging reading coach for school students aged 8-16 in India.

            Article title: "%s"
            Original article text: "%s"
            What the student actually read aloud: "%s"

            Carefully compare the original text with what the student said, then respond ONLY with this JSON \
            (no markdown, no extra text outside the JSON). Do not use or request any student or personal names.
            {
              "fluencyScore":        <integer 1-10, overall smoothness and flow>,
              "pronunciationScore":  <integer 1-10, clarity of word pronunciation>,
              "paceScore":           <integer 1-10, 10=perfect pace, low=too fast or too slow>,
              "accuracyScore":       <integer 1-10, how closely the spoken text matches original words>,
              "confidenceScore":     <integer 1-10, confidence and hesitation level>,
              "accuracyPercent":     <integer 0-100, estimated percentage of original words correctly spoken>,
              "difficultWords":      "<comma-separated list of up to 5 words the student struggled with or skipped, empty string if none>",
              "goodWords":           "<comma-separated list of up to 5 words the student pronounced particularly well>",
              "hindiFeedback":       "<2-3 warm sentences in Hindi: one strength, one thing to improve, encouraging close. Do not use any personal name.>",
              "englishFeedback":     "<Same content as hindiFeedback but in English. Do not use any personal name.>",
              "comprehensionQuestion": "<One simple question in Hindi about the article content to check understanding>",
              "improvementTip":      "<One short, specific, actionable tip in English for next practice session>"
            }
            """.formatted(
                req.articleTitle(),
                req.originalText(),
                req.spokenText()
            );

        try {
            String raw = openAIClient.generateCompletion(prompt);
            raw = raw.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            JsonNode n = mapper.readTree(raw);
            int accuracyPct = n.path("accuracyPercent").asInt(computeSimpleAccuracy(req.originalText(), req.spokenText()));
            return new ReadingFeedback(
                n.path("fluencyScore").asInt(5),
                n.path("pronunciationScore").asInt(5),
                n.path("paceScore").asInt(5),
                n.path("accuracyScore").asInt(5),
                n.path("confidenceScore").asInt(5),
                origWords,
                spokenWords,
                accuracyPct,
                n.path("hindiFeedback").asText(""),
                n.path("englishFeedback").asText(""),
                n.path("comprehensionQuestion").asText(""),
                n.path("difficultWords").asText(""),
                n.path("goodWords").asText(""),
                n.path("improvementTip").asText("")
            );
        } catch (Exception e) {
            log.error("Reading evaluation failed: {}", e.getMessage());
            int acc = computeSimpleAccuracy(req.originalText(), req.spokenText());
            return new ReadingFeedback(
                5, 5, 5, 5, 5,
                origWords, spokenWords, acc,
                "पढ़ने का प्रयास अच्छा था! अगली बार और ध्यान से पढ़ो।",
                "Good attempt! Keep practising.",
                "", "", "", "Practice reading one sentence at a time."
            );
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<AssemblyNews> fetchAndFilter() {
        try {
            URL url = new URL(RSS_URL);
            var conn = url.openConnection();
            conn.setConnectTimeout(RSS_TIMEOUT_MS);
            conn.setReadTimeout(RSS_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "SarvashikshaAI/1.0");

            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed;
            try (XmlReader reader = new XmlReader(conn.getInputStream())) {
                feed = input.build(reader);
            }

            List<SyndEntry> entries = feed.getEntries();
            List<AssemblyNews> preferred  = new ArrayList<>();
            List<AssemblyNews> acceptable = new ArrayList<>();

            for (SyndEntry entry : entries) {
                if (preferred.size() + acceptable.size() >= MAX_NEWS * 3) break;

                String title = entry.getTitle() == null ? "" : entry.getTitle().trim();
                String desc  = entry.getDescription() == null ? ""
                               : entry.getDescription().getValue();
                String combined = (title + " " + desc).toLowerCase();
                String link  = entry.getLink() == null ? "" : entry.getLink();

                if (shouldReject(combined)) continue;

                String summary = cleanSummary(desc);
                AssemblyNews item = new AssemblyNews(title, summary, link);

                if (shouldPrefer(combined)) preferred.add(item);
                else acceptable.add(item);
            }

            // Fill result: preferred first, then acceptable up to MAX_NEWS
            List<AssemblyNews> result = new ArrayList<>();
            result.addAll(preferred);
            result.addAll(acceptable);
            result = result.stream().limit(MAX_NEWS).toList();

            if (result.isEmpty()) return fallback();
            log.info("Fetched {} news items ({} preferred, {} acceptable)",
                      result.size(), preferred.size(), acceptable.size());
            return result;

        } catch (Exception e) {
            log.error("RSS fetch failed: {}", e.getMessage());
            return fallback();
        }
    }

    private boolean shouldReject(String text) {
        for (String kw : REJECT_KEYWORDS) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private boolean shouldPrefer(String text) {
        for (String kw : PREFER_KEYWORDS) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private String cleanSummary(String html) {
        if (html == null || html.isBlank()) return "";
        // Strip HTML tags
        String plain = HTML_TAG.matcher(html).replaceAll(" ");
        plain = WHITESPACE.matcher(plain).replaceAll(" ").trim();
        // Trim to first 2 sentences
        String[] sentences = plain.split("(?<=[.!?])\\s+");
        if (sentences.length >= 2) {
            plain = sentences[0] + " " + sentences[1];
        }
        // Hard cap at 220 chars
        if (plain.length() > 220) {
            plain = plain.substring(0, 217) + "...";
        }
        return plain;
    }

    private List<AssemblyNews> fallback() {
        return List.of(new AssemblyNews(
            "Today's news is not available.",
            "Please check your internet connection and try again later.",
            ""
        ));
    }

    /** Count non-empty words in a string. */
    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    /**
     * Simple local accuracy estimate: % of original words (lowercased, stripped)
     * that appear in the spoken text. Used as a fallback if OpenAI call fails.
     */
    private int computeSimpleAccuracy(String original, String spoken) {
        if (original == null || original.isBlank() || spoken == null || spoken.isBlank()) return 0;
        Set<String> spokenWords = Set.of(spoken.toLowerCase().replaceAll("[^a-z\\s]", "").trim().split("\\s+"));
        String[] origWords = original.toLowerCase().replaceAll("[^a-z\\s]", "").trim().split("\\s+");
        if (origWords.length == 0) return 0;
        long matched = java.util.Arrays.stream(origWords).filter(spokenWords::contains).count();
        return (int) Math.round(100.0 * matched / origWords.length);
    }
}
