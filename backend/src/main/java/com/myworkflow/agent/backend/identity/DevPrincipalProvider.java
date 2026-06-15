package com.myworkflow.agent.backend.identity;

import com.myworkflow.agent.backend.config.BackendProperties;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Component
public class DevPrincipalProvider implements PrincipalProvider {

  private final BackendProperties properties;

  public DevPrincipalProvider(BackendProperties properties) {
    this.properties = properties;
  }

  @Override
  public BackendPrincipal currentPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof BackendPrincipal principal) {
      return principal;
    }

    BackendProperties.DevPrincipal devPrincipal = properties.devPrincipal();
    return new BackendPrincipal(
        devPrincipal.userId(),
        devPrincipal.teamId(),
        devPrincipal.displayName()
    );
  }
}
