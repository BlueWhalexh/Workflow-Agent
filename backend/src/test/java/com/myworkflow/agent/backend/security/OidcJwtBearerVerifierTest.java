package com.myworkflow.agent.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.myworkflow.agent.backend.config.BackendProperties;
import com.myworkflow.agent.backend.identity.BackendPrincipal;
import com.sun.net.httpserver.HttpServer;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OidcJwtBearerVerifierTest {

  private HttpServer jwksServer;
  private KeyPair keyPair;
  private String issuer;
  private String audience;
  private String jwksUri;

  @BeforeEach
  void startJwksServer() throws Exception {
    issuer = "https://issuer.example.test";
    audience = "my-workflow-backend";
    keyPair = generateRsaKeyPair();
    jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    jwksServer.createContext("/jwks", exchange -> {
      byte[] body = jwksJson((RSAPublicKey) keyPair.getPublic()).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    jwksServer.start();
    jwksUri = "http://127.0.0.1:%d/jwks".formatted(jwksServer.getAddress().getPort());
  }

  @AfterEach
  void stopJwksServer() {
    if (jwksServer != null) {
      jwksServer.stop(0);
    }
  }

  @Test
  void validJwtFromConfiguredJwksMapsClaimsToPrincipal() throws Exception {
    ConfigurableOidcBearerTokenVerifier verifier = new ConfigurableOidcBearerTokenVerifier(
        oidcProperties("sub", "team_id", "name")
    );
    String token = jwtPayload("""
        {
          "iss": "%s",
          "sub": "oidc-user-1",
          "aud": "%s",
          "team_id": "team-oidc",
          "name": "OIDC User",
          "iat": %d,
          "exp": %d
        }
        """.formatted(issuer, audience, nowEpochSeconds(), futureEpochSeconds()));

    Optional<BackendPrincipal> principal = verifier.verify(token);

    assertThat(principal).hasValue(new BackendPrincipal("oidc-user-1", "team-oidc", "OIDC User"));
  }

  @Test
  void wrongAudienceIsRejected() throws Exception {
    ConfigurableOidcBearerTokenVerifier verifier = new ConfigurableOidcBearerTokenVerifier(
        oidcProperties("sub", "team_id", "name")
    );
    String token = jwtPayload("""
        {
          "iss": "%s",
          "sub": "oidc-user-1",
          "aud": "other-backend",
          "team_id": "team-oidc",
          "name": "OIDC User",
          "iat": %d,
          "exp": %d
        }
        """.formatted(issuer, nowEpochSeconds(), futureEpochSeconds()));

    assertThat(verifier.verify(token)).isEmpty();
  }

  @Test
  void wrongIssuerIsRejected() throws Exception {
    ConfigurableOidcBearerTokenVerifier verifier = new ConfigurableOidcBearerTokenVerifier(
        oidcProperties("sub", "team_id", "name")
    );
    String token = jwtPayload("""
        {
          "iss": "https://other-issuer.example.test",
          "sub": "oidc-user-1",
          "aud": "%s",
          "team_id": "team-oidc",
          "name": "OIDC User",
          "iat": %d,
          "exp": %d
        }
        """.formatted(audience, nowEpochSeconds(), futureEpochSeconds()));

    assertThat(verifier.verify(token)).isEmpty();
  }

  @Test
  void expiredTokenIsRejected() throws Exception {
    ConfigurableOidcBearerTokenVerifier verifier = new ConfigurableOidcBearerTokenVerifier(
        oidcProperties("sub", "team_id", "name")
    );
    String token = jwtPayload("""
        {
          "iss": "%s",
          "sub": "oidc-user-1",
          "aud": "%s",
          "team_id": "team-oidc",
          "name": "OIDC User",
          "iat": %d,
          "exp": %d
        }
        """.formatted(issuer, audience, pastEpochSeconds() - 300, pastEpochSeconds()));

    assertThat(verifier.verify(token)).isEmpty();
  }

  @Test
  void tokenSignedByUnknownKeyIsRejected() throws Exception {
    ConfigurableOidcBearerTokenVerifier verifier = new ConfigurableOidcBearerTokenVerifier(
        oidcProperties("sub", "team_id", "name")
    );
    KeyPair originalKeyPair = keyPair;
    keyPair = generateRsaKeyPair();
    String token = jwtPayload("""
        {
          "iss": "%s",
          "sub": "oidc-user-1",
          "aud": "%s",
          "team_id": "team-oidc",
          "name": "OIDC User",
          "iat": %d,
          "exp": %d
        }
        """.formatted(issuer, audience, nowEpochSeconds(), futureEpochSeconds()));
    keyPair = originalKeyPair;

    assertThat(verifier.verify(token)).isEmpty();
  }

  @Test
  void missingConfiguredTeamClaimIsRejected() throws Exception {
    ConfigurableOidcBearerTokenVerifier verifier = new ConfigurableOidcBearerTokenVerifier(
        oidcProperties("sub", "tenant_id", "name")
    );
    String token = jwtPayload("""
        {
          "iss": "%s",
          "sub": "oidc-user-1",
          "aud": "%s",
          "team_id": "team-oidc",
          "name": "OIDC User",
          "iat": %d,
          "exp": %d
        }
        """.formatted(issuer, audience, nowEpochSeconds(), futureEpochSeconds()));

    assertThat(verifier.verify(token)).isEmpty();
  }

  private BackendProperties.Oidc oidcProperties(
      String userIdClaim,
      String teamIdClaim,
      String displayNameClaim
  ) {
    return new BackendProperties.Oidc(
        issuer,
        jwksUri,
        audience,
        userIdClaim,
        teamIdClaim,
        displayNameClaim
    );
  }

  private String jwtPayload(String payloadJson) throws Exception {
    String headerJson = """
        {"alg":"RS256","kid":"test-key","typ":"JWT"}
        """;
    String signingInput = base64Url(headerJson.getBytes(StandardCharsets.UTF_8))
        + "."
        + base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(keyPair.getPrivate());
    signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
    return signingInput + "." + base64Url(signature.sign());
  }

  private static KeyPair generateRsaKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static String jwksJson(RSAPublicKey publicKey) {
    return """
        {"keys":[{"kty":"RSA","kid":"test-key","use":"sig","alg":"RS256","n":"%s","e":"%s"}]}
        """.formatted(base64Url(unsigned(publicKey.getModulus())), base64Url(unsigned(publicKey.getPublicExponent())));
  }

  private static byte[] unsigned(BigInteger value) {
    byte[] bytes = value.toByteArray();
    if (bytes.length > 1 && bytes[0] == 0) {
      byte[] trimmed = new byte[bytes.length - 1];
      System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
      return trimmed;
    }
    return bytes;
  }

  private static String base64Url(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static long nowEpochSeconds() {
    return Instant.now().getEpochSecond();
  }

  private static long pastEpochSeconds() {
    return Instant.now().minusSeconds(300).getEpochSecond();
  }

  private static long futureEpochSeconds() {
    return Instant.now().plusSeconds(300).getEpochSecond();
  }
}
