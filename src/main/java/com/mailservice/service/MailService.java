package com.mailservice.service;

import com.mailservice.dto.MailRequest;

import java.util.concurrent.CompletableFuture;

/**
 * Contrato para el servicio de envío de emails.
 */
public interface MailService {

    /**
     * Envía un email de forma async usando el template y variables indicados.
     */
    CompletableFuture<Void> sendMail(MailRequest request);
}
