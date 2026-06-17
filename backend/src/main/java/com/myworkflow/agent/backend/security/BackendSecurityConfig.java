package com.myworkflow.agent.backend.security;

import com.myworkflow.agent.backend.config.BackendProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class BackendSecurityConfig {

  @Bean
  SecurityFilterChain backendSecurityFilterChain(
      HttpSecurity http,
      BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter,
      DevHeaderAuthenticationFilter devHeaderAuthenticationFilter,
      CorsConfigurationSource backendCorsConfigurationSource
  ) throws Exception {
    return http
        .cors((cors) -> cors.configurationSource(backendCorsConfigurationSource))
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests((requests) -> requests.anyRequest().permitAll())
        .addFilterBefore(bearerTokenAuthenticationFilter, AnonymousAuthenticationFilter.class)
        .addFilterBefore(devHeaderAuthenticationFilter, AnonymousAuthenticationFilter.class)
        .build();
  }

  @Bean
  CorsConfigurationSource backendCorsConfigurationSource(BackendProperties properties) {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    if (properties.cors().enabled()) {
      CorsConfiguration configuration = new CorsConfiguration();
      configuration.setAllowedOrigins(properties.cors().allowedOrigins());
      configuration.setAllowCredentials(true);
      configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
      configuration.setAllowedHeaders(java.util.List.of(
          "Authorization",
          "Content-Type",
          "Accept",
          "Last-Event-ID",
          "X-Dev-User-Id",
          "X-Dev-Team-Id",
          "X-Dev-Display-Name"
      ));
      configuration.setMaxAge(3600L);
      source.registerCorsConfiguration("/**", configuration);
    }
    return source;
  }

  @Bean
  UserDetailsService backendUserDetailsService() {
    return (username) -> {
      throw new UsernameNotFoundException(username);
    };
  }
}
