package com.mirai.inventoryservice.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.function.Function;
import javax.crypto.SecretKey;

@Slf4j
@Service
@NoArgsConstructor
public class JwtService {

    @Value("${supabase.jwt.secret}")
    private String jwtSecret;

    // Supabase issues user JWTs with iss = "{supabase.url}/auth/v1" and a fixed
    // aud = "authenticated" for authenticated-user tokens. Both are required so a token
    // signed for a different purpose or a different Supabase project cannot be replayed here,
    // per docs/specs/authentication-and-authorization.md section 3 ("exact issuer" /
    // "allowed audience").
    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.jwt.audience:authenticated}")
    private String expectedAudience;

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("JwtService initialized with secret: {}", (jwtSecret != null ? "LOADED" : "NULL"));
    }


    public String extractName(String token){
        return extractClaim(token, claims -> {
            HashMap<?, ?> userMetadata = claims.get("user_metadata", HashMap.class);
            if (userMetadata != null && userMetadata.get("name") != null) {
                return userMetadata.get("name").toString();
            }
            // Fallback to email if name not set
            String email = claims.get("email", String.class);
            return email != null ? email.split("@")[0] : null;
        });
    }

    // Non-authoritative display value only. This claim MUST NOT be used to grant backend
    // authority — see JwtAuthenticationFilter, which derives the enforced role from the
    // backend-controlled User record instead. user_metadata is editable by the token's own
    // owner via the Supabase client SDK.
    public String extractRole(String token){
        return extractClaim(token, claims -> {
            HashMap<?, ?> userMetadata = claims.get("user_metadata", HashMap.class);
            if (userMetadata != null && userMetadata.get("role") != null) {
                return userMetadata.get("role").toString();
            }
            return null;
        });
    }

    public String extractPersonId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        String expectedIssuer = supabaseUrl.replaceAll("/+$", "") + "/auth/v1";

        JwtParser parser = Jwts.parser()
                .verifyWith(getSignKey())
                // Signature, expiration (exp) and not-before (nbf) are validated
                // automatically by the parser when those claims are present.
                .requireIssuer(expectedIssuer)
                .requireAudience(expectedAudience)
                .build();

        Claims claims = parser.parseSignedClaims(token).getPayload();

        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new io.jsonwebtoken.MalformedJwtException("JWT is missing a required subject claim");
        }

        return claims;
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Boolean validateToken(String token) {
        try {
            boolean result = !isTokenExpired(token);
            return result;
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            log.debug("Token validation error details", e);
            return false;
        }
    }

    private SecretKey getSignKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
