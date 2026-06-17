package com.myworkflow.agent.backend.identity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!jdbc")
public class InMemoryTeamInviteRepository implements TeamInviteRepository {

  private final Map<String, TeamInviteRecord> invites = new ConcurrentHashMap<>();

  @Override
  public TeamInviteRecord create(TeamInviteRecord invite) {
    invites.put(invite.id(), invite);
    return invite;
  }

  @Override
  public List<TeamInviteRecord> listByTeam(String teamId) {
    return invites.values().stream()
        .filter(invite -> invite.teamId().equals(teamId))
        .sorted(Comparator.comparing(TeamInviteRecord::createdAt).thenComparing(TeamInviteRecord::id))
        .toList();
  }

  @Override
  public Optional<TeamInviteRecord> findById(String teamId, String inviteId) {
    return Optional.ofNullable(invites.get(inviteId))
        .filter(invite -> invite.teamId().equals(teamId));
  }

  @Override
  public TeamInviteRecord updateStatus(String teamId, String inviteId, TeamInviteStatus status) {
    final List<TeamInviteRecord> updated = new ArrayList<>(1);
    invites.compute(inviteId, (ignored, existing) -> {
      if (existing == null || !existing.teamId().equals(teamId)) {
        return existing;
      }
      TeamInviteRecord next = new TeamInviteRecord(
          existing.id(),
          existing.teamId(),
          existing.inviteeUserId(),
          existing.displayName(),
          existing.role(),
          status,
          existing.createdByUserId(),
          existing.createdAt(),
          Instant.now()
      );
      updated.add(next);
      return next;
    });
    if (updated.isEmpty()) {
      throw new IllegalArgumentException("Team invite not found");
    }
    return updated.get(0);
  }
}
