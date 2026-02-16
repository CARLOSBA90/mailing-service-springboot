package com.mailservice.config;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Seguridad basada en API Key para comunicación inter-servicios.
 * Incluye: constant-time comparison, rate limiting, logging de auth fallida,
 * security headers, y validación de API Key por perfil.
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    private static final String DEV_API_KEY = "dev-api-key-change-me";

    @Value("${mail-service.api-key}")
    private String apiKey;

    private final Environment environment;

    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateApiKey() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        if (isProd && (apiKey == null || apiKey.isBlank() || DEV_API_KEY.equals(apiKey))) {
            throw new IllegalStateException(
                    "API_KEY no configurada o usando valor por defecto. "
                            + "En perfil 'prod' es obligatorio configurar la variable de entorno API_KEY.");
        }

        if (!isProd && DEV_API_KEY.equals(apiKey)) {
            log.warn("⚠ Usando API Key de desarrollo. NO usar en producción.");
        }
    }

    private static final int MAX_REQUESTS_PER_MINUTE = 30;
    private final Map<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .cacheControl(Customizer.withDefaults()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(apiKeyFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public OncePerRequestFilter apiKeyFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain) throws ServletException, IOException {

                String path = request.getRequestURI();

                if (path.startsWith("/actuator")) {
                    filterChain.doFilter(request, response);
                    return;
                }

                String clientIp = request.getRemoteAddr();

                // Rate limiting por IP
                if (isRateLimited(clientIp)) {
                    log.warn("Rate limit excedido para IP: {} | Path: {}", clientIp, path);
                    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Demasiadas solicitudes. Intente más tarde.\"}");
                    return;
                }

                String requestApiKey = request.getHeader("X-API-Key");

                // Comparación constant-time para prevenir timing attacks
                if (requestApiKey != null && MessageDigest.isEqual(
                        apiKey.getBytes(StandardCharsets.UTF_8),
                        requestApiKey.getBytes(StandardCharsets.UTF_8))) {

                    var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            "api-client", null, java.util.Collections.emptyList());
                    org.springframework.security.core.context.SecurityContextHolder.getContext()
                            .setAuthentication(auth);
                    filterChain.doFilter(request, response);
                } else {
                    log.warn("Intento de acceso con API Key inválida desde IP: {} | Path: {}", clientIp, path);
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"API Key inválida o ausente\"}");
                }
            }
        };
    }

    /**
     * Rate limiting simple por IP usando ventana de tiempo fija (1 minuto).
     * Limpia entradas expiradas en cada verificación para evitar memory leaks.
     */
    private boolean isRateLimited(String clientIp) {
        long now = System.currentTimeMillis();

        // Limpiar entradas expiradas periódicamente
        rateLimitMap.entrySet().removeIf(entry -> now - entry.getValue().windowStart > 60_000);

        RateLimitEntry entry = rateLimitMap.compute(clientIp, (key, existing) -> {
            if (existing == null || now - existing.windowStart > 60_000) {
                return new RateLimitEntry(now);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        return entry.count.get() > MAX_REQUESTS_PER_MINUTE;
    }

    private static class RateLimitEntry {
        final long windowStart;
        final AtomicInteger count;

        RateLimitEntry(long windowStart) {
            this.windowStart = windowStart;
            this.count = new AtomicInteger(1);
        }
    }
}
