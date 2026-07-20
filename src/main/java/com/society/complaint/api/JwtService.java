package com.society.complaint.api;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JwtService handles everything to do with JWT tokens.
 *
 * Plain English: A JWT (JSON Web Token) is like a signed pass.
 * When a user logs in via the API (/api/auth/login), the server:
 *   1. Verifies their email + password
 *   2. Creates a token: a JSON object containing their email + expiry,
 *      digitally signed with a secret key only the server knows
 *   3. Returns the token string to the client
 *
 * On every subsequent API request, the client sends:
 *   Authorization: Bearer <token>
 *
 * The server can verify it's genuine (checks the signature with the same
 * secret key) without hitting the database — the email is embedded in
 * the token itself. Tokens expire after 24 hours.
 */
@Service
public class JwtService {

    @Value("${app.jwt.secret:SocietyComplaintSystemSecretKeyThatIsAtLeast256BitsLongForHS256Algorithm}")
    private String secretKey;

    @Value("${app.jwt.expiration-ms:86400000}") // 24 hours default
    private long expirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())       // email
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
