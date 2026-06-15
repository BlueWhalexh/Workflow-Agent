package com.myworkflow.agent.backend.identity;

public class TeamForbiddenException extends RuntimeException {

  public TeamForbiddenException(String teamId) {
    super("Current user cannot access team: " + teamId);
  }
}
