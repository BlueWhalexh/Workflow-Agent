package com.myworkflow.agent.backend.workspace;

public class WorkspaceForbiddenException extends RuntimeException {

  public WorkspaceForbiddenException(String workspaceId) {
    super("Workspace access denied: " + workspaceId);
  }
}
