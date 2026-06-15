package com.myworkflow.agent.backend.identity;

import com.myworkflow.agent.backend.api.ApiEnvelope;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class IdentityController {

  private final PrincipalProvider principalProvider;
  private final TeamDirectoryService teamDirectoryService;

  public IdentityController(
      PrincipalProvider principalProvider,
      TeamDirectoryService teamDirectoryService
  ) {
    this.principalProvider = principalProvider;
    this.teamDirectoryService = teamDirectoryService;
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

  private static TeamMemberResponse toTeamMemberResponse(TeamMemberRecord member) {
    return new TeamMemberResponse(
        member.teamId(),
        member.userId(),
        member.role()
    );
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
      TeamRole role
  ) {
  }
}
