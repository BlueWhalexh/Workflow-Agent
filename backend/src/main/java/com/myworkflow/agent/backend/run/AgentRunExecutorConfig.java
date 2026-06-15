package com.myworkflow.agent.backend.run;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentRunExecutorConfig {

  @Bean(destroyMethod = "shutdown")
  public ExecutorService agentRunExecutorService() {
    return Executors.newCachedThreadPool();
  }
}
