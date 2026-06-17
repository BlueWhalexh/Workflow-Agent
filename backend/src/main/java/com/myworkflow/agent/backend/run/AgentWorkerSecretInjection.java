package com.myworkflow.agent.backend.run;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public record AgentWorkerSecretInjection(Map<String, String> environmentVariables) {

  private static final Pattern ENV_NAME_PATTERN = Pattern.compile("[A-Z_][A-Z0-9_]{0,127}");

  public AgentWorkerSecretInjection {
    environmentVariables = normalizeEnvironmentVariables(environmentVariables);
  }

  public static AgentWorkerSecretInjection empty() {
    return new AgentWorkerSecretInjection(Map.of());
  }

  public boolean isEmpty() {
    return environmentVariables.isEmpty();
  }

  @Override
  public String toString() {
    return "AgentWorkerSecretInjection[environmentVariableNames=" + environmentVariables.keySet() + "]";
  }

  private static Map<String, String> normalizeEnvironmentVariables(Map<String, String> environmentVariables) {
    if (environmentVariables == null || environmentVariables.isEmpty()) {
      return Map.of();
    }
    Map<String, String> normalized = new LinkedHashMap<>();
    environmentVariables.forEach((name, value) -> {
      if (name == null || !ENV_NAME_PATTERN.matcher(name).matches()) {
        throw new IllegalArgumentException("Injected secret environment variable name is invalid");
      }
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("Injected secret value is required");
      }
      normalized.put(name, value);
    });
    return Map.copyOf(normalized);
  }
}
