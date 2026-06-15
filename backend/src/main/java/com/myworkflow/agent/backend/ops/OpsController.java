package com.myworkflow.agent.backend.ops;

import com.myworkflow.agent.backend.api.ApiEnvelope;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpsController {

  private static final String SERVICE_NAME = "my-workflow-agent-backend";

  @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
  public ApiEnvelope<OpsStatusResponse> health() {
    return ApiEnvelope.ok(new OpsStatusResponse("ok", SERVICE_NAME));
  }

  @GetMapping(value = "/ready", produces = MediaType.APPLICATION_JSON_VALUE)
  public ApiEnvelope<OpsStatusResponse> ready() {
    return ApiEnvelope.ok(new OpsStatusResponse("ready", SERVICE_NAME));
  }

  public record OpsStatusResponse(
      String status,
      String service
  ) {
  }
}
