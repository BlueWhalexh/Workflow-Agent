package com.myworkflow.agent.backend.security;

import com.myworkflow.agent.backend.config.BackendProperties;
import com.myworkflow.agent.backend.identity.BackendPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DevHeaderAuthenticationFilter extends OncePerRequestFilter {

  static final String USER_ID_HEADER = "X-Dev-User-Id";
  static final String TEAM_ID_HEADER = "X-Dev-Team-Id";
  static final String DISPLAY_NAME_HEADER = "X-Dev-Display-Name";

  private final BackendProperties properties;

  public DevHeaderAuthenticationFilter(BackendProperties properties) {
    this.properties = properties;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    BackendPrincipal principal = principalFromRequest(request);
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(principal, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
    try {
      filterChain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private BackendPrincipal principalFromRequest(HttpServletRequest request) {
    String userId = clean(request.getHeader(USER_ID_HEADER));
    String teamId = clean(request.getHeader(TEAM_ID_HEADER));
    String displayName = clean(request.getHeader(DISPLAY_NAME_HEADER));
    if (userId != null && teamId != null) {
      return new BackendPrincipal(userId, teamId, displayName == null ? userId : displayName);
    }

    BackendProperties.DevPrincipal devPrincipal = properties.devPrincipal();
    return new BackendPrincipal(
        devPrincipal.userId(),
        devPrincipal.teamId(),
        devPrincipal.displayName()
    );
  }

  private static String clean(String value) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return value.trim();
  }
}
