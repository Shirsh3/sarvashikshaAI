package com.sarvashikshaai.service;

import com.sarvashikshaai.ai.OpenAIClient;
import com.sarvashikshaai.model.entity.ClassMaterialChunkEntity;
import com.sarvashikshaai.model.entity.ClassMaterialEntity;
import com.sarvashikshaai.repository.ClassMaterialChunkRepository;
import com.sarvashikshaai.repository.ClassMaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Async PDF → chunk → embed pipeline (separate bean so @Async + @Transactional proxies work).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClassMaterialIndexingService {

    private final ClassMaterialRepository materialRepository;
    private final ClassMaterialChunkRepository chunkRepository;
    private final FileExtractionService fileExtractionService;
    private final TextChunker textChunker;
    private final OpenAIClient openAIClient;
    private final EmbeddingSearchService embeddingSearchService;

    @Value("${sarva.materials.ocr.max-pages:20}")
    private int ocrMaxPages;

    @Async
    public void indexAsync(Long materialId) {
        try {
            indexNow(materialId);
        } catch (Exception e) {
            log.error("Material indexing failed id={}: {}", materialId, e.getMessage());
            markFailed(materialId, e.getMessage());
        }
    }

    @Transactional
    public void indexNow(Long materialId) throws Exception {
        ClassMaterialEntity m = materialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("Material not found"));
        m.setStatus(ClassMaterialEntity.STATUS_INDEXING);
        m.setErrorMessage(null);
        m.setUpdatedAt(Instant.now());
        materialRepository.save(m);

        byte[] pdf = Files.readAllBytes(Path.of(m.getStoragePath()));
        int pages = fileExtractionService.countPdfPages(pdf);
        if (pages <= 0) {
            throw new IllegalStateException("Could not read PDF pages");
        }
        String text = fileExtractionService.extractPdfTextPages(pdf, 1, pages, 0);
        boolean usedOcr = false;
        if (text == null || text.isBlank() || text.startsWith("[") || text.trim().length() < 80) {
            log.info("Little/no PDF text for material id={} — running Vision OCR (max {} pages)", materialId, ocrMaxPages);
            text = ocrPdfText(pdf, pages);
            usedOcr = true;
        }
        if (text == null || text.isBlank() || text.trim().length() < 40) {
            throw new IllegalStateException(
                    "Could not read text from this PDF (even with OCR). Try a text PDF or clearer scan.");
        }

        List<String> pieces = textChunker.chunk(text);
        if (pieces.isEmpty()) {
            throw new IllegalStateException("PDF produced no text chunks");
        }

        chunkRepository.deleteByMaterialId(materialId);
        Instant now = Instant.now();
        int idx = 0;
        for (String piece : pieces) {
            float[] emb = openAIClient.createEmbedding(piece);
            ClassMaterialChunkEntity c = new ClassMaterialChunkEntity();
            c.setMaterialId(materialId);
            c.setChunkIndex(idx++);
            c.setContent(piece);
            c.setEmbedding(embeddingSearchService.toJson(emb));
            c.setTokenEstimate(textChunker.estimateTokens(piece));
            c.setCreatedAt(now);
            chunkRepository.save(c);
        }

        m.setChunkCount(pieces.size());
        m.setStatus(ClassMaterialEntity.STATUS_READY);
        m.setErrorMessage(null);
        m.setUpdatedAt(Instant.now());
        materialRepository.save(m);
        log.info("Indexed material id={} grade={} chunks={} ocr={}", materialId, m.getGrade(), pieces.size(), usedOcr);
    }

    private String ocrPdfText(byte[] pdf, int totalPages) {
        int end = Math.min(totalPages, Math.max(1, ocrMaxPages));
        List<String> images = fileExtractionService.renderPdfPagesAsPngDataUris(pdf, 1, end, end, 144f);
        if (images.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int pageNo = 1;
        for (String dataUri : images) {
            try {
                String pageText = openAIClient.extractTextFromImage(dataUri);
                if (pageText != null && !pageText.isBlank()) {
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append("--- Page ").append(pageNo).append(" ---\n");
                    sb.append(pageText.trim());
                }
            } catch (Exception e) {
                String detail = e.getMessage();
                if (e.getCause() != null && e.getCause().getMessage() != null) {
                    detail = detail + " | " + e.getCause().getMessage();
                }
                log.warn("OCR failed for page {}: {}", pageNo, detail);
            }
            pageNo++;
        }
        return sb.toString().trim();
    }

    @Transactional
    public void markFailed(Long materialId, String message) {
        materialRepository.findById(materialId).ifPresent(m -> {
            m.setStatus(ClassMaterialEntity.STATUS_FAILED);
            String msg = message == null ? "Indexing failed" : message;
            if (msg.length() > 1900) msg = msg.substring(0, 1900);
            m.setErrorMessage(msg);
            m.setUpdatedAt(Instant.now());
            materialRepository.save(m);
        });
    }
}
