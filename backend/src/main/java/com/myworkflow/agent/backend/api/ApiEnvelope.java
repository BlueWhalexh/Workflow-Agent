package com.myworkflow.agent.backend.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record ApiEnvelope<T>(
    String schemaVersion,
    boolean ok,
    T data,
    ApiError error
) {
  public static final String SCHEMA_VERSION = "java-backend-api.v1";

  public static <T> ApiEnvelope<T> ok(T data) {
    return new ApiEnvelope<>(SCHEMA_VERSION, true, data, null);
  }

  public static ApiEnvelope<Void> failure(ApiError error) {
    return new ApiEnvelope<>(SCHEMA_VERSION, false, null, error);
  }
}
