package com.myworkflow.agent.backend.ops;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myworkflow.agent.backend.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = BackendApplication.class)
@AutoConfigureMockMvc
class OpsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void healthReturnsStableEnvelope() throws Exception {
    mockMvc.perform(get("/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.status").value("ok"))
        .andExpect(jsonPath("$.data.service").value("my-workflow-agent-backend"))
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  @Test
  void readyReturnsStableEnvelope() throws Exception {
    mockMvc.perform(get("/ready"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.status").value("ready"))
        .andExpect(jsonPath("$.data.service").value("my-workflow-agent-backend"))
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  @Test
  void actuatorHealthIsNotPublicApiSurface() throws Exception {
    mockMvc.perform(get("/actuator/health"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }
}
