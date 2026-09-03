package com.sarvashikshaai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarvashikshaai.ai.OpenAIClient;
import com.sarvashikshaai.model.entity.ClassMaterialChunkEntity;
import com.sarvashikshaai.repository.ClassMaterialChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmbeddingSearchService {

    private final OpenAIClient openAIClient;
    private final ObjectMapper objectMapper;
    private final ClassMaterialChunkRepository chunkRepository;

    public record RankedChunk(Long materialId, int chunkIndex, String content, double score) {}

    public List<RankedChunk> search(String query, String grade, String subject, int topK) {
        return search(query, grade, subject, null, topK);
    }

    public List<RankedChunk> search(String query, String grade, String subject, Long materialId, int topK) {
        if (query == null || query.isBlank()) return List.of();
        float[] q = openAIClient.createEmbedding(query);
        if (q.length == 0) return List.of();

        String g = blankToNull(grade);
        String s = blankToNull(subject);
        List<RankedChunk> ranked = new ArrayList<>();
        for (ClassMaterialChunkEntity chunk : chunkRepository.findReadyChunks(g, s, materialId)) {
            float[] emb = parseEmbedding(chunk.getEmbedding());
            if (emb.length == 0) continue;
            ranked.add(new RankedChunk(
                    chunk.getMaterialId(),
                    chunk.getChunkIndex(),
                    chunk.getContent(),
                    cosine(q, emb)));
        }
        ranked.sort(Comparator.comparingDouble(RankedChunk::score).reversed());
        int k = topK <= 0 ? 5 : topK;
        return ranked.size() <= k ? ranked : ranked.subList(0, k);
    }

    public String formatContext(List<RankedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (RankedChunk c : chunks) {
            sb.append("[Source ").append(i++).append("]\n")
                    .append(c.content().trim())
                    .append("\n\n");
        }
        return sb.toString().trim();
    }

    public String toJson(float[] embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize embedding", e);
        }
    }

    public float[] parseEmbedding(String json) {
        if (json == null || json.isBlank()) return new float[0];
        try {
            JsonNode n = objectMapper.readTree(json);
            if (!n.isArray()) return new float[0];
            float[] out = new float[n.size()];
            for (int i = 0; i < n.size(); i++) {
                out[i] = (float) n.get(i).asDouble();
            }
            return out;
        } catch (Exception e) {
            return new float[0];
        }
    }

    public static String normalizeSubject(String subject) {
        if (subject == null || subject.isBlank()) return "GENERAL";
        return subject.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static double cosine(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        if (n == 0) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
