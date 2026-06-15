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
class OpenApiContractTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void openApiDocumentIncludesOpsEndpoints() throws Exception {
    mockMvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("My Workflow Agent Backend API"))
        .andExpect(jsonPath("$.info.version").value("v1"))
        .andExpect(jsonPath("$.paths['/health']").exists())
        .andExpect(jsonPath("$.paths['/ready']").exists())
        .andExpect(jsonPath("$.paths['/health'].get.responses['200'].content['application/json'].schema.$ref")
            .value("#/components/schemas/ApiEnvelopeOpsStatusResponse"))
        .andExpect(jsonPath("$.paths['/ready'].get.responses['200'].content['application/json'].schema.$ref")
            .value("#/components/schemas/ApiEnvelopeOpsStatusResponse"))
        .andExpect(jsonPath("$.components.schemas.ApiEnvelopeOpsStatusResponse.properties.schemaVersion").exists())
        .andExpect(jsonPath("$.components.schemas.ApiEnvelopeOpsStatusResponse.properties.ok").exists())
        .andExpect(jsonPath("$.components.schemas.ApiEnvelopeOpsStatusResponse.properties.data").exists())
        .andExpect(jsonPath("$.components.schemas.ApiEnvelopeOpsStatusResponse.properties.error").exists());
  }
}
