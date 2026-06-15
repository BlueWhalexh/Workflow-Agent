package com.myworkflow.agent.backend.workspace;

public class InvalidWorkspacePathException extends RuntimeException {

  public InvalidWorkspacePathException(String relativePath) {
    super("Invalid workspace path: " + relativePath);
  }
}
