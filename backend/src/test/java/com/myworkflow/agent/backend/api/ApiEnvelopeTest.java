package com.myworkflow.agent.backend.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiEnvelopeTest {

  @Test
  void okCreatesStableSuccessEnvelope() {
    ApiEnvelope<String> envelope = ApiEnvelope.ok("ready");

    assertThat(envelope.schemaVersion()).isEqualTo("java-backend-api.v1");
    assertThat(envelope.ok()).isTrue();
    assertThat(envelope.data()).isEqualTo("ready");
    assertThat(envelope.error()).isNull();
  }

  @Test
  void failureCreatesStableErrorEnvelope() {
    ApiError error = new ApiError("BACKEND_ERROR", "Backend error", false);

    ApiEnvelope<Void> envelope = ApiEnvelope.failure(error);

    assertThat(envelope.schemaVersion()).isEqualTo("java-backend-api.v1");
    assertThat(envelope.ok()).isFalse();
    assertThat(envelope.data()).isNull();
    assertThat(envelope.error()).isEqualTo(error);
  }
}
