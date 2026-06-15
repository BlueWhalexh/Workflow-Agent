package com.myworkflow.agent.backend.providersecret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProviderRuntimePolicyTest {

  @Test
  void returnsNullWhenModeDoesNotNeedProviderRuntimeAndRefIsBlank() {
    ProviderRuntimePolicy policy = new ProviderRuntimePolicy(new MockEnvironment());

    assertThat(policy.resolve("deterministic-open-agent", false, null)).isNull();
  }

  @Test
  void keepsExistingFakeProviderDefaultForLlmModes() {
    ProviderRuntimePolicy policy = new ProviderRuntimePolicy(new MockEnvironment());

    assertThat(policy.resolve("llm-open-agent", false, null))
        .containsEntry("provider", "fake")
        .containsEntry("timeoutMs", 30_000);
    assertThat(policy.resolve("fixed-workflow", true, ""))
        .containsEntry("provider", "fake")
        .containsEntry("timeoutMs", 30_000);
  }

  @Test
  void resolvesConfiguredRealProviderReferenceWithoutSecretValue() {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("my-workflow.backend.provider-runtime.refs.mimo-safe.provider", "mimo-real")
        .withProperty("my-workflow.backend.provider-runtime.refs.mimo-safe.api-key-env-name", "MIMO_API_KEY")
        .withProperty("my-workflow.backend.provider-runtime.refs.mimo-safe.model", "mimo-v2.5")
        .withProperty("my-workflow.backend.provider-runtime.refs.mimo-safe.base-url", "https://token-plan-cn.xiaomimimo.com/v1")
        .withProperty("my-workflow.backend.provider-runtime.refs.mimo-safe.timeout-ms", "45000");
    ProviderRuntimePolicy policy = new ProviderRuntimePolicy(environment);

    Map<String, Object> providerRuntime = policy.resolve("llm-open-agent", false, "mimo-safe");

    assertThat(providerRuntime)
        .containsEntry("provider", "mimo-real")
        .containsEntry("apiKeyEnvName", "MIMO_API_KEY")
        .containsEntry("model", "mimo-v2.5")
        .containsEntry("baseUrl", "https://token-plan-cn.xiaomimimo.com/v1")
        .containsEntry("timeoutMs", 45_000);
    assertThat(providerRuntime).doesNotContainKeys("apiKey", "token", "authorization", "Authorization");
  }

  @Test
  void rejectsConfiguredProviderReferencesWithRawSecretValues() {
    assertRawSecretConfigIsRejected("api-key");
    assertRawSecretConfigIsRejected("token");
    assertRawSecretConfigIsRejected("authorization");
  }

  @Test
  void rejectsConfiguredProviderReferencesWithInvalidApiKeyEnvName() {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("my-workflow.backend.provider-runtime.refs.mimo-invalid-env.provider", "mimo-real")
        .withProperty(
            "my-workflow.backend.provider-runtime.refs.mimo-invalid-env.api-key-env-name",
            "fixture-secret-value");
    ProviderRuntimePolicy policy = new ProviderRuntimePolicy(environment);

    assertThatThrownBy(() -> policy.resolve("llm-open-agent", false, "mimo-invalid-env"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("api-key-env-name must be an environment variable name");
  }

  @Test
  void rejectsUnknownAndInvalidProviderRuntimeReferences() {
    ProviderRuntimePolicy policy = new ProviderRuntimePolicy(new MockEnvironment());

    assertThatThrownBy(() -> policy.resolve("llm-open-agent", false, "missing-ref"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown provider runtime reference");
    assertThatThrownBy(() -> policy.resolve("llm-open-agent", false, "../mimo"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid provider runtime reference");
  }

  private static void assertRawSecretConfigIsRejected(String secretPropertyName) {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("my-workflow.backend.provider-runtime.refs.mimo-unsafe.provider", "mimo-real")
        .withProperty(
            "my-workflow.backend.provider-runtime.refs.mimo-unsafe.%s".formatted(secretPropertyName),
            "fixture-secret-value");
    ProviderRuntimePolicy policy = new ProviderRuntimePolicy(environment);

    assertThatThrownBy(() -> policy.resolve("llm-open-agent", false, "mimo-unsafe"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Provider runtime reference must not configure raw secret values");
  }
}
