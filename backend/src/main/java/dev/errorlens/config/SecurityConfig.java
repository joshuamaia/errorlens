package dev.errorlens.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import dev.errorlens.application.auth.TokenService;
import dev.errorlens.infrastructure.security.BearerTokenAuthenticationFilter;
import dev.errorlens.infrastructure.security.LabIdentities;
import dev.errorlens.infrastructure.security.ProblemSecurityHandler;
import dev.errorlens.infrastructure.security.Roles;
import dev.errorlens.infrastructure.security.SecurityStepFilters;

/**
 * Segurança do laboratório: API stateless, identidades locais (BCrypt), token opaco para o
 * frontend e HTTP Basic para os endpoints de laboratório exercitados pelos cenários.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(LabIdentities identities, PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                user(identities, encoder, LabIdentities.ADMIN, Roles.ADMIN, Roles.ANALYST, Roles.OPERATOR,
                        Roles.AUDITOR),
                user(identities, encoder, LabIdentities.ANALYST, Roles.ANALYST),
                user(identities, encoder, LabIdentities.VIEWER, Roles.VIEWER),
                user(identities, encoder, LabIdentities.AUDITOR, Roles.AUDITOR),
                user(identities, encoder, LabIdentities.RUNNER, Roles.OPERATOR));
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokenService,
                                            ProblemSecurityHandler problemHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.realmName("ErrorLens Lab").authenticationEntryPoint(problemHandler))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .addFilterBefore(new SecurityStepFilters.Entry(), SecurityContextHolderFilter.class)
                .addFilterBefore(new BearerTokenAuthenticationFilter(tokenService), BasicAuthenticationFilter.class)
                .addFilterAfter(new SecurityStepFilters.Exit(), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/lab/secure/**").hasRole(Roles.AUDITOR)
                        .requestMatchers("/api/v1/lab/**").hasRole(Roles.OPERATOR)
                        .requestMatchers("/api/v1/lab-admin/**").hasRole(Roles.ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/executions/**").hasRole(Roles.ADMIN)
                        .requestMatchers("/api/v1/executions/**", "/api/v1/analyzer/**")
                        .hasAnyRole(Roles.ANALYST, Roles.ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/scenarios/**")
                        .hasAnyRole(Roles.ANALYST, Roles.ADMIN)
                        .requestMatchers("/api/v1/**").authenticated()
                        .requestMatchers("/actuator/**").hasRole(Roles.ADMIN)
                        .anyRequest().denyAll());
        return http.build();
    }

    /**
     * O frontend acessa o backend pelo proxy reverso do Nginx (mesma origem). CORS fica restrito
     * ao servidor de desenvolvimento local do Angular.
     */
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:4200"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        config.setExposedHeaders(List.of("X-Correlation-Id", "Content-Disposition"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static org.springframework.security.core.userdetails.UserDetails user(
            LabIdentities identities, PasswordEncoder encoder, String username, String... roles) {
        return User.withUsername(username)
                .password(encoder.encode(identities.passwordOf(username)))
                .roles(roles)
                .build();
    }
}
