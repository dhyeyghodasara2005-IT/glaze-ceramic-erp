package com.ceramicerp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * Creates and checks JWT login tokens.
 * A token is a signed note saying "this is user X, valid until T". The signature uses our
 * secret key, so nobody can forge or modify a token without the key.
 */
@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        byte[] keyBytes = Decoders.BASE64.decode(props.secret());
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 256 bits (32 bytes) base64. "
                    + "Generate one with: openssl rand -base64 48");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public IssuedToken issue(AppUserDetails user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.expiration());
        String token = Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getUserId())
                .issuer(props.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /**
     * Returns the username inside a valid token.
     *
     * @throws JwtException if the token is expired, tampered with, or not ours
     */
    public String validateAndGetUsername(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }
}
