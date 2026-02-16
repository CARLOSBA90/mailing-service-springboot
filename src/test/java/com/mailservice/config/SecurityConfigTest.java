package com.mailservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de seguridad para el filtro de API Key.
 * Valida autenticación, rechazo y security headers.
 */
@SpringBootTest(properties = "mail-service.api-key=test-secure-key-12345")
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String SEND_ENDPOINT = "/api/mail/send";
    private static final String VALID_API_KEY = "test-secure-key-12345";

    // ── Rechazo sin API Key → 401 ──

    @Test
    @DisplayName("Debe rechazar request sin header X-API-Key con 401")
    void shouldRejectRequestWithoutApiKey() throws Exception {
        mockMvc.perform(post(SEND_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validMailRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("API Key inválida o ausente"));
    }

    // ── Rechazo con API Key incorrecta → 401 ──

    @Test
    @DisplayName("Debe rechazar request con API Key incorrecta con 401")
    void shouldRejectRequestWithInvalidApiKey() throws Exception {
        mockMvc.perform(post(SEND_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "clave-incorrecta")
                .content(validMailRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("API Key inválida o ausente"));
    }

    // ── Aceptación con API Key correcta → 202 ──

    @Test
    @DisplayName("Debe aceptar request con API Key válida con 202")
    void shouldAcceptRequestWithValidApiKey() throws Exception {
        mockMvc.perform(post(SEND_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", VALID_API_KEY)
                .content(validMailRequestJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── Security Headers presentes ──

    @Test
    @DisplayName("Debe incluir security headers en la respuesta")
    void shouldIncludeSecurityHeaders() throws Exception {
        mockMvc.perform(post(SEND_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", VALID_API_KEY)
                .content(validMailRequestJson()))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"));
    }

    // ── Actuator health es público ──

    @Test
    @DisplayName("Actuator health debe ser accesible sin API Key")
    void shouldAllowActuatorHealthWithoutApiKey() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/actuator/health"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // 200 = UP, 503 = DOWN (no hay mail server en test). Ambos son válidos.
                    // Lo importante es que NO sea 401/403 (no requiere API Key).
                    assertTrue(status == 200 || status == 503,
                            "Se esperaba 200 o 503, pero fue: " + status);
                });
    }

    private String validMailRequestJson() {
        return """
                {
                    "to": "test@ejemplo.com",
                    "subject": "Test Subject",
                    "template": "welcome",
                    "variables": { "nombre": "Carlos" }
                }
                """;
    }
}
