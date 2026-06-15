package com.myworkflow.agent.backend.run;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RunEventService {

  private final AgentRunService agentRunService;
  private final RunEventRepository runEventRepository;

  public RunEventService(
      AgentRunService agentRunService,
      RunEventRepository runEventRepository
  ) {
    this.agentRunService = agentRunService;
    this.runEventRepository = runEventRepository;
  }

  public List<RunEventRecord> listRunEvents(String runId) {
    agentRunService.getRun(runId);
    return runEventRepository.findByRunId(runId);
  }

  public AuthorizedRunEventStream authorizeRunEventStream(String runId) {
    agentRunService.getRun(runId);
    return new AuthorizedRunEventStream(runId);
  }

  public List<RunEventRecord> listAuthorizedRunEvents(AuthorizedRunEventStream stream) {
    return runEventRepository.findByRunId(stream.runId());
  }

  public record AuthorizedRunEventStream(String runId) {
  }
}
