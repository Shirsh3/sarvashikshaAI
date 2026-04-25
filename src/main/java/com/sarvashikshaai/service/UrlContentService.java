package com.sarvashikshaai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLParameters;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlContentService {

    private final FileExtractionService fileExtractionService;
    private static SSLParameters buildSslParameters() {
        SSLParameters params = new SSLParameters();
        params.setProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
        return params;
    }
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(12))
            .sslParameters(buildSslParameters())
            .build();

    private static final long PDF_CACHE_TTL_MS = 5 * 60 * 1000L; // 5 minutes
    private static final int PDF_CACHE_MAX_ENTRIES = 16;

    private record CachedPdf(byte[] bytes, long fetchedAtMs) {}
    private final Map<String, CachedPdf> pdfCache = new ConcurrentHashMap<>();

    public String extractContextFromUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) return "";
        try {
            URI uri = URI.create(sourceUrl.trim());
            boolean isNcert = isNcertHost(uri);
            String html = fetchTextWithRetry(uri);
            if (html.isBlank()) return "";

            StringBuilder ctx = new StringBuilder();
            String pageText = isNcert ? "" : stripHtml(html);
            if (!pageText.isBlank()) {
                ctx.append("Source page text:\n").append(pageText);
            }

            // NCERT: prefer in-page <a href=…textbook/pdf/…> (matches what the site actually offers),
            // then derived lech/ihsc/… patterns from query string, then generic .pdf in HTML.
            Set<String> pdfOrder = new LinkedHashSet<>();
            pdfOrder.addAll(extractNcertTextbookPdfLinksFromHtml(html));
            pdfOrder.addAll(deriveNcertChapterPdfLinks(uri));
            pdfOrder.addAll(extractPdfLinks(html, uri));
            List<String> pdfLinks = new ArrayList<>(pdfOrder);
            if (!pdfLinks.isEmpty()) {
                int ncertMax = isNcert ? 6 : 3;
                String pdfText = fetchAndExtractPdfBatch(pdfLinks, ncertMax);
                if (!pdfText.isBlank()) {
                    if (ctx.length() > 0) ctx.append("\n\n");
                    ctx.append("Extracted PDF content:\n").append(pdfText);
                }
            }
            // If NCERT PDFs failed, still return a short page excerpt so the model is not only given a URL.
            if (isNcert && !ctx.toString().contains("Extracted PDF content:") && !html.isBlank()) {
                String snap = stripHtml(html);
                if (snap != null && !snap.isBlank()) {
                    if (ctx.length() > 0) {
                        ctx.append("\n\n");
                    }
                    ctx.append("Page snapshot (no PDF text extracted — use for topic cue only):\n")
                            .append(snap);
                }
            }
            return ctx.toString();
        } catch (Exception e) {
            log.warn("URL extraction failed for {}: {}", sourceUrl, e.getMessage());
            return "";
        }
    }

    /** Resolve ordered PDF links for an NCERT textbook selector URL. */
    public List<String> resolvePdfLinksFromUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) return List.of();
        try {
            URI uri = URI.create(sourceUrl.trim());
            String html = fetchTextWithRetry(uri);
            if (html == null || html.isBlank()) return List.of();
            Set<String> pdfOrder = new LinkedHashSet<>();
            pdfOrder.addAll(extractNcertTextbookPdfLinksFromHtml(html));
            pdfOrder.addAll(deriveNcertChapterPdfLinks(uri));
            pdfOrder.addAll(extractPdfLinks(html, uri));
            return new ArrayList<>(pdfOrder);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Best-effort: extract a human-friendly title from an NCERT selector page. */
    public String resolveNcertPageTitle(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) return "";
        try {
            URI uri = URI.create(sourceUrl.trim());
            String html = fetchTextWithRetry(uri);
            if (html == null || html.isBlank()) return "";
            Matcher m = Pattern.compile("<title>\\s*([^<]{1,200})\\s*</title>", Pattern.CASE_INSENSITIVE).matcher(html);
            if (!m.find()) return "";
            String t = m.group(1);
            if (t == null) return "";
            // Normalize whitespace + strip common boilerplate
            String cleaned = t.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
            cleaned = cleaned.replaceAll("(?i)\\s*\\|\\s*NCERT\\s*$", "").trim();
            cleaned = cleaned.replaceAll("(?i)\\s*\\-\\s*NCERT\\s*$", "").trim();
            cleaned = cleaned.replaceAll("(?i)\\s*::\\s*NCERT\\s*$", "").trim();
            cleaned = cleaned.replaceAll("(?i)^NCERT\\s*\\|\\s*", "").trim();
            cleaned = cleaned.replaceAll("(?i)^NCERT\\s*::\\s*", "").trim();
            return cleaned;
        } catch (Exception e) {
            return "";
        }
    }

    /** Download PDF bytes (bounded by {@link FileExtractionService} MAX_BYTES when extracting). */
    public byte[] fetchPdfBytes(String pdfUrl) {
        if (pdfUrl == null || pdfUrl.isBlank()) return new byte[0];
        try {
            // Cache: keep the last few NCERT PDFs in-memory briefly to avoid re-downloading for each "Next" click.
            String key = pdfUrl.trim();
            long now = System.currentTimeMillis();
            CachedPdf hit = pdfCache.get(key);
            if (hit != null && (now - hit.fetchedAtMs) <= PDF_CACHE_TTL_MS && hit.bytes != null && hit.bytes.length > 0) {
                return Arrays.copyOf(hit.bytes, hit.bytes.length);
            }

            HttpRequest req = HttpRequest.newBuilder(URI.create(pdfUrl.trim()))
                    .timeout(Duration.ofSeconds(25))
                    .GET()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Accept", "application/pdf,*/*;q=0.8")
                    .build();
            HttpResponse<byte[]> res = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() >= 400) return new byte[0];
            byte[] body = res.body();
            byte[] out = body == null ? new byte[0] : body;
            if (out.length > 0) {
                // Best-effort eviction
                if (pdfCache.size() >= PDF_CACHE_MAX_ENTRIES) {
                    long oldest = Long.MAX_VALUE;
                    String oldestKey = null;
                    for (var e : pdfCache.entrySet()) {
                        if (e.getValue() != null && e.getValue().fetchedAtMs < oldest) {
                            oldest = e.getValue().fetchedAtMs;
                            oldestKey = e.getKey();
                        }
                    }
                    if (oldestKey != null) pdfCache.remove(oldestKey);
                }
                pdfCache.put(key, new CachedPdf(Arrays.copyOf(out, out.length), now));
            }
            return out;
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private String fetchTextWithRetry(URI uri) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .build();
                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (res.statusCode() >= 400) return "";
                String body = res.body();
                if (body == null) return "";
                return body.length() > 120_000 ? body.substring(0, 120_000) : body;
            } catch (Exception e) {
                last = e;
                if (attempt < 2) {
                    try { Thread.sleep(250); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
            }
        }
        throw last != null ? last : new IllegalStateException("Failed to fetch URL content");
    }

    private List<String> extractPdfLinks(String html, URI base) {
        List<String> links = new ArrayList<>();
        Pattern p = Pattern.compile("href\\s*=\\s*['\\\"]([^'\\\"]+\\.pdf(?:\\?[^'\\\"]*)?)['\\\"]", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);
        while (m.find()) {
            try {
                String href = m.group(1);
                URI resolved = base.resolve(href);
                String url = resolved.toString();
                if (isIrrelevantPdf(url)) continue;
                if (!links.contains(url)) links.add(url);
                if (links.size() >= 3) break; // keep bounded
            } catch (Exception ignored) {}
        }
        return links;
    }

    private String fetchAndExtractPdfBatch(List<String> pdfLinks, int maxPdfs) {
        StringBuilder out = new StringBuilder();
        int extracted = 0;
        for (String link : pdfLinks) {
            if (extracted >= maxPdfs) break;
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(link))
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .header("Accept", "application/pdf,*/*;q=0.8")
                        .build();
                HttpResponse<byte[]> res;
                try {
                    res = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
                } catch (Exception first) {
                    // Retry once for transient TLS/connection issues seen on NCERT.
                    res = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
                }
                if (res.statusCode() >= 400) continue;
                byte[] body = res.body();
                if (body == null || body.length == 0) continue;
                String text = fileExtractionService.extractPdfText(body);
                if (text != null && !text.isBlank()) {
                    if (out.length() > 0) out.append("\n\n");
                    out.append("PDF: ").append(link).append("\n")
                            .append(text.length() > 5000 ? text.substring(0, 5000) + "...[truncated]" : text);
                    extracted++;
                }
            } catch (Exception e) {
                log.debug("Could not extract PDF from {}: {}", link, e.getMessage());
            }
        }
        return out.toString();
    }

    /**
     * Pull absolute PDF hrefs the NCERT page actually references (most reliable for odd codes like
     * {@code lech2=5-5} when a guessed filename would 404).
     */
    private List<String> extractNcertTextbookPdfLinksFromHtml(String html) {
        List<String> out = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return out;
        }
        // Absolute URLs
        Pattern abs = Pattern.compile("https?://ncert\\.nic\\.in/textbook/pdf/[a-zA-Z0-9._-]+\\.pdf", Pattern.CASE_INSENSITIVE);
        Matcher ma = abs.matcher(html);
        while (ma.find()) {
            String u = ma.group(0);
            if (isIrrelevantPdf(u)) {
                continue;
            }
            if (!out.contains(u)) {
                out.add(u);
            }
            if (out.size() >= 12) {
                break;
            }
        }
        // Relative: href="/textbook/pdf/…"
        Pattern rel = Pattern.compile("['\\\"](?:/)?textbook/pdf/([a-zA-Z0-9._-]+\\.pdf)['\\\"]", Pattern.CASE_INSENSITIVE);
        Matcher mr = rel.matcher(html);
        while (mr.find()) {
            try {
                String u = "https://ncert.nic.in/textbook/pdf/" + mr.group(1);
                if (!isIrrelevantPdf(u) && !out.contains(u)) {
                    out.add(u);
                }
            } catch (Exception ignored) { /* ignore */ }
            if (out.size() >= 12) {
                break;
            }
        }
        return out;
    }

    private List<String> deriveNcertChapterPdfLinks(URI uri) {
        List<String> links = new ArrayList<>();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!host.contains("ncert.nic.in")) {
            return links;
        }
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return links;
        }

        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
            String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8).trim();
            // e.g. lech1, lech2, ihsc1, aiee1 — letters + at least one trailing digit
            if (!key.matches("[a-z]{3,}\\d+")) {
                continue;
            }
            Matcher range = Pattern.compile("^(\\d+)-(\\d+)$").matcher(value);
            if (!range.matches()) {
                continue;
            }
            int start = Integer.parseInt(range.group(1));
            int end = Integer.parseInt(range.group(2));
            if (start <= 0 || end < start) {
                continue;
            }
            int boundedEnd = Math.min(end, start + 7); // keep request bounded
            for (int ch = start; ch <= boundedEnd; ch++) {
                String url = "https://ncert.nic.in/textbook/pdf/" + key + String.format("%02d", ch) + ".pdf";
                if (!links.contains(url)) {
                    links.add(url);
                }
            }
        }
        return links;
    }

    private boolean isNcertHost(URI uri) {
        String host = uri.getHost();
        return host != null && host.toLowerCase().contains("ncert.nic.in");
    }

    private boolean isIrrelevantPdf(String url) {
        String u = url.toLowerCase();
        return u.contains("howtoclearcache")
                || u.contains("rationalised.pdf")
                || u.contains("instruction.pdf")
                || u.contains("corrigendum");
    }

    private String stripHtml(String html) {
        String t = html.replaceAll("(?is)<script.*?>.*?</script>", " ")
                .replaceAll("(?is)<style.*?>.*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return t.length() > 6000 ? t.substring(0, 6000) + "...[truncated]" : t;
    }
}

