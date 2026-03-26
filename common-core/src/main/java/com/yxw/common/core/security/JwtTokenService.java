package com.yxw.common.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared JWT utility for token generation and validation across services.
 */
public class JwtTokenService {

    private static final String BASE64_PREFIX = "base64:";

    private final Key key;
    private final long expirationMs;

    public JwtTokenService(String secret, long expirationMs) {
        byte[] keyBytes = resolveKeyBytes(secret);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("app.jwt.secret must be at least 32 bytes, or use a base64: prefixed secret.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    private byte[] resolveKeyBytes(String secret) {
        if (secret != null && secret.startsWith(BASE64_PREFIX)) {
            return Decoders.BASE64.decode(secret.substring(BASE64_PREFIX.length()));
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    public String generateToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public Long extractUserId(String token) {
        Claims claims = parseToken(token);
        Object userId = claims.get("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        if (userId instanceof String value) {
            return Long.valueOf(value);
        }
        return null;
    }

    public String extractUsername(String token) {
        return parseToken(token).getSubject();
    }
}
