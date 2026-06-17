package com.myworkflow.agent.backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myworkflow.agent.backend.config.BackendProperties;
import com.myworkflow.agent.backend.identity.BackendPrincipal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("!'${my-workflow.backend.oidc.jwks-uri:}'.trim().isEmpty() || !'${my-workflow.backend.oidc.issuer-uri:}'.trim().isEmpty()")
public class ConfigurableOidcBearerTokenVerifier implements BearerTokenVerifier {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .build();

  private final BackendProperties.Oidc oidc;
  private final NimbusJwtDecoder jwtDecoder;

  @Autowired
  public ConfigurableOidcBearerTokenVerifier(BackendProperties properties) {
    this(properties.oidc());
  }

  ConfigurableOidcBearerTokenVerifier(BackendProperties.Oidc oidc) {
    if (oidc.jwksUri() == null && oidc.issuerUri() == null) {
      throw new IllegalArgumentException("OIDC JWKS URI or issuer URI must be configured");
    }
    this.oidc = oidc;
    this.jwtDecoder = NimbusJwtDecoder.withJwkSetUri(resolveJwksUri(oidc)).build();
    this.jwtDecoder.setJwtValidator(tokenValidator(oidc));
  }

  @Override
  public Optional<BackendPrincipal> verify(String token) {
    try {
      return principalFrom(jwtDecoder.decode(token));
    } catch (JwtException | IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  private Optional<BackendPrincipal> principalFrom(Jwt jwt) {
    Optional<String> userId = claim(jwt, oidc.userIdClaim());
    Optional<String> teamId = claim(jwt, oidc.teamIdClaim());
    Optional<String> displayName = claim(jwt, oidc.displayNameClaim()).or(() -> userId);
    if (userId.isEmpty() || teamId.isEmpty() || displayName.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new BackendPrincipal(userId.get(), teamId.get(), displayName.get()));
  }

  private static OAuth2TokenValidator<Jwt> tokenValidator(BackendProperties.Oidc oidc) {
    OAuth2TokenValidator<Jwt> validator = oidc.validationIssuer() == null
        ? JwtValidators.createDefault()
        : JwtValidators.createDefaultWithIssuer(oidc.validationIssuer());
    if (oidc.audience() == null) {
      return validator;
    }
    return new DelegatingOAuth2TokenValidator<>(validator, audienceValidator(oidc.audience()));
  }

  private static OAuth2TokenValidator<Jwt> audienceValidator(String requiredAudience) {
    return jwt -> {
      if (jwt.getAudience().contains(requiredAudience)) {
        return OAuth2TokenValidatorResult.success();
      }
      OAuth2Error error = new OAuth2Error(
          "invalid_token",
          "The required audience is missing",
          null
      );
      return OAuth2TokenValidatorResult.failure(error);
    };
  }

  private static String resolveJwksUri(BackendProperties.Oidc oidc) {
    if (oidc.jwksUri() != null) {
      return oidc.jwksUri();
    }
    return discoverJwksUri(oidc.issuerUri());
  }

  private static String discoverJwksUri(String issuerUri) {
    try {
      HttpRequest request = HttpRequest.newBuilder(discoveryUri(issuerUri))
          .timeout(Duration.ofSeconds(5))
          .GET()
          .build();
      HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("OIDC discovery request failed");
      }
      JsonNode document = OBJECT_MAPPER.readTree(response.body());
      String discoveredIssuer = text(document, "issuer").orElse(null);
      if (discoveredIssuer != null && !issuerUri.equals(discoveredIssuer)) {
        throw new IllegalStateException("OIDC discovery issuer mismatch");
      }
      return text(document, "jwks_uri")
          .orElseThrow(() -> new IllegalStateException("OIDC discovery did not return jwks_uri"));
    } catch (IllegalStateException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("OIDC discovery failed", exception);
    }
  }

  private static URI discoveryUri(String issuerUri) {
    return URI.create(stripTrailingSlash(issuerUri) + "/.well-known/openid-configuration");
  }

  private static String stripTrailingSlash(String value) {
    String result = value;
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }

  private static Optional<String> text(JsonNode document, String fieldName) {
    JsonNode value = document.get(fieldName);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      return Optional.empty();
    }
    return Optional.of(value.asText().trim());
  }

  private static Optional<String> claim(Jwt jwt, String claimName) {
    Object value = jwt.getClaims().get(claimName);
    if (value == null) {
      return Optional.empty();
    }
    String stringValue = value.toString().trim();
    if (stringValue.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(stringValue);
  }
}
