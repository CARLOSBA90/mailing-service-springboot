package com.mailservice.controller;

import com.mailservice.dto.MailRequest;
import com.mailservice.dto.MailResponse;
import com.mailservice.service.MailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint REST para envío de emails.
 */
@RestController
@RequestMapping("/api/mail")
@RequiredArgsConstructor
@Slf4j
public class MailController {

    private final MailService mailService;

    @PostMapping("/send")
    public ResponseEntity<MailResponse> sendMail(@Valid @RequestBody MailRequest request) {
        log.info("Request recibido - To: {} | Template: {}", request.getTo(), request.getTemplate());

        // async
        try {
            mailService.validateBeforeSend(request);
        } catch (IllegalArgumentException e) {
            // Template no está en la whitelist → 400 Bad Request
            log.warn("Envío rechazado: template inválido | Razón: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(MailResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            // Servicio deshabilitado o límite diario → 409 Conflict
            log.warn("Envío rechazado antes de encolar | Razón: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(MailResponse.error(e.getMessage()));
        }

        mailService.sendMail(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(MailResponse.queued());
    }
}
