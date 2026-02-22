package com.mailservice.service;

import com.mailservice.dto.MailRequest;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Contrato para el servicio de envío de emails.
 */
public interface MailService {

    /**
     * Valida de forma síncrona si el envío está permitido en este momento.
     * Debe llamarse en el controller ANTES de encolar el email.
     *
     * @throws IllegalStateException si el servicio está deshabilitado o el límite
     *                               diario fue alcanzado.
     */
    void validateBeforeSend(MailRequest request);

    /**
     * Envía un email de forma async usando el template y variables indicados.
     * Asume que ya se llamó a {@link #validateBeforeSend} previamente.
     */
    CompletableFuture<Void> sendMail(MailRequest request);

    /**
     * Reintenta el envío de un email previamente fallido.
     */
    CompletableFuture<Void> retryMail(UUID mailLogId);
}
