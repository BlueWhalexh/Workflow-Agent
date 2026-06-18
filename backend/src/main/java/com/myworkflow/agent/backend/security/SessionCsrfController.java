package com.myworkflow.agent.backend.security;

import com.myworkflow.agent.backend.api.ApiEnvelope;
import com.myworkflow.agent.backend.identity.PrincipalProvider;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class SessionCsrfController {

  private static final Duration CSRF_COOKIE_MAX_AGE = Duration.ofHours(2);
  private static final int CSRF_TOKEN_BYTES = 32;

  private final PrincipalProvider principalProvider;
  private final SecureRandom secureRandom;

  public SessionCsrfController(PrincipalProvider principalProvider) {
    this.principalProvider = principalProvider;
    this.secureRandom = new SecureRandom();
  }

  @GetMapping("/session/csrf")
  public ResponseEntity<ApiEnvelope<CsrfTokenResponse>> csrfToken() {
    principalProvider.currentPrincipal();
    String token = newCsrfToken();
    ResponseCookie cookie = ResponseCookie.from(CookieCsrfProtectionFilter.CSRF_COOKIE_NAME, token)
        .path("/")
        .httpOnly(true)
        .sameSite("Lax")
        .maxAge(CSRF_COOKIE_MAX_AGE)
        .build();

    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(ApiEnvelope.ok(new CsrfTokenResponse(
            token,
            CookieCsrfProtectionFilter.CSRF_HEADER_NAME
        )));
  }

  private String newCsrfToken() {
    byte[] bytes = new byte[CSRF_TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public record CsrfTokenResponse(
      String token,
      String headerName
  ) {
  }
}
