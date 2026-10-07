package com.alertamujer.backend.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Component;

/** Issues and verifies the deliberately small HS256 access-token contract. */
@Component
public class JwtAccessTokenService {

    public static final String ISSUER = "alertamujer-api";
    public static final String AUDIENCE = "alertamujer-clients";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtAccessTokenService(@Value("${JWT_SECRET}") String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        }
        SecretKey key = new SecretKeySpec(secretBytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> issuerAndTime = JwtValidators.createDefaultWithIssuer(ISSUER);
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(AUDIENCE)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        jwtDecoder.setJwtValidator(jwt -> {
            OAuth2TokenValidatorResult issuerResult = issuerAndTime.validate(jwt);
            return issuerResult.hasErrors() ? issuerResult : audience.validate(jwt);
        });
        this.decoder = jwtDecoder;
    }

    public String issue(UUIDClaims claims, Instant now, Instant expiresAt) {
        JwtClaimsSet claimSet = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(claims.userId().toString())
                .claim("role", claims.role())
                .claim("sid", claims.sessionId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(MacAlgorithm.HS256).build(), claimSet))
                .getTokenValue();
    }

    public Jwt decode(String accessToken) {
        return decoder.decode(accessToken);
    }

    public record UUIDClaims(java.util.UUID userId, java.util.UUID sessionId, String role) {
    }
}
