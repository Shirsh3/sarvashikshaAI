package com.sarvashikshaai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarvashikshaai.ai.OpenAIClient;
import com.sarvashikshaai.model.entity.ThoughtEntry;
import com.sarvashikshaai.repository.ThoughtEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThoughtConfigService {

    private final ThoughtEntryRepository thoughtRepo;
    private final OpenAIClient openAIClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Object poolLock = new Object();

    /**
     * Ensures there is a fresh pool of AI-generated Thought+Word+Habit entries
     * when all existing entries have been shown.
     */
    @Transactional
    public void ensureThoughtPool() {
        List<ThoughtEntry> unseenHi = thoughtRepo.findByShownFalseAndLanguage("hi");
        List<ThoughtEntry> unseenEn = thoughtRepo.findByShownFalseAndLanguage("en");
        if (!unseenHi.isEmpty() && !unseenEn.isEmpty()) {
            return;
        }

        // Guard against concurrent regenerations
        synchronized (poolLock) {
            unseenHi = thoughtRepo.findByShownFalseAndLanguage("hi");
            unseenEn = thoughtRepo.findByShownFalseAndLanguage("en");
            if (!unseenHi.isEmpty() && !unseenEn.isEmpty()) {
                log.debug("ThoughtConfigService.ensureThoughtPool: pools refilled by another thread (hi={}, en={}), skipping OpenAI call",
                        unseenHi.size(), unseenEn.size());
                return;
            }

            log.info("ThoughtConfigService.ensureThoughtPool: missing entries (hi={}, en={}), calling OpenAI to regenerate pools",
                    unseenHi.size(), unseenEn.size());
            long t0 = System.currentTimeMillis();

            String prompt = """
You are generating JSON for a school morning assembly.

Return STRICTLY a JSON object with two properties:
- "entriesHi": array of 10 items for Hindi.
- "entriesEn": array of 10 items for English.

Each item in "entriesHi" must have EXACTLY these string fields:
- thoughtHi  : A Hindi-only "Thought of the Day" sentence (no English words).
- wordEnglish: Single English "Word of the Day" (e.g. "Integrity").
- wordHindi  : Short Hindi meaning of that word (e.g. "ईमानदारी").
- habit      : One short, actionable good habit in Hinglish or Hindi.

Each item in "entriesEn" must have EXACTLY these string fields:
- thoughtEn  : An English-only "Thought of the Day" sentence (no Hindi script).
- wordEnglish: Single English "Word of the Day" (e.g. "Integrity").
- wordHindi  : Short Hindi meaning of that word (e.g. "ईमानदारी").
- habit      : One short, actionable good habit in simple English.

Rules:
- 10 distinct entries in entriesHi.
- 10 distinct entries in entriesEn.
- Simple language for children aged 6–15.
- Focus themes: honesty, kindness, discipline, learning, respect, teamwork.
- thoughtEn MUST NOT be a direct translation of any Hindi thoughtHi. They should be independent thoughts.
- Keep style consistent and not too random (imagine temperature around 0.3).
- Do NOT include numbering or any extra text outside the JSON.

Example of the overall JSON SHAPE (values are just examples, do NOT reuse):
{
  "entriesHi": [
    {
      "thoughtHi": "सच्ची मेहनत से हर सपना पूरा होता है।",
      "wordEnglish": "Integrity",
      "wordHindi": "ईमानदारी",
      "habit": "Aaj kisi ki bina bole help karein."
    }
  ],
  "entriesEn": [
    {
      "thoughtEn": "Small honest efforts create big success.",
      "wordEnglish": "Integrity",
      "wordHindi": "ईमानदारी",
      "habit": "Help one classmate today without being asked."
    }
  ]
}
""";

            String raw = openAIClient.generateAssemblyCompletion(prompt);
            long t1 = System.currentTimeMillis();
            log.info("ThoughtConfigService.ensureThoughtPool: OpenAI call completed in {} ms", (t1 - t0));
            try {
                JsonNode root = objectMapper.readTree(raw);
                JsonNode entriesHi = root.path("entriesHi");
                JsonNode entriesEn = root.path("entriesEn");

                int savedHi = 0;
                int savedEn = 0;

                if (entriesHi.isArray()) {
                    for (JsonNode node : entriesHi) {
                        ThoughtEntry entry = new ThoughtEntry();
                        entry.setLanguage("hi");
                        entry.setThoughtHi(safeText(node, "thoughtHi"));
                        entry.setWordEnglish(safeText(node, "wordEnglish"));
                        entry.setWordHindi(safeText(node, "wordHindi"));
                        entry.setHabit(safeText(node, "habit"));
                        entry.setShown(false);
                        thoughtRepo.save(entry);
                        savedHi++;
                    }
                } else {
                    log.warn("ThoughtConfigService.ensureThoughtPool: 'entriesHi' array missing or not an array in OpenAI response");
                }

                if (entriesEn.isArray()) {
                    for (JsonNode node : entriesEn) {
                        ThoughtEntry entry = new ThoughtEntry();
                        entry.setLanguage("en");
                        entry.setThoughtEn(safeText(node, "thoughtEn"));
                        entry.setWordEnglish(safeText(node, "wordEnglish"));
                        entry.setWordHindi(safeText(node, "wordHindi"));
                        entry.setHabit(safeText(node, "habit"));
                        entry.setShown(false);
                        thoughtRepo.save(entry);
                        savedEn++;
                    }
                } else {
                    log.warn("ThoughtConfigService.ensureThoughtPool: 'entriesEn' array missing or not an array in OpenAI response");
                }

                long t2 = System.currentTimeMillis();
                log.info("ThoughtConfigService.ensureThoughtPool: saved {} Hindi and {} English ThoughtEntry rows in {} ms",
                        savedHi, savedEn, (t2 - t1));
            } catch (Exception e) {
                log.error("Failed to parse OpenAI JSON for Thought pools: {}", e.getMessage());
            }
        }
    }

    public record ThoughtPair(ThoughtEntry hi, ThoughtEntry en) {}

    @Transactional
    public ThoughtPair pickRandomPairAndMarkShown() {
        List<ThoughtEntry> unseenHi = thoughtRepo.findByShownFalseAndLanguage("hi");
        List<ThoughtEntry> unseenEn = thoughtRepo.findByShownFalseAndLanguage("en");
        log.debug("ThoughtConfigService.pickRandomPairAndMarkShown: unseen hi={}, unseen en={}", unseenHi.size(), unseenEn.size());

        if (unseenHi.isEmpty() || unseenEn.isEmpty()) {
            log.info("ThoughtConfigService.pickRandomPairAndMarkShown: one or both pools empty, triggering ensureThoughtPool");
            ensureThoughtPool();
            unseenHi = thoughtRepo.findByShownFalseAndLanguage("hi");
            unseenEn = thoughtRepo.findByShownFalseAndLanguage("en");
            if (unseenHi.isEmpty() || unseenEn.isEmpty()) {
                log.warn("ThoughtConfigService.pickRandomPairAndMarkShown: still missing entries after ensureThoughtPool, using fallback");
                ThoughtEntry hiFallback = new ThoughtEntry();
                hiFallback.setLanguage("hi");
                hiFallback.setThoughtHi("मेहनत और लगन से हर सपना पूरा होता है।");
                hiFallback.setWordEnglish("Perseverance");
                hiFallback.setWordHindi("लगन");
                hiFallback.setHabit("Aaj kisi ka himmat badhayein ek achhe shabd se.");
                hiFallback.setShown(true);

                ThoughtEntry enFallback = new ThoughtEntry();
                enFallback.setLanguage("en");
                enFallback.setThoughtEn("With effort and discipline, every dream can come true.");
                enFallback.setWordEnglish("Perseverance");
                enFallback.setWordHindi("लगन");
                enFallback.setHabit("Encourage someone today with kind words.");
                enFallback.setShown(true);

                return new ThoughtPair(hiFallback, enFallback);
            }
        }

        ThoughtEntry hi = unseenHi.get((int) (Math.random() * unseenHi.size()));
        ThoughtEntry en = unseenEn.get((int) (Math.random() * unseenEn.size()));

        hi.setShown(true);
        en.setShown(true);
        thoughtRepo.save(hi);
        thoughtRepo.save(en);

        log.debug("ThoughtConfigService.pickRandomPairAndMarkShown: picked hiId={}, enId={}", hi.getId(), en.getId());
        return new ThoughtPair(hi, en);
    }

    /**
     * Ensures a pool exists; does not delete existing entries.
     */
    @Transactional
    public void regeneratePool() {
        log.info("ThoughtConfigService.regeneratePool: ensuring pool exists (no delete)");
        ensureThoughtPool();
    }

    private String safeText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }
}

