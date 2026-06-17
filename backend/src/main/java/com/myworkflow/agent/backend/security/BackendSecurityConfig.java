package com.myworkflow.agent.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class BackendSecurityConfig {

  @Bean
  SecurityFilterChain backendSecurityFilterChain(
      HttpSecurity http,
      BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter,
      DevHeaderAuthenticationFilter devHeaderAuthenticationFilter
  ) throws Exception {
    return http
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
  UserDetailsService backendUserDetailsService() {
    return (username) -> {
      throw new UsernameNotFoundException(username);
    };
  }
}
