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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Implementación del servicio de mailing.
 * Procesa emails de forma async con reintentos automáticos y persistencia de
 * logs.
 * Las configuraciones operativas se leen dinámicamente desde ConfigService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MailLogRepository mailLogRepository;
    private final ConfigService configService;

    @Value("${mail-service.from.address}")
    private String fromAddress;

    @Value("${mail-service.from.name}")
    private String fromName;

    /**
     * Validación síncrona que el controller debe llamar ANTES de encolar.
     * Valida: servicio habilitado, límite diario y whitelist de templates.
     *
     * @throws IllegalStateException    si el servicio está deshabilitado o el
     *                                  límite diario se alcanzó.
     * @throws IllegalArgumentException si el template no está en la whitelist.
     */
    @Override
    public void validateBeforeSend(MailRequest request) {
        // Guardia 1: servicio habilitado
        if (!configService.isServiceEnabled()) {
            log.warn("Envío rechazado: servicio deshabilitado | destinatario: {}", request.getTo());
            throw new IllegalStateException("El servicio de envío está temporalmente deshabilitado.");
        }

        // Guardia 2: límite diario
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long sentToday = mailLogRepository.countSentSince(startOfDay);
        int dailyLimit = configService.getDailySendLimit();

        if (sentToday >= dailyLimit) {
            log.warn("Envío rechazado: límite diario alcanzado ({}/{}) | destinatario: {}",
                    sentToday, dailyLimit, request.getTo());
            throw new IllegalStateException(
                    String.format("Límite diario de envíos alcanzado."));
        }

        // Guardia 3: template en whitelist
        Set<String> allowed = resolveAllowedTemplates();
        if (!allowed.contains(request.getTemplate())) {
            log.warn("Envío rechazado: template no permitido '{}' | destinatario: {}",
                    request.getTemplate(), request.getTo());
            throw new IllegalArgumentException(
                    "Template no permitido: '" + request.getTemplate() + "'");
        }
    }

    @Override
    @Async("mailExecutor")
    public CompletableFuture<Void> sendMail(MailRequest request) {
        // Las guardias de negocio ya fueron validadas de forma síncrona
        // en el controller antes de llegar aquí.
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
        if (!configService.isServiceEnabled()) {
            throw new IllegalStateException("El servicio de envío está temporalmente deshabilitado.");
        }

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
        int maxAttempts = configService.getMaxRetryAttempts();
        long delayMs = configService.getRetryCooldownMs();
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                attempt++;
                sendHtmlMessage(to, subject, htmlContent);
                return; // Éxito
            } catch (Exception e) {
                log.warn("Intento {}/{} fallido para: {} | Error: {}", attempt, maxAttempts, to, e.getMessage());

                if (attempt >= maxAttempts) {
                    throw new RuntimeException("Máximo de reintentos alcanzado", e);
                }

                try {
                    mailLog.markAsRetrying();
                    mailLogRepository.save(mailLog);
                    Thread.sleep(delayMs);
                    delayMs *= 2; // Backoff exponencial
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Reintento interrumpido", ie);
                }
            }
        }
    }

    /**
     * Parsea la whitelist de templates desde ConfigService.
     * Reutilizado tanto en la validación síncrona como en el render.
     */
    private Set<String> resolveAllowedTemplates() {
        return Arrays.stream(configService.getAllowedTemplates().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private String renderTemplate(MailRequest request) {
        // La validación del template ya fue hecha en validateBeforeSend().
        // Aquí solo se renderiza.
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
