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
        mailService.sendMail(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(MailResponse.queued());
    }
}
