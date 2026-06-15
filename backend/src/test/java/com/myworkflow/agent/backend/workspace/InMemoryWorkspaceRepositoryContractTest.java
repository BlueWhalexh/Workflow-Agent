package com.myworkflow.agent.backend.workspace;

import org.junit.jupiter.api.Test;

class InMemoryWorkspaceRepositoryContractTest {

  @Test
  void inMemoryRepositorySatisfiesWorkspaceRepositoryContract() {
    WorkspaceRepositoryContract.assertRepositoryContract(new InMemoryWorkspaceRepository());
  }
}
