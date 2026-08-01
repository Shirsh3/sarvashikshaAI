package com.sarvashikshaai.security;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies Alexa Skill request signatures (Signature + SignatureCertChainUrl).
 *
 * @see <a href="https://developer.amazon.com/en-US/docs/alexa/custom-skills/host-a-custom-skill-as-a-web-service.html">Amazon docs</a>
 */
@Component
public class AlexaSignatureVerifier {

    private static final String EXPECTED_HOST = "s3.amazonaws.com";
    private static final String EXPECTED_PATH_PREFIX = "/echo.api/";
    private static final String EXPECTED_SAN = "echo-api.amazon.com";

    private final Map<String, X509Certificate> certCache = new ConcurrentHashMap<>();

    public void verify(String signatureCertChainUrl, String signature, byte[] body) {
        if (signatureCertChainUrl == null || signatureCertChainUrl.isBlank()
                || signature == null || signature.isBlank()
                || body == null) {
            throw new SecurityException("Missing Alexa signature headers");
        }
        try {
            URI uri = URI.create(signatureCertChainUrl);
            validateCertUrl(uri);
            X509Certificate signingCert = certCache.computeIfAbsent(signatureCertChainUrl, this::downloadAndValidateCert);
            if (signingCert.getNotAfter().before(new java.util.Date())) {
                certCache.remove(signatureCertChainUrl);
                signingCert = certCache.computeIfAbsent(signatureCertChainUrl, this::downloadAndValidateCert);
            }
            Signature sig = Signature.getInstance("SHA1withRSA");
            sig.initVerify(signingCert.getPublicKey());
            sig.update(body);
            byte[] decoded = Base64.getDecoder().decode(signature);
            if (!sig.verify(decoded)) {
                throw new SecurityException("Alexa signature mismatch");
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Alexa signature verification failed: " + e.getMessage(), e);
        }
    }

    private static void validateCertUrl(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new SecurityException("Cert URL must be https");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!EXPECTED_HOST.equals(host) && !host.endsWith("." + EXPECTED_HOST)) {
            throw new SecurityException("Unexpected cert host");
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (!path.startsWith(EXPECTED_PATH_PREFIX)) {
            throw new SecurityException("Unexpected cert path");
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw new SecurityException("Unexpected cert port");
        }
    }

    private X509Certificate downloadAndValidateCert(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("GET");
            try (InputStream in = conn.getInputStream()) {
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                Collection<? extends java.security.cert.Certificate> chain =
                        factory.generateCertificates(in);
                if (chain == null || chain.isEmpty()) {
                    throw new SecurityException("Empty cert chain");
                }
                List<X509Certificate> certs = chain.stream()
                        .map(c -> (X509Certificate) c)
                        .toList();
                X509Certificate leaf = certs.get(0);
                leaf.checkValidity();
                boolean hasSan = false;
                Collection<List<?>> altNames = leaf.getSubjectAlternativeNames();
                if (altNames != null) {
                    for (List<?> entry : altNames) {
                        if (entry.size() >= 2 && Integer.valueOf(2).equals(entry.get(0))) {
                            if (EXPECTED_SAN.equals(String.valueOf(entry.get(1)))) {
                                hasSan = true;
                                break;
                            }
                        }
                    }
                }
                if (!hasSan && leaf.getSubjectX500Principal().getName().contains(EXPECTED_SAN)) {
                    hasSan = true;
                }
                if (!hasSan) {
                    throw new SecurityException("Cert SAN missing echo-api.amazon.com");
                }
                return leaf;
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Unable to download Alexa cert: " + e.getMessage(), e);
        }
    }
}
