package com.myworkflow.agent.backend.ops;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI backendOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("My Workflow Agent Backend API")
            .version("v1")
            .description("Central backend API for multi-user, multi-workspace agent orchestration."));
  }
}
