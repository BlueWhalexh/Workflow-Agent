package com.myworkflow.agent.backend.security;

import com.myworkflow.agent.backend.identity.BackendPrincipal;
import com.myworkflow.agent.backend.config.BackendProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
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
  private final BackendProperties properties;

  public BearerTokenAuthenticationFilter(
      ObjectProvider<BearerTokenVerifier> verifierProvider,
      BackendProperties properties
  ) {
    this.verifierProvider = verifierProvider;
    this.properties = properties;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    Optional<String> bearerToken = bearerToken(request)
        .or(() -> sessionCookieToken(request, properties.oauthIntrospection().sessionCookieName()));
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

  private static Optional<String> sessionCookieToken(HttpServletRequest request, String cookieName) {
    if (cookieName == null || cookieName.isBlank() || request.getCookies() == null) {
      return Optional.empty();
    }
    for (Cookie cookie : request.getCookies()) {
      if (cookieName.equals(cookie.getName())) {
        String token = cookie.getValue() == null ? "" : cookie.getValue().trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
      }
    }
    return Optional.empty();
  }
}
