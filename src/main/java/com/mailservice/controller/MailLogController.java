package com.mailservice.controller;

import com.mailservice.entity.MailLog;
import com.mailservice.entity.MailStatus;
import com.mailservice.repository.MailLogRepository;
import com.mailservice.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints REST para consulta de logs y reintentos de emails.
 */
@RestController
@RequestMapping("/api/mail/logs")
@RequiredArgsConstructor
@Slf4j
public class MailLogController {

    private final MailLogRepository mailLogRepository;
    private final MailService mailService;

    /**
     * Listar logs con paginación y filtros opcionales.
     * GET /api/mail/logs?page=0&size=20&status=FAILED&recipient=test@mail.com
     */
    @GetMapping
    public ResponseEntity<Page<MailLog>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) MailStatus status,
            @RequestParam(required = false) String recipient) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<MailLog> logs;

        if (status != null) {
            logs = mailLogRepository.findByStatusOrderByCreatedAtDesc(status, pageRequest);
        } else if (recipient != null && !recipient.isBlank()) {
            logs = mailLogRepository.findByRecipientContainingIgnoreCaseOrderByCreatedAtDesc(recipient, pageRequest);
        } else {
            logs = mailLogRepository.findAll(pageRequest);
        }

        return ResponseEntity.ok(logs);
    }

    /**
     * Obtener un log específico por ID.
     * GET /api/mail/logs/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<MailLog> getLogById(@PathVariable UUID id) {
        return mailLogRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Obtener estadísticas generales.
     * GET /api/mail/logs/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

        Map<String, Object> stats = Map.of(
                "totalEmails", mailLogRepository.count(),
                "pending", mailLogRepository.countByStatus(MailStatus.PENDING),
                "sent", mailLogRepository.countByStatus(MailStatus.SENT),
                "failed", mailLogRepository.countByStatus(MailStatus.FAILED),
                "retrying", mailLogRepository.countByStatus(MailStatus.RETRYING),
                "sentToday", mailLogRepository.countSentSince(today),
                "failedToday", mailLogRepository.countFailedSince(today));

        return ResponseEntity.ok(stats);
    }

    /**
     * Reintentar un email fallido.
     * POST /api/mail/logs/{id}/retry
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, String>> retryMail(@PathVariable UUID id) {
        try {
            mailService.retryMail(id);
            log.info("Reintento de email iniciado | logId: {}", id);
            return ResponseEntity.accepted().body(Map.of(
                    "message", "Reintento encolado exitosamente",
                    "logId", id.toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reintentar todos los emails fallidos (batch).
     * POST /api/mail/logs/retry-failed
     */
    @PostMapping("/retry-failed")
    public ResponseEntity<Map<String, Object>> retryAllFailed() {
        var failedLogs = mailLogRepository.findByStatusAndAttemptsLessThan(MailStatus.FAILED, 5);

        failedLogs.forEach(mailLog -> {
            try {
                mailService.retryMail(mailLog.getId());
            } catch (Exception e) {
                log.error("Error al reintentar email | logId: {} | Error: {}", mailLog.getId(), e.getMessage());
            }
        });

        log.info("Reintento batch iniciado | cantidad: {}", failedLogs.size());
        return ResponseEntity.accepted().body(Map.of(
                "message", "Reintento batch encolado",
                "count", failedLogs.size()));
    }
}
