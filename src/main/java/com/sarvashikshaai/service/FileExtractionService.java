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

    // Upload safety limit (user file uploads / screenshots)
    private static final long MAX_UPLOAD_BYTES = 5 * 1024 * 1024L; // 5 MB
    // Remote NCERT PDFs can be larger; allow a higher ceiling for page-window extraction.
    private static final long MAX_REMOTE_PDF_BYTES = 25L * 1024 * 1024L; // 25 MB

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
        if (file.getSize() > MAX_UPLOAD_BYTES) return "[File too large — maximum 5 MB]";
        try {
            return extractPdfText(file.getBytes());
        } catch (Exception e) {
            log.error("PDF extraction failed: {}", e.getMessage());
            return "[Could not read PDF content]";
        }
    }

    /**
     * Extract text from raw PDF bytes (e.g. downloaded remote PDFs).
     */
    public String extractPdfText(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) return "";
        if (pdfBytes.length > MAX_REMOTE_PDF_BYTES) return "[PDF too large to process]";
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc).trim();
            // Cap at ~6000 chars to stay within token limits
            return text.length() > 6000 ? text.substring(0, 6000) + "...[truncated]" : text;
        } catch (Exception e) {
            log.error("PDF extraction failed: {}", e.getMessage());
            return "[Could not read PDF content]";
        }
    }

    /** Page count for a PDF (for page-window UI). Returns 0 on failure. */
    public int countPdfPages(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) return 0;
        if (pdfBytes.length > MAX_REMOTE_PDF_BYTES) return 0;
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return doc.getNumberOfPages();
        } catch (Exception e) {
            log.error("PDF page count failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Extract text from a page range (1-indexed, inclusive). Returns empty string on failure.
     * This is intentionally not truncated to 6k; caller decides their own cap.
     */
    public String extractPdfTextPages(byte[] pdfBytes, int startPage, int endPage, int maxChars) {
        if (pdfBytes == null || pdfBytes.length == 0) return "";
        if (pdfBytes.length > MAX_REMOTE_PDF_BYTES) return "";
        int s = Math.max(1, startPage);
        int e = Math.max(s, endPage);
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int total = doc.getNumberOfPages();
            if (total <= 0) return "";
            if (s > total) return "";
            if (e > total) e = total;
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(s);
            stripper.setEndPage(e);
            String text = stripper.getText(doc).trim();
            if (maxChars > 0 && text.length() > maxChars) {
                return text.substring(0, maxChars) + "...[truncated]";
            }
            return text;
        } catch (Exception e1) {
            log.error("PDF page-range extraction failed: {}", e1.getMessage());
            return "";
        }
    }

    /**
     * For image uploads: encode as a base64 data URI suitable for OpenAI Vision.
     * Returns null on failure.
     */
    public String encodeImageToBase64(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        if (file.getSize() > MAX_UPLOAD_BYTES) return null;
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
