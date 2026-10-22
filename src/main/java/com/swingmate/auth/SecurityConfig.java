package com.swingmate.auth;

import static com.swingmate.auth.JwtAuthenticationFilter.ACCESS_AUTHORITY;
import static com.swingmate.auth.JwtAuthenticationFilter.ONBOARDING_AUTHORITY;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swingmate.common.ErrorCode;
import com.swingmate.common.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                            ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/sessions/**", "/api/v1/auth/sessions").hasAuthority(ACCESS_AUTHORITY)
                        .requestMatchers("/api/v1/auth/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/api/v1/onboarding/**", "/api/v1/phone-verifications/**")
                        .hasAuthority(ONBOARDING_AUTHORITY)
                        .requestMatchers("/api/v1/users/me").hasAnyAuthority(ACCESS_AUTHORITY, ONBOARDING_AUTHORITY)
                        .anyRequest().hasAuthority(ACCESS_AUTHORITY))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, objectMapper, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, objectMapper, ErrorCode.FORBIDDEN)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode code)
            throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code));
    }
}
