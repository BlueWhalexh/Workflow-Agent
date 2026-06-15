package com.myworkflow.agent.backend.api;

public record ApiError(
    String code,
    String message,
    boolean retryable
) {
}
