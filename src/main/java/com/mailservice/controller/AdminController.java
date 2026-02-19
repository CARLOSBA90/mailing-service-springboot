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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Controller para el panel de administración de emails.
 * Renderiza vistas Thymeleaf para monitoreo y gestión de logs.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final MailLogRepository mailLogRepository;
    private final MailService mailService;

    /**
     * Dashboard principal con estadísticas.
     */
    @GetMapping
    public String dashboard(Model model) {
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

        model.addAttribute("totalEmails", mailLogRepository.count());
        model.addAttribute("sent", mailLogRepository.countByStatus(MailStatus.SENT));
        model.addAttribute("failed", mailLogRepository.countByStatus(MailStatus.FAILED));
        model.addAttribute("pending", mailLogRepository.countByStatus(MailStatus.PENDING));
        model.addAttribute("retrying", mailLogRepository.countByStatus(MailStatus.RETRYING));
        model.addAttribute("sentToday", mailLogRepository.countSentSince(today));
        model.addAttribute("failedToday", mailLogRepository.countFailedSince(today));

        // Últimos 5 emails
        Page<MailLog> recentLogs = mailLogRepository.findAll(
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")));
        model.addAttribute("recentLogs", recentLogs.getContent());

        return "admin/dashboard";
    }

    /**
     * Lista de logs paginada con filtros por estado, destinatario y rango de
     * fechas.
     */
    @GetMapping("/logs")
    public String logs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Model model) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MailLog> logs;

        // Parsear fechas si vienen
        LocalDateTime from = parseDate(dateFrom, true);
        LocalDateTime to = parseDate(dateTo, false);

        if (from != null && to != null) {
            if (status != null && !status.isBlank()) {
                // Filtro combinado: estado + fechas
                MailStatus mailStatus = MailStatus.valueOf(status);
                logs = mailLogRepository.findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
                        mailStatus, from, to, pageRequest);
            } else {
                logs = mailLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to, pageRequest);
            }
            model.addAttribute("activeDateFrom", dateFrom);
            model.addAttribute("activeDateTo", dateTo);
            if (status != null && !status.isBlank())
                model.addAttribute("activeStatus", status);
        } else if (status != null && !status.isBlank()) {
            MailStatus mailStatus = MailStatus.valueOf(status);
            logs = mailLogRepository.findByStatusOrderByCreatedAtDesc(mailStatus, pageRequest);
            model.addAttribute("activeStatus", status);
        } else if (recipient != null && !recipient.isBlank()) {
            logs = mailLogRepository.findByRecipientContainingIgnoreCaseOrderByCreatedAtDesc(recipient, pageRequest);
            model.addAttribute("activeRecipient", recipient);
        } else {
            logs = mailLogRepository.findAll(pageRequest);
        }

        model.addAttribute("logs", logs);
        model.addAttribute("statuses", MailStatus.values());

        return "admin/logs";
    }

    /**
     * Parsea una fecha string (yyyy-MM-dd) a LocalDateTime.
     * Si isStart=true, devuelve inicio del día (00:00:00). Si false, fin del día
     * (23:59:59).
     */
    private LocalDateTime parseDate(String dateStr, boolean isStart) {
        if (dateStr == null || dateStr.isBlank())
            return null;
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
            return isStart ? date.atStartOfDay() : date.atTime(23, 59, 59);
        } catch (Exception e) {
            log.warn("Fecha inválida recibida: {}", dateStr);
            return null;
        }
    }

    /**
     * Detalle de un log específico.
     */
    @GetMapping("/logs/{id}")
    public String logDetail(@PathVariable UUID id, Model model) {
        MailLog mailLog = mailLogRepository.findById(id)
                .orElse(null);

        if (mailLog == null) {
            return "redirect:/admin/logs";
        }

        model.addAttribute("log", mailLog);
        return "admin/log-detail";
    }

    /**
     * Reintentar un email fallido (POST desde la UI).
     */
    @PostMapping("/logs/{id}/retry")
    public String retryMail(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            mailService.retryMail(id);
            redirectAttributes.addFlashAttribute("successMessage", "Reintento encolado exitosamente");
            log.info("Reintento manual desde Admin UI | logId: {}", id);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Log no encontrado");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/logs";
    }

    /**
     * Reintentar todos los emails fallidos (batch).
     */
    @PostMapping("/logs/retry-all-failed")
    public String retryAllFailed(RedirectAttributes redirectAttributes) {
        var failedLogs = mailLogRepository.findByStatusAndAttemptsLessThan(MailStatus.FAILED, 5);
        int count = 0;

        for (MailLog mailLog : failedLogs) {
            try {
                mailService.retryMail(mailLog.getId());
                count++;
            } catch (Exception e) {
                log.error("Error al reintentar email | logId: {} | Error: {}", mailLog.getId(), e.getMessage());
            }
        }

        redirectAttributes.addFlashAttribute("successMessage",
                String.format("Reintento batch iniciado: %d emails encolados", count));
        log.info("Reintento batch desde Admin UI | cantidad: {}", count);
        return "redirect:/admin/logs";
    }
}
