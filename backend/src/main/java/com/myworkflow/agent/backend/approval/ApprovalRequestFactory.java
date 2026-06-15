package com.myworkflow.agent.backend.approval;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class ApprovalRequestFactory {

  private ApprovalRequestFactory() {
  }

  static ApprovalRequestRecord createPending(
      String runId,
      String workspaceId,
      String requestedByUserId,
      String artifactRef,
      List<String> targetWorkspacePaths,
      Instant now
  ) {
    return new ApprovalRequestRecord(
        "apr_" + UUID.randomUUID().toString().replace("-", ""),
        runId,
        workspaceId,
        requestedByUserId,
        null,
        null,
        ApprovalStatus.PENDING,
        safeRelativeOrNull(artifactRef),
        safeTargetPaths(targetWorkspacePaths),
        now,
        null
    );
  }

  private static String safeRelativeOrNull(String artifactRef) {
    if (artifactRef == null || artifactRef.isBlank()) {
      return null;
    }
    return isSafeRelativePath(artifactRef) ? artifactRef : null;
  }

  private static List<String> safeTargetPaths(List<String> targetWorkspacePaths) {
    if (targetWorkspacePaths == null) {
      return List.of();
    }
    return targetWorkspacePaths.stream()
        .filter(ApprovalRequestFactory::isSafeRelativePath)
        .toList();
  }

  private static boolean isSafeRelativePath(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    Path path = Path.of(value);
    if (path.isAbsolute()) {
      return false;
    }
    for (Path segment : path.normalize()) {
      if ("..".equals(segment.toString())) {
        return false;
      }
    }
    return true;
  }
}
