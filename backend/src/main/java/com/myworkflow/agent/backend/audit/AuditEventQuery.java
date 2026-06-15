package com.myworkflow.agent.backend.audit;

public record AuditEventQuery(
    int limit,
    int offset,
    String eventType,
    String runId
) {
  public static final int DEFAULT_LIMIT = 100;
  public static final int MAX_LIMIT = 100;

  public AuditEventQuery {
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("Audit event limit must be between 1 and 100");
    }
    if (offset < 0) {
      throw new IllegalArgumentException("Audit event offset must be greater than or equal to 0");
    }
    eventType = normalizeOptional(eventType);
    runId = normalizeOptional(runId);
  }

  public static AuditEventQuery defaultQuery() {
    return new AuditEventQuery(DEFAULT_LIMIT, 0, null, null);
  }

  private static String normalizeOptional(String value) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return value.trim();
  }
}
