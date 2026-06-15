package com.myworkflow.agent.backend.artifact;

public class ArtifactNotFoundException extends RuntimeException {

  public ArtifactNotFoundException(String artifactId) {
    super("Artifact not found: " + artifactId);
  }
}
