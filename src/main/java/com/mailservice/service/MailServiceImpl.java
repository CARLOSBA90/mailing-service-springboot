package com.mailservice.service;

import com.mailservice.dto.MailRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.concurrent.CompletableFuture;

/**
 * Implementación del servicio de mailing.
 * Procesa emails de forma async con reintentos automáticos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${mail-service.from.address}")
    private String fromAddress;

    @Value("${mail-service.from.name}")
    private String fromName;

    @Override
    @Async("mailExecutor")
    @Retryable(retryFor = MessagingException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public CompletableFuture<Void> sendMail(MailRequest request) {
        try {
            log.info("Enviando email a: {} | template: {}", request.getTo(), request.getTemplate());

            String htmlContent = renderTemplate(request);
            sendHtmlMessage(request.getTo(), request.getSubject(), htmlContent);

            log.info("Email enviado exitosamente a: {}", request.getTo());
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Error al enviar email a: {} | Error: {}", request.getTo(), e.getMessage());
            throw new RuntimeException("Fallo al enviar email", e);
        }
    }

    private String renderTemplate(MailRequest request) {
        Context context = new Context();
        if (request.getVariables() != null) {
            request.getVariables().forEach(context::setVariable);
        }
        return templateEngine.process("mail/" + request.getTemplate(), context);
    }

    private void sendHtmlMessage(String to, String subject, String htmlContent)
            throws MessagingException, java.io.UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromAddress, fromName);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }
}
