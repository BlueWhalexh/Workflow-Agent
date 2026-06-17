package com.myworkflow.agent.backend.security;

import com.myworkflow.agent.backend.identity.BackendPrincipal;
import java.util.Optional;

public interface BearerTokenVerifier {

  Optional<BackendPrincipal> verify(String token);
}
