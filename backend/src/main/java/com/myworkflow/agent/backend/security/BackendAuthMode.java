package com.myworkflow.agent.backend.security;

import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class BackendAuthMode {

  private final Environment environment;

  public BackendAuthMode(Environment environment) {
    this.environment = environment;
  }

  public boolean devIdentityEnabled() {
    return Arrays.stream(environment.getActiveProfiles())
        .noneMatch(BackendAuthMode::isProductionProfile);
  }

  private static boolean isProductionProfile(String profile) {
    return "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile);
  }
}
