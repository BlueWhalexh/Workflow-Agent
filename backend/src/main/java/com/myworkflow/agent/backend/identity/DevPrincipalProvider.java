package com.myworkflow.agent.backend.identity;

import com.myworkflow.agent.backend.config.BackendProperties;
import com.myworkflow.agent.backend.security.BackendAuthMode;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Component
public class DevPrincipalProvider implements PrincipalProvider {

  private final BackendProperties properties;
  private final BackendAuthMode authMode;

  public DevPrincipalProvider(BackendProperties properties, BackendAuthMode authMode) {
    this.properties = properties;
    this.authMode = authMode;
  }

  @Override
  public BackendPrincipal currentPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof BackendPrincipal principal) {
      return principal;
    }

    if (!authMode.devIdentityEnabled()) {
      throw new AuthenticationRequiredException();
    }

    BackendProperties.DevPrincipal devPrincipal = properties.devPrincipal();
    return new BackendPrincipal(
        devPrincipal.userId(),
        devPrincipal.teamId(),
        devPrincipal.displayName()
    );
  }
}
