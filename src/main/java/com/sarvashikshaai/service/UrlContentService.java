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
import java.util.List;
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

            // Try NCERT chapter PDFs first (e.g. lech1=1-5 -> lech101.pdf..lech105.pdf).
            List<String> pdfLinks = new ArrayList<>();
            List<String> ncertChapterPdfLinks = deriveNcertChapterPdfLinks(uri);
            if (!ncertChapterPdfLinks.isEmpty()) {
                pdfLinks.addAll(ncertChapterPdfLinks);
            }
            // Fallback: any direct PDF links in the page (filtered).
            pdfLinks.addAll(extractPdfLinks(html, uri));
            if (!pdfLinks.isEmpty()) {
                String pdfText = fetchAndExtractPdfBatch(pdfLinks, 3);
                if (!pdfText.isBlank()) {
                    if (ctx.length() > 0) ctx.append("\n\n");
                    ctx.append("Extracted PDF content:\n").append(pdfText);
                }
            }
            // For NCERT selector URLs, avoid returning noisy page chrome when no PDF text could be extracted.
            if (isNcert && !ctx.toString().contains("Extracted PDF content:")) {
                return "";
            }
            return ctx.toString();
        } catch (Exception e) {
            log.warn("URL extraction failed for {}: {}", sourceUrl, e.getMessage());
            return "";
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

    private List<String> deriveNcertChapterPdfLinks(URI uri) {
        List<String> links = new ArrayList<>();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!host.contains("ncert.nic.in")) return links;
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) return links;

        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8).trim();
            String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8).trim();
            if (!key.matches("[a-z]{3,}\\d+")) continue;
            Matcher range = Pattern.compile("^(\\d+)-(\\d+)$").matcher(value);
            if (!range.matches()) continue;
            int start = Integer.parseInt(range.group(1));
            int end = Integer.parseInt(range.group(2));
            if (start <= 0 || end < start) continue;
            int boundedEnd = Math.min(end, start + 7); // keep request bounded
            for (int ch = start; ch <= boundedEnd; ch++) {
                links.add("https://ncert.nic.in/textbook/pdf/" + key + String.format("%02d", ch) + ".pdf");
            }
            break;
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

