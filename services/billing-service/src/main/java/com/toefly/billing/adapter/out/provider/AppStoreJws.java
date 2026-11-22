package com.toefly.billing.adapter.out.provider;

import com.toefly.billing.application.port.in.InvalidSignatureException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies an App Store JWS (StoreKit 2 transaction or Server Notification V2): ES256 over {@code header.payload} with
 * the leaf of the {@code x5c} chain; every certificate is signed by the next and the last one is the trusted root —
 * Apple Root CA G3 in production, the StoreKit Testing certificate locally (AC-27.04.2).
 */
public class AppStoreJws {

    private final X509Certificate trustedRoot;
    private final JsonMapper json;
    private final Clock clock;

    public AppStoreJws(X509Certificate trustedRoot, JsonMapper json, Clock clock) {
        this.trustedRoot = trustedRoot;
        this.json = json;
        this.clock = clock;
    }

    /** Returns the verified payload. */
    public JsonNode verify(String jws) {
        String[] parts = jws.split("\\.");
        if (parts.length != 3) {
            throw new InvalidSignatureException("The JWS is malformed");
        }
        try {
            JsonNode header = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
            if (!"ES256".equals(header.path("alg").asString())) {
                throw new InvalidSignatureException("The JWS algorithm must be ES256");
            }
            List<X509Certificate> chain = chain(header.path("x5c"));
            Date now = Date.from(clock.instant());
            for (int i = 0; i < chain.size(); i++) {
                X509Certificate certificate = chain.get(i);
                certificate.checkValidity(now);
                X509Certificate issuer = i + 1 < chain.size() ? chain.get(i + 1) : trustedRoot;
                certificate.verify(issuer.getPublicKey());
            }
            X509Certificate last = chain.get(chain.size() - 1);
            if (!MessageDigest.isEqual(last.getEncoded(), trustedRoot.getEncoded())
                    && !last.getIssuerX500Principal().equals(trustedRoot.getSubjectX500Principal())) {
                throw new InvalidSignatureException("The JWS chain does not end at the trusted root");
            }
            Signature es256 = Signature.getInstance("SHA256withECDSAinP1363Format");
            es256.initVerify(chain.get(0).getPublicKey());
            es256.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!es256.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                throw new InvalidSignatureException("The JWS signature does not match");
            }
            return json.readTree(Base64.getUrlDecoder().decode(parts[1]));
        } catch (GeneralSecurityException | IllegalArgumentException | tools.jackson.core.JacksonException invalid) {
            throw new InvalidSignatureException("The JWS is invalid: " + invalid.getMessage());
        }
    }

    private static List<X509Certificate> chain(JsonNode x5c) throws GeneralSecurityException {
        if (!x5c.isArray() || x5c.isEmpty()) {
            throw new InvalidSignatureException("The JWS has no x5c chain");
        }
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> chain = new ArrayList<>();
        for (JsonNode encoded : x5c) {
            chain.add((X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(Base64.getDecoder().decode(encoded.asString()))));
        }
        return chain;
    }
}
