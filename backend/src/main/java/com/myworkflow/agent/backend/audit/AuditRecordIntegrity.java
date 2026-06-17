package com.myworkflow.agent.backend.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class AuditRecordIntegrity {

  static final String SIGNATURE_KIND = "sha256-chain-v1";

  private AuditRecordIntegrity() {
  }

  static IntegrityFields forEvent(
      String auditEventId,
      String actorUserId,
      String teamId,
      String workspaceId,
      String runId,
      String eventType,
      String message,
      String createdAt,
      String previousRecordDigest
  ) {
    String recordDigest = recordDigest(
        auditEventId,
        actorUserId,
        teamId,
        workspaceId,
        runId,
        eventType,
        message,
        createdAt
    );
    String chainDigest = chainDigest(previousRecordDigest, recordDigest);
    return new IntegrityFields(
        recordDigest,
        previousRecordDigest,
        chainDigest,
        SIGNATURE_KIND,
        chainDigest
    );
  }

  static String recordDigest(
      String auditEventId,
      String actorUserId,
      String teamId,
      String workspaceId,
      String runId,
      String eventType,
      String message,
      String createdAt
  ) {
    String canonical = String.join("\n",
        nullToEmpty(auditEventId),
        nullToEmpty(actorUserId),
        nullToEmpty(teamId),
        nullToEmpty(workspaceId),
        nullToEmpty(runId),
        nullToEmpty(eventType),
        nullToEmpty(message),
        createdAt
    );
    return sha256(canonical);
  }

  private static String chainDigest(String previousRecordDigest, String recordDigest) {
    String canonical = String.join("\n",
        nullToEmpty(previousRecordDigest),
        recordDigest
    );
    return sha256(canonical);
  }

  private static String sha256(String canonical) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(canonical.getBytes(StandardCharsets.UTF_8));
      return "sha256:" + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest algorithm is unavailable", exception);
    }
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  record IntegrityFields(
      String recordDigest,
      String previousRecordDigest,
      String chainDigest,
      String signatureKind,
      String signatureValue
  ) {
  }
}
