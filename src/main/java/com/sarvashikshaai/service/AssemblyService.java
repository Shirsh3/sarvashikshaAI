package com.sarvashikshaai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarvashikshaai.model.entity.ThoughtEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides all content for the Morning Assembly page.
 *
 * Thought of the Day (see {@link ThoughtConfigService}):
 *   - DB pools of AI-generated rows; each OpenAI refill adds 10 Hindi and 10 English entries.
 *   - Each {@link #getDailyThought(String)} picks one random unseen Hindi and one random unseen
 *     English entry, then marks both shown.
 *   - When either pool is empty, {@link ThoughtConfigService#ensureThoughtPool()} calls OpenAI once
 *     to refill. Assembly loads thought via {@code GET /api/assembly/daily-thought} so the UI can
 *     show a global loader during that call.
 *
 * Assembly text (Anthem, Pledge, Prayer):
 *   Loaded once from src/main/resources/data/assembly-content.json.
 *
 * YouTube links:
 *   Pulled from StudentSyncService (which reads the teacher's "Assembly" sheet tab).
 */
@Service
@Slf4j
public class AssemblyService {

    private static final String ZEN_QUOTES_URL = "https://zenquotes.io/api/today";

    private final ObjectMapper mapper = new ObjectMapper();
    private final WebClient    zenClient;
    private final ThoughtConfigService thoughtConfigService;
    private final AssemblyConfigService assemblyConfigService;

    /** Section name keys used to look up teacher's YouTube links */
    public static final String KEY_ANTHEM  = "national anthem";
    public static final String KEY_PRAYER  = "morning prayer";
    public static final String KEY_PLEDGE  = "pledge";
    public static final String KEY_HINDI   = "hindi prayer";

    // ── Loaded once on first call ─────────────────────────────────────────────
    private List<String> hindiQuotes;
    private List<String> englishQuotes;
    private Map<String, Object> assemblyContent;   // keys: anthem, pledge, prayer
    private volatile Map<String, String> cachedLinks;
    private volatile long cachedLinksAtMs = 0L;

    // ── Record returned to the controller ─────────────────────────────────────
    public record DailyThought(
            String thoughtHi,
            String thoughtEn,
            String wordEnglish,
            String wordHindi,
            String habit
    ) {}
    public record AssemblySection(String text, String meaning) {}
    public record AssemblyContent(AssemblySection anthem, AssemblySection pledge, AssemblySection prayer) {}

    public AssemblyService(ThoughtConfigService thoughtConfigService,
                           AssemblyConfigService assemblyConfigService) {
        this.thoughtConfigService = thoughtConfigService;
        this.assemblyConfigService = assemblyConfigService;
        this.zenClient   = WebClient.builder().baseUrl(ZEN_QUOTES_URL).build();
        loadJsonFiles();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns today's Thought of the Day (Hindi + English) plus
     * Word of the Day (English + Hindi meaning) and a Habit of the Day.
     *
     * Hindi and English thoughts are drawn from independent pools, but
     * the Word + Habit line currently follows the Hindi-style entry.
     */
    public DailyThought getDailyThought(String teacherEmail) {
        long t0 = System.currentTimeMillis();
        thoughtConfigService.ensureThoughtPool();
        ThoughtConfigService.ThoughtPair pair = thoughtConfigService.pickRandomPairAndMarkShown();
        long t1 = System.currentTimeMillis();
        log.debug("AssemblyService.getDailyThought: total time {} ms (including any pool/OpenAI work)", (t1 - t0));

        ThoughtEntry hi = pair.hi();
        ThoughtEntry en = pair.en();

        return new DailyThought(
                hi.getThoughtHi(),
                en.getThoughtEn(),
                hi.getWordEnglish(),
                hi.getWordHindi(),
                hi.getHabit()
        );
    }

    public AssemblyContent getAssemblyContent() {
        return new AssemblyContent(
            section("anthem"),
            section("pledge"),
            section("prayer")
        );
    }

    /**
     * Returns YouTube links for assembly sections from in-app config.
     * Keys: "national anthem", "morning prayer", "pledge", "hindi prayer"
     */
    public Map<String, String> getAssemblyLinks(String teacherEmail) {
        long now = System.currentTimeMillis();
        if (cachedLinks != null && (now - cachedLinksAtMs) < 60_000L) {
            return cachedLinks;
        }
        Map<String, String> fresh = assemblyConfigService.getLinks();
        cachedLinks = fresh;
        cachedLinksAtMs = now;
        return fresh;
    }

    private static final Pattern YOUTUBE_11 = Pattern.compile("([a-zA-Z0-9_-]{11})(?![a-zA-Z0-9_-])");

    /**
     * Extracts the YouTube video ID from common URL formats (watch, embed, shorts, youtu.be, live).
     * Returns null if the URL is blank or unparseable.
     */
    public static String extractVideoId(String url) {
        if (url == null || url.isBlank()) return null;
        url = url.trim();
        // /embed/VIDEOID and /v/VIDEOID
        int embed = url.indexOf("/embed/");
        if (embed >= 0) {
            String id = url.substring(embed + 7).split("[?&#/]")[0];
            if (isPlausibleId(id)) return id;
        }
        int slashV = url.indexOf("/v/");
        if (slashV >= 0 && !url.contains("/watch")) {
            String id = url.substring(slashV + 3).split("[?&#/]")[0];
            if (isPlausibleId(id)) return id;
        }
        // youtu.be short links
        if (url.contains("youtu.be/")) {
            String id = url.substring(url.lastIndexOf("youtu.be/") + 9).split("[?&#/]")[0];
            if (isPlausibleId(id)) return id;
        }
        // standard watch?v=
        if (url.contains("v=")) {
            String after = url.substring(url.indexOf("v=") + 2);
            String id = after.split("[?&#/]")[0];
            if (isPlausibleId(id)) return id;
        }
        // shorts
        if (url.contains("/shorts/")) {
            String after = url.substring(url.lastIndexOf("/shorts/") + 8);
            String id = after.split("[?&#/]")[0];
            if (isPlausibleId(id)) return id;
        }
        // /live/VIDEOID
        if (url.contains("/live/")) {
            String after = url.substring(url.lastIndexOf("/live/") + 6);
            String id = after.split("[?&#/]")[0];
            if (isPlausibleId(id)) return id;
        }
        // Last resort: first 11-char YouTube-like id in the string
        Matcher m = YOUTUBE_11.matcher(url);
        if (m.find()) return m.group(1);
        return null;
    }

    private static boolean isPlausibleId(String id) {
        return id != null && id.length() == 11 && id.matches("[a-zA-Z0-9_-]+");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Picks a quote from the primary list (e.g. sheet quotes) if non-empty,
     * otherwise falls back to the secondary list (JSON bundle).
     */
    private String pickFromList(List<String> primary, List<String> fallback) {
        List<String> source = (primary != null && !primary.isEmpty()) ? primary : fallback;
        if (source == null || source.isEmpty()) return "मेहनत और लगन से हर सपना पूरा होता है।";
        int idx = LocalDate.now().getDayOfYear() % source.size();
        return source.get(idx);
    }

    private String pickEnglishWithFallback(List<String> dbEnglish) {
        if (dbEnglish != null && !dbEnglish.isEmpty()) {
            int idx = LocalDate.now().getDayOfYear() % dbEnglish.size();
            return dbEnglish.get(idx);
        }
        return fetchEnglish();
    }

    private String fetchEnglish() {
        try {
            String body = zenClient.get()
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(4))
                    .block();
            if (body != null) {
                JsonNode arr = mapper.readTree(body);
                if (arr.isArray() && !arr.isEmpty()) {
                    String quote  = arr.get(0).path("q").asText("");
                    String author = arr.get(0).path("a").asText("");
                    if (!quote.isBlank()) {
                        return author.isBlank() ? quote : quote + " — " + author;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("ZenQuotes API unavailable ({}), using fallback.", e.getMessage());
        }
        return pickEnglishFallback();
    }

    private String pickEnglishFallback() {
        if (englishQuotes == null || englishQuotes.isEmpty())
            return "The more that you read, the more things you will know.";
        int idx = LocalDate.now().getDayOfYear() % englishQuotes.size();
        return englishQuotes.get(idx);
    }

    @SuppressWarnings("unchecked")
    private AssemblySection section(String key) {
        if (assemblyContent == null) return new AssemblySection("", "");
        Object node = assemblyContent.get(key);
        if (node instanceof Map<?, ?> m) {
            String text    = (String) ((Map<String, Object>) m).getOrDefault("text", "");
            String meaning = (String) ((Map<String, Object>) m).getOrDefault("meaning", "");
            return new AssemblySection(text, meaning);
        }
        return new AssemblySection("", "");
    }

    private void loadJsonFiles() {
        try (InputStream hi = new ClassPathResource("data/thoughts-hi.json").getInputStream()) {
            hindiQuotes = mapper.readValue(hi, new TypeReference<>() {});
            log.info("Loaded {} Hindi thoughts", hindiQuotes.size());
        } catch (Exception e) {
            log.error("Failed to load thoughts-hi.json: {}", e.getMessage());
        }

        try (InputStream en = new ClassPathResource("data/thoughts-en.json").getInputStream()) {
            englishQuotes = mapper.readValue(en, new TypeReference<>() {});
            log.info("Loaded {} English thoughts", englishQuotes.size());
        } catch (Exception e) {
            log.error("Failed to load thoughts-en.json: {}", e.getMessage());
        }

        try (InputStream ac = new ClassPathResource("data/assembly-content.json").getInputStream()) {
            assemblyContent = mapper.readValue(ac, new TypeReference<>() {});
            log.info("Loaded assembly content (sections: {})", assemblyContent.keySet());
        } catch (Exception e) {
            log.error("Failed to load assembly-content.json: {}", e.getMessage());
        }
    }
}
