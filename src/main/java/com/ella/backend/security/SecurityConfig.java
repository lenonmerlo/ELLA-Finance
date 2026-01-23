package com.ella.backend.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint((request, response, authException) -> {
                                                        if (!response.isCommitted()) {
                                                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                                                response.setContentType("application/json");
                                                                response.getWriter().write("{\"error\":\"Unauthorized\"}");
                                                        }
                                                })
                                                .accessDeniedHandler((request, response, accessDeniedException) -> {
                                                        if (!response.isCommitted()) {
                                                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                                                response.setContentType("application/json");
                                                                response.getWriter().write("{\"error\":\"Forbidden\"}");
                                                        }
                                                })
                                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // Pré-flight CORS (browser)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 🔓 Rotas do Swagger / OpenAPI
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).permitAll()

                        // 🔓 Rotas públicas
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/users/health",
                                "/api/persons/health"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()

                        // 🔒 Rotas ADMIN (privado)
                        // Listagem de usuários é somente ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")

                        // Usuário autenticado pode acessar /api/users/{id};
                        // a regra fina (ADMIN ou self) é aplicada no UserController.
                        .requestMatchers("/api/users/**").authenticated()

                        // 🔒 persons + financeiro: qualquer autenticação,
                        // regras finas com @PreAuthorize nos controllers
                        .requestMatchers("/api/persons/**").authenticated()
                        .requestMatchers("/api/finance/**").authenticated()

                        // 🔒 qualquer outra rota exige login
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // AuthenticationManager
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    // Encoder de senha
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Configuração de CORS
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allowed origins (produção + dev)
        configuration.setAllowedOrigins(List.of(
                "https://ellafinance.com.br",
                "https://www.ellafinance.com.br",
                "https://ellafinance.vercel.app",
                "http://localhost:5173",
                "http://127.0.0.1:5173"
        ));

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With"
        ));

        // JWT via Authorization header (sem cookies) -> não precisa Allow-Credentials.
        configuration.setAllowCredentials(false);

        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }


}
