package com.myworkflow.agent.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myworkflow.agent.backend.api.ApiEnvelope;
import com.myworkflow.agent.backend.api.ApiError;
import com.myworkflow.agent.backend.config.BackendProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CookieCsrfProtectionFilter extends OncePerRequestFilter {

  static final String CSRF_COOKIE_NAME = "MWA_CSRF";
  static final String CSRF_HEADER_NAME = "X-CSRF-Token";

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final BackendProperties properties;
  private final ObjectMapper objectMapper;

  public CookieCsrfProtectionFilter(BackendProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    if (requiresCsrfToken(request) && !hasMatchingCsrfToken(request)) {
      writeCsrfFailure(response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private boolean requiresCsrfToken(HttpServletRequest request) {
    return isMutatingMethod(request.getMethod())
        && !hasBearerAuthorization(request)
        && hasCookie(request, properties.oauthIntrospection().sessionCookieName());
  }

  private static boolean isMutatingMethod(String method) {
    return switch (method.toUpperCase(Locale.ROOT)) {
      case "POST", "PUT", "PATCH", "DELETE" -> true;
      default -> false;
    };
  }

  private static boolean hasBearerAuthorization(HttpServletRequest request) {
    String header = request.getHeader(AUTHORIZATION_HEADER);
    return header != null && header.startsWith(BEARER_PREFIX) && !header.substring(BEARER_PREFIX.length()).isBlank();
  }

  private static boolean hasMatchingCsrfToken(HttpServletRequest request) {
    String headerToken = clean(request.getHeader(CSRF_HEADER_NAME));
    String cookieToken = clean(cookieValue(request, CSRF_COOKIE_NAME));
    return headerToken != null && headerToken.equals(cookieToken);
  }

  private static boolean hasCookie(HttpServletRequest request, String cookieName) {
    return clean(cookieValue(request, cookieName)) != null;
  }

  private static String cookieValue(HttpServletRequest request, String cookieName) {
    if (cookieName == null || cookieName.isBlank() || request.getCookies() == null) {
      return null;
    }
    for (Cookie cookie : request.getCookies()) {
      if (cookieName.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  private static String clean(String value) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return value.trim();
  }

  private void writeCsrfFailure(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), ApiEnvelope.failure(new ApiError(
        "CSRF_TOKEN_REQUIRED",
        "CSRF token is required for session cookie requests",
        false
    )));
  }
}
