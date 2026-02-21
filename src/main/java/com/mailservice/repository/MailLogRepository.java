package com.mailservice.repository;

import com.mailservice.entity.MailLog;
import com.mailservice.entity.MailStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repositorio de auditoría de emails.
 * Provee queries personalizadas para consultas y reintentos.
 */
@Repository
public interface MailLogRepository extends JpaRepository<MailLog, UUID> {

    // Buscar por estado con paginación
    Page<MailLog> findByStatusOrderByCreatedAtDesc(MailStatus status, Pageable pageable);

    // Buscar por destinatario
    Page<MailLog> findByRecipientContainingIgnoreCaseOrderByCreatedAtDesc(String recipient, Pageable pageable);

    // Buscar todos los fallidos (para reintento batch)
    List<MailLog> findByStatusAndAttemptsLessThan(MailStatus status, int maxAttempts);

    // Buscar por rango de fechas
    Page<MailLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime from, LocalDateTime to, Pageable pageable);

    // Buscar por estado + rango de fechas (filtro combinado)
    Page<MailLog> findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
            MailStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable);

    // Contar por estado (para dashboard)
    long countByStatus(MailStatus status);

    // Estadísticas: Total enviados hoy
    @Query("SELECT COUNT(m) FROM MailLog m WHERE m.status = 'SENT' AND m.createdAt >= :since")
    long countSentSince(LocalDateTime since);

    // Estadísticas: Total fallidos hoy
    @Query("SELECT COUNT(m) FROM MailLog m WHERE m.status = 'FAILED' AND m.createdAt >= :since")
    long countFailedSince(LocalDateTime since);
}
