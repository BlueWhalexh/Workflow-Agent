package com.myworkflow.agent.backend.workspace;

import com.myworkflow.agent.backend.api.ApiEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/v1/workspaces", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkspaceController {

  private final WorkspaceService workspaceService;

  public WorkspaceController(WorkspaceService workspaceService) {
    this.workspaceService = workspaceService;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiEnvelope<WorkspaceResponse> createWorkspace(
      @Valid @RequestBody CreateWorkspaceRequest request
  ) {
    return ApiEnvelope.ok(toResponse(workspaceService.registerWorkspace(
        request.name(),
        request.defaultBranch()
    )));
  }

  @GetMapping
  public ApiEnvelope<List<WorkspaceResponse>> listWorkspaces() {
    return ApiEnvelope.ok(workspaceService.listVisibleWorkspaces().stream()
        .map(WorkspaceController::toResponse)
        .toList());
  }

  @GetMapping("/{workspaceId}")
  public ApiEnvelope<WorkspaceResponse> getWorkspace(@PathVariable String workspaceId) {
    return ApiEnvelope.ok(toResponse(workspaceService.getWorkspace(workspaceId)));
  }

  @GetMapping("/{workspaceId}/members")
  public ApiEnvelope<List<WorkspaceMemberResponse>> listMembers(@PathVariable String workspaceId) {
    return ApiEnvelope.ok(workspaceService.listMembers(workspaceId).stream()
        .map(WorkspaceController::toMemberResponse)
        .toList());
  }

  @PutMapping(path = "/{workspaceId}/members/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiEnvelope<WorkspaceMemberResponse> grantMember(
      @PathVariable String workspaceId,
      @PathVariable String userId,
      @Valid @RequestBody GrantWorkspaceMemberRequest request
  ) {
    return ApiEnvelope.ok(toMemberResponse(workspaceService.grantMember(
        workspaceId,
        userId,
        request.teamId(),
        request.role()
    )));
  }

  @DeleteMapping("/{workspaceId}/members/{userId}")
  public ApiEnvelope<WorkspaceMemberResponse> removeMember(
      @PathVariable String workspaceId,
      @PathVariable String userId
  ) {
    return ApiEnvelope.ok(toMemberResponse(workspaceService.removeMember(workspaceId, userId)));
  }

  @PostMapping(path = "/{workspaceId}/owner-transfer", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiEnvelope<WorkspaceMemberResponse> transferOwnership(
      @PathVariable String workspaceId,
      @Valid @RequestBody TransferWorkspaceOwnerRequest request
  ) {
    return ApiEnvelope.ok(toMemberResponse(workspaceService.transferOwnership(
        workspaceId,
        request.newOwnerUserId()
    )));
  }

  private static WorkspaceResponse toResponse(WorkspaceRecord workspace) {
    return new WorkspaceResponse(
        workspace.workspaceId(),
        workspace.name(),
        workspace.defaultBranch(),
        workspace.status()
    );
  }

  private static WorkspaceMemberResponse toMemberResponse(WorkspaceMemberRecord member) {
    return new WorkspaceMemberResponse(
        member.workspaceId(),
        member.userId(),
        member.teamId(),
        member.role()
    );
  }

  public record CreateWorkspaceRequest(
      @NotBlank String name,
      String defaultBranch
  ) {
  }

  public record WorkspaceResponse(
      String workspaceId,
      String name,
      String defaultBranch,
      WorkspaceStatus status
  ) {
  }

  public record GrantWorkspaceMemberRequest(
      @NotBlank String teamId,
      @NotNull WorkspaceRole role
  ) {
  }

  public record TransferWorkspaceOwnerRequest(
      @NotBlank String newOwnerUserId
  ) {
  }

  public record WorkspaceMemberResponse(
      String workspaceId,
      String userId,
      String teamId,
      WorkspaceRole role
  ) {
  }
}
