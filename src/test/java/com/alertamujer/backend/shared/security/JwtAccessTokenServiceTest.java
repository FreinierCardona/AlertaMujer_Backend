package com.alertamujer.backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtAccessTokenServiceTest {

    private static final String SECRET = "test-only-hs256-secret-with-at-least-32-bytes";

    @Test
    void issuesOnlyTheDocumentedHs256ClaimsAndVerifiesThem() {
        JwtAccessTokenService service = new JwtAccessTokenService(SECRET);
        Instant now = Instant.now();
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        var decoded = service.decode(service.issue(
                new JwtAccessTokenService.UUIDClaims(userId, sessionId, "USER"), now, now.plusSeconds(900)));

        assertThat(decoded.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("alertamujer-api");
        assertThat(decoded.getAudience()).containsExactly("alertamujer-clients");
        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaimAsString("role")).isEqualTo("USER");
        assertThat(decoded.getClaimAsString("sid")).isEqualTo(sessionId.toString());
        assertThat(decoded.getClaims().keySet()).containsExactlyInAnyOrder("iss", "aud", "sub", "role", "sid", "iat", "exp");
    }

    @Test
    void rejectsInvalidSignatureIssuerAudienceAndExpiration() throws Exception {
        JwtAccessTokenService service = new JwtAccessTokenService(SECRET);
        Instant now = Instant.now();

        assertThatThrownBy(() -> service.decode(signed("other", List.of("alertamujer-clients"), now.plusSeconds(60))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.decode(signed("alertamujer-api", List.of("other"), now.plusSeconds(60))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.decode(signed("alertamujer-api", List.of("alertamujer-clients"), now.minusSeconds(60))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.decode("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    private String signed(String issuer, List<String> audience, Instant expiresAt) throws Exception {
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(UUID.randomUUID().toString())
                .claim("role", "USER")
                .claim("sid", UUID.randomUUID().toString())
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(expiresAt))
                .build());
        token.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        return token.serialize();
    }
}
