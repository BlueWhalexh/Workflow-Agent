package com.myworkflow.agent.backend.security;

import com.myworkflow.agent.backend.config.BackendProperties;
import com.myworkflow.agent.backend.identity.BackendPrincipal;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "my-workflow.backend.oidc.jwks-uri")
public class ConfigurableOidcBearerTokenVerifier implements BearerTokenVerifier {

  private final BackendProperties.Oidc oidc;
  private final NimbusJwtDecoder jwtDecoder;

  public ConfigurableOidcBearerTokenVerifier(BackendProperties properties) {
    this(properties.oidc());
  }

  ConfigurableOidcBearerTokenVerifier(BackendProperties.Oidc oidc) {
    if (oidc.jwksUri() == null) {
      throw new IllegalArgumentException("OIDC JWKS URI must be configured");
    }
    this.oidc = oidc;
    this.jwtDecoder = NimbusJwtDecoder.withJwkSetUri(oidc.jwksUri()).build();
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
    OAuth2TokenValidator<Jwt> validator = oidc.issuer() == null
        ? JwtValidators.createDefault()
        : JwtValidators.createDefaultWithIssuer(oidc.issuer());
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
