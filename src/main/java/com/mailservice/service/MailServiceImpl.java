package com.mailservice.service;

import com.mailservice.dto.MailRequest;
import com.mailservice.entity.MailLog;
import com.mailservice.entity.MailStatus;
import com.mailservice.repository.MailLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementación del servicio de mailing.
 * Procesa emails de forma async con reintentos automáticos y persistencia de
 * logs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailServiceImpl implements MailService {

    private static final Set<String> ALLOWED_TEMPLATES = Set.of(
            "welcome", "password-reset", "order-confirmation");

    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MailLogRepository mailLogRepository;

    @Value("${mail-service.from.address}")
    private String fromAddress;

    @Value("${mail-service.from.name}")
    private String fromName;

    @Override
    @Async("mailExecutor")
    public CompletableFuture<Void> sendMail(MailRequest request) {
        MailLog mailLog = createMailLog(request);

        try {
            log.info("Enviando email a: {} | template: {}", request.getTo(), request.getTemplate());

            String htmlContent = renderTemplate(request);
            sendWithRetry(request.getTo(), request.getSubject(), htmlContent, mailLog);

            mailLog.markAsSent();
            mailLogRepository.save(mailLog);
            log.info("Email enviado exitosamente a: {} | logId: {}", request.getTo(), mailLog.getId());

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            mailLog.markAsFailed(e.getMessage());
            mailLogRepository.save(mailLog);
            log.error("Error definitivo al enviar email a: {} | logId: {} | Error: {}",
                    request.getTo(), mailLog.getId(), e.getMessage());
            throw new RuntimeException("Fallo al enviar email", e);
        }
    }

    /**
     * Reintenta el envío de un email previamente fallido.
     */
    public CompletableFuture<Void> retryMail(UUID mailLogId) {
        MailLog mailLog = mailLogRepository.findById(mailLogId)
                .orElseThrow(() -> new IllegalArgumentException("MailLog no encontrado: " + mailLogId));

        if (mailLog.getStatus() != MailStatus.FAILED) {
            throw new IllegalStateException("Solo se pueden reintentar emails con estado FAILED");
        }

        mailLog.markAsRetrying();
        mailLogRepository.save(mailLog);

        MailRequest request = MailRequest.builder()
                .to(mailLog.getRecipient())
                .subject(mailLog.getSubject())
                .template(mailLog.getTemplateName())
                .variables(mailLog.getVariables())
                .build();

        return sendMail(request);
    }

    // --- Métodos privados ---

    private MailLog createMailLog(MailRequest request) {
        MailLog mailLog = MailLog.builder()
                .recipient(request.getTo())
                .subject(request.getSubject())
                .templateName(request.getTemplate())
                .variables(request.getVariables())
                .status(MailStatus.PENDING)
                .build();

        return mailLogRepository.save(mailLog);
    }

    private void sendWithRetry(String to, String subject, String htmlContent, MailLog mailLog) {
        int attempt = 0;
        long delayMs = 2000;

        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                attempt++;
                sendHtmlMessage(to, subject, htmlContent);
                return; // Éxito
            } catch (Exception e) {
                log.warn("Intento {}/{} fallido para: {} | Error: {}", attempt, MAX_RETRY_ATTEMPTS, to, e.getMessage());

                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    throw new RuntimeException("Máximo de reintentos alcanzado", e);
                }

                try {
                    Thread.sleep(delayMs);
                    delayMs *= 2; // Backoff exponencial
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Reintento interrumpido", ie);
                }
            }
        }
    }

    private String renderTemplate(MailRequest request) {
        String template = request.getTemplate();

        if (!ALLOWED_TEMPLATES.contains(template)) {
            log.warn("Intento de uso de template no permitido: {}", template);
            throw new IllegalArgumentException("Template no permitido: " + template);
        }

        Context context = new Context();
        if (request.getVariables() != null) {
            request.getVariables().forEach(context::setVariable);
        }
        return templateEngine.process("mail/" + template, context);
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
