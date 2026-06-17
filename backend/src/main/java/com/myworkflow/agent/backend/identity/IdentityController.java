package com.myworkflow.agent.backend.identity;

import com.myworkflow.agent.backend.api.ApiEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class IdentityController {

  private final PrincipalProvider principalProvider;
  private final TeamDirectoryService teamDirectoryService;
  private final TeamDirectorySyncService teamDirectorySyncService;
  private final TeamInviteService teamInviteService;

  public IdentityController(
      PrincipalProvider principalProvider,
      TeamDirectoryService teamDirectoryService,
      TeamDirectorySyncService teamDirectorySyncService,
      TeamInviteService teamInviteService
  ) {
    this.principalProvider = principalProvider;
    this.teamDirectoryService = teamDirectoryService;
    this.teamDirectorySyncService = teamDirectorySyncService;
    this.teamInviteService = teamInviteService;
  }

  @GetMapping("/me")
  public ApiEnvelope<MeResponse> me() {
    BackendPrincipal principal = principalProvider.currentPrincipal();
    return ApiEnvelope.ok(new MeResponse(
        principal.userId(),
        principal.teamId(),
        principal.displayName()
    ));
  }

  @GetMapping("/teams")
  public ApiEnvelope<List<TeamResponse>> teams() {
    BackendPrincipal principal = principalProvider.currentPrincipal();
    return ApiEnvelope.ok(List.of(new TeamResponse(
        principal.teamId(),
        principal.teamId(),
        "ACTIVE"
    )));
  }

  @GetMapping("/teams/{teamId}/members")
  public ApiEnvelope<List<TeamMemberResponse>> teamMembers(@PathVariable String teamId) {
    return ApiEnvelope.ok(teamDirectoryService.listCurrentTeamMembers(teamId).stream()
        .map(IdentityController::toTeamMemberResponse)
        .toList());
  }

  @PutMapping(path = "/teams/{teamId}/members/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiEnvelope<TeamMemberResponse> upsertTeamMember(
      @PathVariable String teamId,
      @PathVariable String userId,
      @Valid @RequestBody UpsertTeamMemberRequest request
  ) {
    return ApiEnvelope.ok(toTeamMemberResponse(teamDirectoryService.upsertCurrentTeamMember(
        teamId,
        userId,
        request.displayName(),
        request.role()
    )));
  }

  @PostMapping("/teams/{teamId}/members/{userId}/disable")
  public ApiEnvelope<TeamMemberResponse> disableTeamMember(
      @PathVariable String teamId,
      @PathVariable String userId
  ) {
    return ApiEnvelope.ok(toTeamMemberResponse(teamDirectoryService.disableCurrentTeamMember(teamId, userId)));
  }

  @PostMapping(path = "/teams/{teamId}/directory-sync", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiEnvelope<DirectorySyncResponse> syncTeamDirectory(
      @PathVariable String teamId,
      @Valid @RequestBody DirectorySyncRequest request
  ) {
    rejectRawDirectoryPayloadAliases(request);
    TeamDirectorySyncService.DirectorySyncResult result = teamDirectorySyncService.syncCurrentTeamDirectory(
        teamId,
        request.source(),
        Boolean.TRUE.equals(request.disableMissing()),
        request.members().stream()
            .map((member) -> new TeamDirectorySyncService.DirectorySyncMemberInput(
                member.userId(),
                member.displayName(),
                member.role()
            ))
            .toList()
    );
    return ApiEnvelope.ok(new DirectorySyncResponse(
        result.teamId(),
        result.source(),
        result.importedCount(),
        result.disabledCount(),
        result.members().stream()
            .map(IdentityController::toTeamMemberResponse)
            .toList()
    ));
  }

  @PostMapping(path = "/teams/{teamId}/invites", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiEnvelope<TeamInviteResponse> createTeamInvite(
      @PathVariable String teamId,
      @Valid @RequestBody CreateTeamInviteRequest request
  ) {
    return ApiEnvelope.ok(toTeamInviteResponse(teamInviteService.createCurrentTeamInvite(
        teamId,
        request.inviteeUserId(),
        request.displayName(),
        request.role()
    )));
  }

  @GetMapping("/teams/{teamId}/invites")
  public ApiEnvelope<List<TeamInviteResponse>> teamInvites(@PathVariable String teamId) {
    return ApiEnvelope.ok(teamInviteService.listCurrentTeamInvites(teamId).stream()
        .map(IdentityController::toTeamInviteResponse)
        .toList());
  }

  @PostMapping("/teams/{teamId}/invites/{inviteId}/accept")
  public ApiEnvelope<TeamInviteResponse> acceptTeamInvite(
      @PathVariable String teamId,
      @PathVariable String inviteId
  ) {
    return ApiEnvelope.ok(toTeamInviteResponse(teamInviteService.acceptCurrentTeamInvite(teamId, inviteId)));
  }

  @PostMapping("/teams/{teamId}/invites/{inviteId}/revoke")
  public ApiEnvelope<TeamInviteResponse> revokeTeamInvite(
      @PathVariable String teamId,
      @PathVariable String inviteId
  ) {
    return ApiEnvelope.ok(toTeamInviteResponse(teamInviteService.revokeCurrentTeamInvite(teamId, inviteId)));
  }

  private static TeamMemberResponse toTeamMemberResponse(TeamMemberRecord member) {
    return new TeamMemberResponse(
        member.teamId(),
        member.userId(),
        member.displayName(),
        member.role(),
        member.status()
    );
  }

  private static TeamInviteResponse toTeamInviteResponse(TeamInviteRecord invite) {
    return new TeamInviteResponse(
        invite.id(),
        invite.teamId(),
        invite.inviteeUserId(),
        invite.displayName(),
        invite.role(),
        invite.status(),
        invite.createdByUserId(),
        invite.createdAt(),
        invite.updatedAt()
    );
  }

  private static void rejectRawDirectoryPayloadAliases(DirectorySyncRequest request) {
    if (request.token() != null
        || request.authorization() != null
        || request.Authorization() != null
        || request.secret() != null
        || request.rawPayload() != null) {
      throw new IllegalArgumentException("Raw directory payloads are not accepted");
    }
  }

  public record MeResponse(
      String userId,
      String teamId,
      String displayName
  ) {
  }

  public record TeamResponse(
      String teamId,
      String name,
      String status
  ) {
  }

  public record TeamMemberResponse(
      String teamId,
      String userId,
      String displayName,
      TeamRole role,
      TeamMemberStatus status
  ) {
  }

  public record UpsertTeamMemberRequest(
      String displayName,
      @NotNull TeamRole role
  ) {
  }

  public record DirectorySyncRequest(
      String source,
      Boolean disableMissing,
      @NotNull List<DirectorySyncMemberRequest> members,
      String token,
      String authorization,
      String Authorization,
      String secret,
      String rawPayload
  ) {
  }

  public record DirectorySyncMemberRequest(
      String userId,
      String displayName,
      @NotNull TeamRole role
  ) {
  }

  public record DirectorySyncResponse(
      String teamId,
      String source,
      int importedCount,
      int disabledCount,
      List<TeamMemberResponse> members
  ) {
  }

  public record CreateTeamInviteRequest(
      String inviteeUserId,
      String displayName,
      @NotNull TeamRole role
  ) {
  }

  public record TeamInviteResponse(
      String id,
      String teamId,
      String inviteeUserId,
      String displayName,
      TeamRole role,
      TeamInviteStatus status,
      String createdByUserId,
      Instant createdAt,
      Instant updatedAt
  ) {
  }
}
