package com.sarvashikshaai.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

/**
 * Extracts usable content from an uploaded file.
 *
 * PDF  → plain text (via Apache PDFBox)
 * Image→ base64 data-URI (for OpenAI Vision API)
 */
@Service
@Slf4j
public class FileExtractionService {

    private static final long MAX_BYTES = 5 * 1024 * 1024L; // 5 MB

    public enum FileType { PDF, IMAGE, UNSUPPORTED }

    public FileType detectType(MultipartFile file) {
        String name = (file.getOriginalFilename() == null ? "" : file.getOriginalFilename()).toLowerCase();
        String mime = (file.getContentType()       == null ? "" : file.getContentType()).toLowerCase();
        if (name.endsWith(".pdf") || mime.contains("pdf"))            return FileType.PDF;
        if (mime.startsWith("image/") || name.matches(".*\\.(jpg|jpeg|png|webp|gif)$"))
                                                                       return FileType.IMAGE;
        return FileType.UNSUPPORTED;
    }

    /**
     * For PDF uploads: extract all text content.
     * Returns empty string on failure.
     */
    public String extractPdfText(MultipartFile file) {
        if (file == null || file.isEmpty()) return "";
        if (file.getSize() > MAX_BYTES) return "[File too large — maximum 5 MB]";
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc).trim();
            // Cap at ~6000 chars to stay within token limits
            return text.length() > 6000 ? text.substring(0, 6000) + "...[truncated]" : text;
        } catch (Exception e) {
            log.error("PDF extraction failed: {}", e.getMessage());
            return "[Could not read PDF content]";
        }
    }

    /**
     * For image uploads: encode as a base64 data URI suitable for OpenAI Vision.
     * Returns null on failure.
     */
    public String encodeImageToBase64(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        if (file.getSize() > MAX_BYTES) return null;
        try {
            String mime    = file.getContentType() != null ? file.getContentType() : "image/jpeg";
            String encoded = Base64.getEncoder().encodeToString(file.getBytes());
            return "data:" + mime + ";base64," + encoded;
        } catch (Exception e) {
            log.error("Image encoding failed: {}", e.getMessage());
            return null;
        }
    }
}
