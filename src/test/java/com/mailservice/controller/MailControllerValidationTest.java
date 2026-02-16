package com.mailservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de validación de campos obligatorios del endpoint de mail.
 */
@SpringBootTest(properties = "mail-service.api-key=test-secure-key-12345")
@AutoConfigureMockMvc
class MailControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String SEND_ENDPOINT = "/api/mail/send";
    private static final String VALID_API_KEY = "test-secure-key-12345";

    // ── Validación de campos obligatorios → 400 ──

    @Test
    @DisplayName("Debe rechazar request sin campo 'to' con 400")
    void shouldRejectRequestWithoutTo() throws Exception {
        String json = """
                {
                    "subject": "Test",
                    "template": "welcome"
                }
                """;
        mockMvc.perform(post(SEND_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", VALID_API_KEY)
                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Debe rechazar request con email inválido con 400")
    void shouldRejectRequestWithInvalidEmail() throws Exception {
        String json = """
                {
                    "to": "no-es-un-email",
                    "subject": "Test",
                    "template": "welcome"
                }
                """;
        mockMvc.perform(post(SEND_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", VALID_API_KEY)
                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Debe rechazar request sin campo 'subject' con 400")
    void shouldRejectRequestWithoutSubject() throws Exception {
        String json = """
                {
                    "to": "test@ejemplo.com",
                    "template": "welcome"
                }
                """;
        mockMvc.perform(post(SEND_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", VALID_API_KEY)
                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Debe rechazar request sin campo 'template' con 400")
    void shouldRejectRequestWithoutTemplate() throws Exception {
        String json = """
                {
                    "to": "test@ejemplo.com",
                    "subject": "Test"
                }
                """;
        mockMvc.perform(post(SEND_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", VALID_API_KEY)
                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Debe rechazar request con body vacío con 400")
    void shouldRejectEmptyBody() throws Exception {
        mockMvc.perform(post(SEND_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", VALID_API_KEY)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
