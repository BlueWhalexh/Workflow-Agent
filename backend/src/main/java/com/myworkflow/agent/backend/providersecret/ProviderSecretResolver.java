package com.myworkflow.agent.backend.providersecret;

import java.util.Optional;

public interface ProviderSecretResolver {

  Optional<String> resolveSecretValue(String secretRef);
}
