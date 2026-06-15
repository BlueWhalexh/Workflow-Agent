package com.myworkflow.agent.backend.audit;

import org.junit.jupiter.api.Test;

class InMemoryAuditRepositoryContractTest {

  @Test
  void inMemoryRepositorySatisfiesAuditQueryContract() {
    AuditRepositoryContract.assertWorkspaceAuditQueryContract(new InMemoryAuditRepository());
  }
}
