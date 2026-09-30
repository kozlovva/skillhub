package com.skillhub.adapters.in.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiTokenAuthFilter apiTokenAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        BearerTokenResolver bearerTokenResolver = request -> {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer skh_")) {
                return null;
            }
            return defaultResolver.resolve(request);
        };
        http
            .addFilterBefore(apiTokenAuthFilter, AbstractPreAuthenticatedProcessingFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .oauth2ResourceServer(oauth -> oauth
                .bearerTokenResolver(bearerTokenResolver)
                .jwt(jwt -> {}))
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
