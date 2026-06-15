package com.myworkflow.agent.backend.artifact;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class ArtifactRefFactory {

  private ArtifactRefFactory() {
  }

  static List<ArtifactRefRecord> create(
      String runId,
      String workspaceId,
      List<String> artifactRefs,
      Instant now
  ) {
    List<ArtifactRefRecord> records = new ArrayList<>();
    int sortOrder = 0;
    for (String artifactRef : artifactRefs == null ? List.<String>of() : artifactRefs) {
      if (!isSafeRelativeRef(artifactRef)) {
        continue;
      }
      records.add(new ArtifactRefRecord(
          "art_" + UUID.randomUUID().toString().replace("-", ""),
          runId,
          workspaceId,
          artifactRef,
          kindFor(artifactRef),
          redactionStatusFor(artifactRef),
          contentTypeFor(artifactRef),
          sortOrder,
          now
      ));
      sortOrder++;
    }
    return records;
  }

  private static boolean isSafeRelativeRef(String artifactRef) {
    if (artifactRef == null || artifactRef.isBlank()) {
      return false;
    }
    Path path = Path.of(artifactRef);
    if (path.isAbsolute()) {
      return false;
    }
    Path normalized = path.normalize();
    for (Path segment : normalized) {
      if ("..".equals(segment.toString())) {
        return false;
      }
    }
    return !normalized.toString().isBlank();
  }

  private static String kindFor(String artifactRef) {
    if (artifactRef.contains("/raw-provider/")) {
      return "RAW_PROVIDER";
    }
    if (artifactRef.endsWith("/report.md")) {
      return "REPORT";
    }
    if (artifactRef.endsWith(".jsonl")) {
      return "TRACE";
    }
    return "ARTIFACT";
  }

  private static String redactionStatusFor(String artifactRef) {
    return artifactRef.contains("/raw-provider/") ? "REDACTED" : "NOT_REQUIRED";
  }

  private static String contentTypeFor(String artifactRef) {
    if (artifactRef.endsWith(".md")) {
      return "text/markdown";
    }
    if (artifactRef.endsWith(".json")) {
      return "application/json";
    }
    if (artifactRef.endsWith(".jsonl")) {
      return "application/jsonl";
    }
    return "text/plain";
  }
}
