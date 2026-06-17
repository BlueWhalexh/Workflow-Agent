package com.myworkflow.agent.backend.runner;

import com.myworkflow.agent.backend.api.ApiEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Profile("jdbc")
@RequestMapping(path = "/v1/workspaces/{workspaceId}/remote-runners", produces = MediaType.APPLICATION_JSON_VALUE)
public class RemoteRunnerController {

  private final RemoteRunnerService remoteRunnerService;

  public RemoteRunnerController(RemoteRunnerService remoteRunnerService) {
    this.remoteRunnerService = remoteRunnerService;
  }

  @GetMapping
  public ApiEnvelope<List<RemoteRunnerService.RemoteRunnerPublicMetadata>> listRemoteRunners(
      @PathVariable String workspaceId
  ) {
    return ApiEnvelope.ok(remoteRunnerService.listWorkspaceRunners(workspaceId));
  }

  @PutMapping(path = "/{runnerRef}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiEnvelope<RemoteRunnerService.RemoteRunnerPublicMetadata> upsertRemoteRunner(
      @PathVariable String workspaceId,
      @PathVariable String runnerRef,
      @Valid @RequestBody UpsertRemoteRunnerRequest request
  ) {
    rejectRawSecretAliases(request);
    return ApiEnvelope.ok(remoteRunnerService.upsertWorkspaceRunner(
        workspaceId,
        runnerRef,
        request.displayName(),
        request.endpointUrl(),
        request.capabilities()
    ));
  }

  @PostMapping(path = "/{runnerRef}/heartbeat")
  public ApiEnvelope<RemoteRunnerService.RemoteRunnerPublicMetadata> recordHeartbeat(
      @PathVariable String workspaceId,
      @PathVariable String runnerRef
  ) {
    return ApiEnvelope.ok(remoteRunnerService.recordHeartbeat(workspaceId, runnerRef));
  }

  @PostMapping(path = "/{runnerRef}/lease", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiEnvelope<RemoteRunnerService.RemoteRunnerPublicMetadata> claimLease(
      @PathVariable String workspaceId,
      @PathVariable String runnerRef,
      @Valid @RequestBody ClaimLeaseRequest request
  ) {
    return ApiEnvelope.ok(remoteRunnerService.claimLease(
        workspaceId,
        runnerRef,
        request.leaseOwner(),
        request.leaseTtlSeconds()
    ));
  }

  private static void rejectRawSecretAliases(UpsertRemoteRunnerRequest request) {
    if (request.runnerToken() != null
        || request.signatureSecret() != null
        || request.apiKey() != null
        || request.token() != null
        || request.authorization() != null
        || request.Authorization() != null) {
      throw new IllegalArgumentException("Raw remote runner secrets are not accepted");
    }
  }

  public record UpsertRemoteRunnerRequest(
      @NotBlank String displayName,
      @NotBlank String endpointUrl,
      @NotNull List<String> capabilities,
      String runnerToken,
      String signatureSecret,
      String apiKey,
      String token,
      String authorization,
      String Authorization
  ) {
  }

  public record ClaimLeaseRequest(
      @NotBlank String leaseOwner,
      int leaseTtlSeconds
  ) {
  }
}
