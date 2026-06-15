package com.myworkflow.agent.backend.providersecret;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProviderRuntimePolicy {

  private static final Pattern SAFE_REF_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
  private static final Pattern ENV_VAR_NAME_PATTERN = Pattern.compile("[A-Z_][A-Z0-9_]*");
  private static final Set<String> SUPPORTED_PROVIDERS = Set.of(
      "fake",
      "deepseek-fixture",
      "deepseek-real",
      "claude-code-fixture",
      "mimo-vllm-fixture",
      "mimo-real",
      "weak-relations-fixture",
      "timeout-fixture",
      "invalid-content-fixture"
  );
  private static final Set<String> RAW_SECRET_CONFIG_KEYS = Set.of(
      "api-key",
      "token",
      "authorization"
  );

  private final Environment environment;

  public ProviderRuntimePolicy(Environment environment) {
    this.environment = environment;
  }

  public Map<String, Object> resolve(String mode, boolean execute, String providerRuntimeRef) {
    String normalizedRef = normalizeRef(providerRuntimeRef);
    if (normalizedRef == null) {
      return modeNeedsProviderRuntime(mode, execute) ? fakeProviderRuntime() : null;
    }
    if ("fake".equals(normalizedRef)) {
      return fakeProviderRuntime();
    }
    if (!SAFE_REF_PATTERN.matcher(normalizedRef).matches()) {
      throw new IllegalArgumentException("Invalid provider runtime reference");
    }

    String prefix = "my-workflow.backend.provider-runtime.refs.%s.".formatted(normalizedRef);
    String provider = environment.getProperty(prefix + "provider");
    if (provider == null || provider.isBlank()) {
      throw new IllegalArgumentException("Unknown provider runtime reference");
    }
    rejectRawSecretConfig(prefix);
    if (!SUPPORTED_PROVIDERS.contains(provider)) {
      throw new IllegalArgumentException("Unsupported provider runtime provider");
    }

    Map<String, Object> runtime = new LinkedHashMap<>();
    runtime.put("provider", provider);
    putString(runtime, "model", environment.getProperty(prefix + "model"));
    putString(runtime, "baseUrl", environment.getProperty(prefix + "base-url"));
    putApiKeyEnvName(runtime, environment.getProperty(prefix + "api-key-env-name"));
    putInteger(runtime, "timeoutMs", environment.getProperty(prefix + "timeout-ms"));
    putInteger(runtime, "maxTokens", environment.getProperty(prefix + "max-tokens"));
    putDouble(runtime, "temperature", environment.getProperty(prefix + "temperature"));
    if (!runtime.containsKey("timeoutMs")) {
      runtime.put("timeoutMs", 30_000);
    }
    return Map.copyOf(runtime);
  }

  private static boolean modeNeedsProviderRuntime(String mode, boolean execute) {
    return "llm-open-agent".equals(mode) || ("fixed-workflow".equals(mode) && execute);
  }

  private static Map<String, Object> fakeProviderRuntime() {
    return Map.of("provider", "fake", "timeoutMs", 30_000);
  }

  private static String normalizeRef(String providerRuntimeRef) {
    if (providerRuntimeRef == null || providerRuntimeRef.isBlank()) {
      return null;
    }
    return providerRuntimeRef.trim();
  }

  private void rejectRawSecretConfig(String prefix) {
    for (String rawSecretConfigKey : RAW_SECRET_CONFIG_KEYS) {
      String rawSecretValue = environment.getProperty(prefix + rawSecretConfigKey);
      if (rawSecretValue != null && !rawSecretValue.isBlank()) {
        throw new IllegalArgumentException("Provider runtime reference must not configure raw secret values");
      }
    }
  }

  private static void putString(Map<String, Object> runtime, String key, String value) {
    if (value != null && !value.isBlank()) {
      runtime.put(key, value.trim());
    }
  }

  private static void putApiKeyEnvName(Map<String, Object> runtime, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    String trimmedValue = value.trim();
    if (!ENV_VAR_NAME_PATTERN.matcher(trimmedValue).matches()) {
      throw new IllegalArgumentException("api-key-env-name must be an environment variable name");
    }
    runtime.put("apiKeyEnvName", trimmedValue);
  }

  private static void putInteger(Map<String, Object> runtime, String key, String value) {
    if (value != null && !value.isBlank()) {
      runtime.put(key, Integer.parseInt(value.trim()));
    }
  }

  private static void putDouble(Map<String, Object> runtime, String key, String value) {
    if (value != null && !value.isBlank()) {
      runtime.put(key, Double.parseDouble(value.trim()));
    }
  }
}
