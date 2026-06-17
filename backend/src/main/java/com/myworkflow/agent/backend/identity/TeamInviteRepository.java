package com.myworkflow.agent.backend.identity;

import java.util.List;
import java.util.Optional;

public interface TeamInviteRepository {

  TeamInviteRecord create(TeamInviteRecord invite);

  List<TeamInviteRecord> listByTeam(String teamId);

  Optional<TeamInviteRecord> findById(String teamId, String inviteId);

  TeamInviteRecord updateStatus(String teamId, String inviteId, TeamInviteStatus status);
}
