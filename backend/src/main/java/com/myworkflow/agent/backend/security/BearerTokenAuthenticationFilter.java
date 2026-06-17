package com.myworkflow.agent.backend.security;

import com.myworkflow.agent.backend.identity.BackendPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final ObjectProvider<BearerTokenVerifier> verifierProvider;

  public BearerTokenAuthenticationFilter(ObjectProvider<BearerTokenVerifier> verifierProvider) {
    this.verifierProvider = verifierProvider;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    Optional<String> bearerToken = bearerToken(request);
    if (bearerToken.isPresent()) {
      BearerTokenVerifier verifier = verifierProvider.getIfAvailable();
      if (verifier != null) {
        verifier.verify(bearerToken.get()).ifPresent(BearerTokenAuthenticationFilter::setPrincipal);
      }
    }

    filterChain.doFilter(request, response);
  }

  private static void setPrincipal(BackendPrincipal principal) {
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(principal, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private static Optional<String> bearerToken(HttpServletRequest request) {
    String header = request.getHeader(AUTHORIZATION_HEADER);
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      return Optional.empty();
    }

    String token = header.substring(BEARER_PREFIX.length()).trim();
    if (token.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(token);
  }
}
