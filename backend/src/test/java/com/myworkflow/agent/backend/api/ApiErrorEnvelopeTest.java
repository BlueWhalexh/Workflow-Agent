package com.myworkflow.agent.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myworkflow.agent.backend.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = BackendApplication.class)
@AutoConfigureMockMvc
@Import(ApiErrorEnvelopeTest.ThrowingController.class)
class ApiErrorEnvelopeTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void missingRouteReturnsStableEnvelope() throws Exception {
    mockMvc.perform(get("/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("Resource not found"))
        .andExpect(jsonPath("$.error.retryable").value(false))
        .andExpect(jsonPath("$.path").doesNotExist())
        .andExpect(jsonPath("$.trace").doesNotExist());
  }

  @Test
  void unsupportedMethodReturnsStableEnvelope() throws Exception {
    mockMvc.perform(post("/health"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"))
        .andExpect(jsonPath("$.error.message").value("Method not allowed"))
        .andExpect(jsonPath("$.error.retryable").value(false))
        .andExpect(jsonPath("$.path").doesNotExist())
        .andExpect(jsonPath("$.trace").doesNotExist());
  }

  @Test
  void unexpectedExceptionReturnsStableEnvelopeWithoutExceptionDetails() throws Exception {
    mockMvc.perform(get("/test/runtime-error"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.error.message").value("Internal backend error"))
        .andExpect(jsonPath("$.error.retryable").value(false))
        .andExpect(jsonPath("$.path").doesNotExist())
        .andExpect(jsonPath("$.trace").doesNotExist());
  }

  @RestController
  static class ThrowingController {

    @GetMapping("/test/runtime-error")
    String throwRuntimeError() {
      throw new IllegalStateException("secret-token-like-value should not appear");
    }
  }
}
