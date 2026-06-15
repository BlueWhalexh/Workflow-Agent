package com.myworkflow.agent.backend.workspace;

public class WorkspaceNotFoundException extends RuntimeException {

  public WorkspaceNotFoundException(String workspaceId) {
    super("Workspace not found: " + workspaceId);
  }
}
