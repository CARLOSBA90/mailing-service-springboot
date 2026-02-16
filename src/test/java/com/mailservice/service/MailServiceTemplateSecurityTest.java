package com.mailservice.service;

import com.mailservice.dto.MailRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de seguridad para la validación de templates.
 * Verifica que el path traversal y templates no permitidos sean rechazados.
 */
@SpringBootTest(properties = "mail-service.api-key=test-secure-key-12345")
class MailServiceTemplateSecurityTest {

    @Autowired
    private MailServiceImpl mailService;

    // ── Path Traversal → error controlado ──

    @Test
    @DisplayName("Debe rechazar template con path traversal (../)")
    void shouldRejectPathTraversalTemplate() {
        MailRequest request = MailRequest.builder()
                .to("test@ejemplo.com")
                .subject("Test")
                .template("../../application")
                .variables(Map.of("nombre", "Test"))
                .build();

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> mailService.sendMail(request).join());

        assertRootCauseIsIllegalArgument(exception, "Template no permitido");
    }

    @Test
    @DisplayName("Debe rechazar template con barras invertidas")
    void shouldRejectBackslashTraversalTemplate() {
        MailRequest request = MailRequest.builder()
                .to("test@ejemplo.com")
                .subject("Test")
                .template("..\\..\\application")
                .build();

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> mailService.sendMail(request).join());

        assertRootCauseIsIllegalArgument(exception, "Template no permitido");
    }

    // ── Template no existente → error controlado ──

    @Test
    @DisplayName("Debe rechazar template que no está en la whitelist")
    void shouldRejectUnknownTemplate() {
        MailRequest request = MailRequest.builder()
                .to("test@ejemplo.com")
                .subject("Test")
                .template("template-inventado")
                .build();

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> mailService.sendMail(request).join());

        assertRootCauseIsIllegalArgument(exception, "Template no permitido");
    }

    // ── Templates válidos → sin excepción de whitelist ──

    @Test
    @DisplayName("Debe aceptar template 'welcome' (está en whitelist)")
    void shouldAcceptWelcomeTemplate() {
        MailRequest request = MailRequest.builder()
                .to("test@ejemplo.com")
                .subject("Test")
                .template("welcome")
                .variables(Map.of("nombre", "Carlos"))
                .build();

        assertDoesNotThrowIllegalArgument(request);
    }

    @Test
    @DisplayName("Debe aceptar template 'password-reset' (está en whitelist)")
    void shouldAcceptPasswordResetTemplate() {
        MailRequest request = MailRequest.builder()
                .to("test@ejemplo.com")
                .subject("Test")
                .template("password-reset")
                .build();

        assertDoesNotThrowIllegalArgument(request);
    }

    @Test
    @DisplayName("Debe aceptar template 'order-confirmation' (está en whitelist)")
    void shouldAcceptOrderConfirmationTemplate() {
        MailRequest request = MailRequest.builder()
                .to("test@ejemplo.com")
                .subject("Test")
                .template("order-confirmation")
                .build();

        assertDoesNotThrowIllegalArgument(request);
    }

    /**
     * Verifica que la causa raíz de la CompletionException sea
     * IllegalArgumentException
     * con el mensaje esperado. La cadena es: CompletionException → RuntimeException
     * → IllegalArgumentException.
     */
    private void assertRootCauseIsIllegalArgument(CompletionException exception, String expectedMessage) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        assertInstanceOf(IllegalArgumentException.class, rootCause);
        assertTrue(rootCause.getMessage().contains(expectedMessage),
                "Se esperaba mensaje con '" + expectedMessage + "' pero fue: " + rootCause.getMessage());
    }

    /**
     * Verifica que un template válido no lance IllegalArgumentException.
     * Puede fallar por SMTP (esperado en test), pero NO por whitelist.
     */
    private void assertDoesNotThrowIllegalArgument(MailRequest request) {
        try {
            mailService.sendMail(request).join();
        } catch (Exception e) {
            Throwable rootCause = e;
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            assertFalse(rootCause instanceof IllegalArgumentException,
                    "No debería rechazar un template de la whitelist, pero lanzó: " + rootCause.getMessage());
        }
    }
}
