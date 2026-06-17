package com.myworkflow.agent.backend.api;

import jakarta.validation.ConstraintViolationException;
import com.myworkflow.agent.backend.approval.ApprovalNotFoundException;
import com.myworkflow.agent.backend.artifact.ArtifactNotFoundException;
import com.myworkflow.agent.backend.identity.AuthenticationRequiredException;
import com.myworkflow.agent.backend.identity.TeamForbiddenException;
import com.myworkflow.agent.backend.run.AgentRunNotFoundException;
import com.myworkflow.agent.backend.workspace.InvalidWorkspacePathException;
import com.myworkflow.agent.backend.workspace.WorkspaceForbiddenException;
import com.myworkflow.agent.backend.workspace.WorkspaceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiEnvelope<Void> handleValidationError(MethodArgumentNotValidException exception) {
    return ApiEnvelope.failure(new ApiError(
        "VALIDATION_ERROR",
        "Request validation failed",
        false
    ));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiEnvelope<Void> handleConstraintViolation(ConstraintViolationException exception) {
    return ApiEnvelope.failure(new ApiError(
        "VALIDATION_ERROR",
        "Request validation failed",
        false
    ));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiEnvelope<Void> handleIllegalArgument(IllegalArgumentException exception) {
    return ApiEnvelope.failure(new ApiError(
        "VALIDATION_ERROR",
        "Request validation failed",
        false
    ));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiEnvelope<Void> handleUnreadableMessage(HttpMessageNotReadableException exception) {
    return ApiEnvelope.failure(new ApiError(
        "INVALID_REQUEST",
        "Request body is invalid",
        false
    ));
  }

  @ExceptionHandler(InvalidWorkspacePathException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiEnvelope<Void> handleInvalidWorkspacePath(InvalidWorkspacePathException exception) {
    return ApiEnvelope.failure(new ApiError(
        "WORKSPACE_PATH_INVALID",
        "Workspace path is invalid",
        false
    ));
  }

  @ExceptionHandler(WorkspaceForbiddenException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiEnvelope<Void> handleWorkspaceForbidden(WorkspaceForbiddenException exception) {
    return ApiEnvelope.failure(new ApiError(
        "WORKSPACE_FORBIDDEN",
        "The current user cannot access this workspace",
        false
    ));
  }

  @ExceptionHandler(TeamForbiddenException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiEnvelope<Void> handleTeamForbidden(TeamForbiddenException exception) {
    return ApiEnvelope.failure(new ApiError(
        "TEAM_FORBIDDEN",
        "The current user cannot access this team",
        false
    ));
  }

  @ExceptionHandler(AuthenticationRequiredException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ApiEnvelope<Void> handleAuthenticationRequired(AuthenticationRequiredException exception) {
    return ApiEnvelope.failure(new ApiError(
        "AUTHENTICATION_REQUIRED",
        "Authentication is required",
        false
    ));
  }

  @ExceptionHandler(WorkspaceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiEnvelope<Void> handleWorkspaceNotFound(WorkspaceNotFoundException exception) {
    return ApiEnvelope.failure(new ApiError(
        "WORKSPACE_NOT_FOUND",
        "Workspace not found",
        false
    ));
  }

  @ExceptionHandler(AgentRunNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiEnvelope<Void> handleAgentRunNotFound(AgentRunNotFoundException exception) {
    return ApiEnvelope.failure(new ApiError(
        "AGENT_RUN_NOT_FOUND",
        "Agent run not found",
        false
    ));
  }

  @ExceptionHandler(ArtifactNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiEnvelope<Void> handleArtifactNotFound(ArtifactNotFoundException exception) {
    return ApiEnvelope.failure(new ApiError(
        "ARTIFACT_NOT_FOUND",
        "Artifact not found",
        false
    ));
  }

  @ExceptionHandler(ApprovalNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiEnvelope<Void> handleApprovalNotFound(ApprovalNotFoundException exception) {
    return ApiEnvelope.failure(new ApiError(
        "APPROVAL_NOT_FOUND",
        "Approval request not found",
        false
    ));
  }

  @ExceptionHandler({
      NoHandlerFoundException.class,
      NoResourceFoundException.class
  })
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiEnvelope<Void> handleNotFound(Exception exception) {
    return ApiEnvelope.failure(new ApiError(
        "NOT_FOUND",
        "Resource not found",
        false
    ));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
  public ApiEnvelope<Void> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
    return ApiEnvelope.failure(new ApiError(
        "METHOD_NOT_ALLOWED",
        "Method not allowed",
        false
    ));
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiEnvelope<Void> handleUnexpectedError(Exception exception) {
    return ApiEnvelope.failure(new ApiError(
        "INTERNAL_ERROR",
        "Internal backend error",
        false
    ));
  }
}
