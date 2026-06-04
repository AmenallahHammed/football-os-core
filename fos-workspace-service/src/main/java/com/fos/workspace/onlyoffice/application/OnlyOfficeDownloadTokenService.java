package com.fos.workspace.onlyoffice.application;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class OnlyOfficeDownloadTokenService {

    private static final String PURPOSE = "onlyoffice-download";

    private final String jwtSecret;
    private final int tokenExpiryMinutes;

    public OnlyOfficeDownloadTokenService(@Value("${fos.onlyoffice.jwt-secret}") String jwtSecret,
                                          @Value("${fos.onlyoffice.token-expiry-minutes:60}") int tokenExpiryMinutes) {
        this.jwtSecret = jwtSecret;
        this.tokenExpiryMinutes = tokenExpiryMinutes;
    }

    public String signDownloadToken(UUID documentId, int versionNumber) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("purpose", PURPOSE)
                .claim("documentId", documentId.toString())
                .claim("version", versionNumber)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds((long) tokenExpiryMinutes * 60)))
                .signWith(signingKey())
                .compact();
    }

    public DownloadTokenClaims parseDownloadToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String purpose = claims.get("purpose", String.class);
        if (!PURPOSE.equals(purpose)) {
            throw new IllegalArgumentException("Invalid OnlyOffice download token purpose");
        }

        String documentId = claims.get("documentId", String.class);
        Number version = claims.get("version", Number.class);
        if (documentId == null || documentId.isBlank() || version == null) {
            throw new IllegalArgumentException("Incomplete OnlyOffice download token");
        }

        return new DownloadTokenClaims(UUID.fromString(documentId), version.intValue());
    }

    private SecretKey signingKey() {
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("OnlyOffice JWT secret must be at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(secretBytes);
    }

    public record DownloadTokenClaims(UUID documentId, int versionNumber) {
    }
}
