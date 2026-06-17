package com.myworkflow.agent.backend.config;

import java.nio.file.Path;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BackendProperties {

  private final Path dataRoot;
  private final Path providerSecretFileRoot;
  private final DevPrincipal devPrincipal;
  private final AuditRetention auditRetention;
  private final Oidc oidc;
  private final OAuthIntrospection oauthIntrospection;

  @Autowired
  public BackendProperties(
      @Value("${my-workflow.backend.data-root:${java.io.tmpdir}/my-workflow-agent-backend}") String dataRoot,
      @Value("${my-workflow.backend.dev-principal.user-id:dev-user}") String devUserId,
      @Value("${my-workflow.backend.dev-principal.team-id:dev-team}") String devTeamId,
      @Value("${my-workflow.backend.dev-principal.display-name:Dev User}") String devDisplayName,
      @Value("${my-workflow.backend.provider-secrets.file-root:}") String providerSecretFileRoot,
      @Value("${my-workflow.backend.audit.retention-days:365}") int auditRetentionDays,
      @Value("${my-workflow.backend.oidc.issuer-uri:}") String oidcIssuerUri,
      @Value("${my-workflow.backend.oidc.issuer:}") String oidcIssuer,
      @Value("${my-workflow.backend.oidc.jwks-uri:}") String oidcJwksUri,
      @Value("${my-workflow.backend.oidc.audience:}") String oidcAudience,
      @Value("${my-workflow.backend.oidc.user-id-claim:sub}") String oidcUserIdClaim,
      @Value("${my-workflow.backend.oidc.team-id-claim:team_id}") String oidcTeamIdClaim,
      @Value("${my-workflow.backend.oidc.display-name-claim:name}") String oidcDisplayNameClaim,
      @Value("${my-workflow.backend.oauth.introspection-uri:}") String oauthIntrospectionUri,
      @Value("${my-workflow.backend.oauth.client-id:}") String oauthClientId,
      @Value("${my-workflow.backend.oauth.client-secret-env-name:}") String oauthClientSecretEnvName,
      @Value("${my-workflow.backend.oauth.user-id-claim:sub}") String oauthUserIdClaim,
      @Value("${my-workflow.backend.oauth.team-id-claim:team_id}") String oauthTeamIdClaim,
      @Value("${my-workflow.backend.oauth.display-name-claim:name}") String oauthDisplayNameClaim
  ) {
    this.dataRoot = Path.of(dataRoot).toAbsolutePath().normalize();
    this.providerSecretFileRoot = blankToNull(providerSecretFileRoot)
        .map(value -> Path.of(value).toAbsolutePath().normalize())
        .orElse(null);
    this.devPrincipal = new DevPrincipal(devUserId, devTeamId, devDisplayName);
    this.auditRetention = new AuditRetention(auditRetentionDays, "REPORT_ONLY", false);
    this.oidc = new Oidc(
        oidcIssuerUri,
        oidcIssuer,
        oidcJwksUri,
        oidcAudience,
        oidcUserIdClaim,
        oidcTeamIdClaim,
        oidcDisplayNameClaim
    );
    this.oauthIntrospection = new OAuthIntrospection(
        oauthIntrospectionUri,
        oauthClientId,
        oauthClientSecretEnvName,
        oauthUserIdClaim,
        oauthTeamIdClaim,
        oauthDisplayNameClaim
    );
    if (this.oauthIntrospection.enabled() && !"disabled".equals(this.oidc.mode())) {
      throw new IllegalArgumentException("OAuth introspection and OIDC JWT verification are mutually exclusive");
    }
  }

  public BackendProperties(
      String dataRoot,
      String devUserId,
      String devTeamId,
      String devDisplayName
  ) {
    this(
        dataRoot,
        devUserId,
        devTeamId,
        devDisplayName,
        "",
        365,
        "",
        "",
        "",
        "",
        "sub",
        "team_id",
        "name",
        "",
        "",
        "",
        "sub",
        "team_id",
        "name"
    );
  }

  public Path dataRoot() {
    return dataRoot;
  }

  public Optional<Path> providerSecretFileRoot() {
    return Optional.ofNullable(providerSecretFileRoot);
  }

  public DevPrincipal devPrincipal() {
    return devPrincipal;
  }

  public AuditRetention auditRetention() {
    return auditRetention;
  }

  public Oidc oidc() {
    return oidc;
  }

  public OAuthIntrospection oauthIntrospection() {
    return oauthIntrospection;
  }

  public String authMode() {
    if (oauthIntrospection.enabled()) {
      return "oauth-introspection";
    }
    return oidc.mode();
  }

  public record DevPrincipal(
      String userId,
      String teamId,
      String displayName
  ) {
  }

  public record AuditRetention(
      int retentionDays,
      String mode,
      boolean destructivePurgeEnabled
  ) {
    public AuditRetention {
      if (retentionDays < 1 || retentionDays > 3650) {
        throw new IllegalArgumentException("Audit retention days must be between 1 and 3650");
      }
    }
  }

  public record Oidc(
      String issuerUri,
      String issuer,
      String jwksUri,
      String audience,
      String userIdClaim,
      String teamIdClaim,
      String displayNameClaim
  ) {
    public Oidc {
      issuerUri = blankToNull(issuerUri).orElse(null);
      issuer = blankToNull(issuer).orElse(null);
      jwksUri = blankToNull(jwksUri).orElse(null);
      audience = blankToNull(audience).orElse(null);
      userIdClaim = requiredClaimName(userIdClaim, "sub");
      teamIdClaim = requiredClaimName(teamIdClaim, "team_id");
      displayNameClaim = requiredClaimName(displayNameClaim, "name");
      if (issuerUri != null && jwksUri != null) {
        throw new IllegalArgumentException("OIDC issuer-uri and jwks-uri are mutually exclusive");
      }
      if (issuerUri != null && issuer != null && !issuerUri.equals(issuer)) {
        throw new IllegalArgumentException("OIDC issuer must match issuer-uri when both are configured");
      }
    }

    public String mode() {
      if (issuerUri != null) {
        return "oidc-discovery";
      }
      if (jwksUri != null) {
        return "oidc-jwks";
      }
      return "disabled";
    }

    public boolean discoveryEnabled() {
      return issuerUri != null;
    }

    public boolean issuerConfigured() {
      return validationIssuer() != null;
    }

    public boolean jwksUriConfigured() {
      return jwksUri != null;
    }

    public boolean audienceConfigured() {
      return audience != null;
    }

    public String validationIssuer() {
      return issuer == null ? issuerUri : issuer;
    }
  }

  public record OAuthIntrospection(
      String introspectionUri,
      String clientId,
      String clientSecretEnvName,
      String userIdClaim,
      String teamIdClaim,
      String displayNameClaim
  ) {
    public OAuthIntrospection {
      introspectionUri = blankToNull(introspectionUri).orElse(null);
      clientId = blankToNull(clientId).orElse(null);
      clientSecretEnvName = blankToNull(clientSecretEnvName).orElse(null);
      userIdClaim = requiredClaimName(userIdClaim, "sub");
      teamIdClaim = requiredClaimName(teamIdClaim, "team_id");
      displayNameClaim = requiredClaimName(displayNameClaim, "name");
      if ((clientId == null) != (clientSecretEnvName == null)) {
        throw new IllegalArgumentException(
            "OAuth introspection client-id and client-secret-env-name must be configured together"
        );
      }
      if (clientSecretEnvName != null && !clientSecretEnvName.matches("[A-Z_][A-Z0-9_]*")) {
        throw new IllegalArgumentException("OAuth introspection client-secret-env-name must be an env var name");
      }
    }

    public boolean enabled() {
      return introspectionUri != null;
    }

    public boolean clientAuthConfigured() {
      return clientId != null;
    }
  }

  private static Optional<String> blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(value.trim());
  }

  private static String requiredClaimName(String value, String defaultValue) {
    return blankToNull(value).orElse(defaultValue);
  }
}
