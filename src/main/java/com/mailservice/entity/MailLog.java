package com.mailservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entidad JPA para auditoría y persistencia de logs de emails.
 */
@Entity
@Table(name = "mail_logs", indexes = {
        @Index(name = "idx_mail_logs_status", columnList = "status"),
        @Index(name = "idx_mail_logs_created_at", columnList = "createdAt"),
        @Index(name = "idx_mail_logs_recipient", columnList = "recipient"),
        @Index(name = "idx_mail_logs_template", columnList = "templateName")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private MailStatus status = MailStatus.PENDING;

    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> variables;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime sentAt;

    private LocalDateTime lastRetryAt;

    // --- Métodos de negocio ---

    public void markAsSent() {
        this.status = MailStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.attempts++;
    }

    public void markAsFailed(String error) {
        this.status = MailStatus.FAILED;
        this.errorMessage = error;
        this.attempts++;
    }

    public void markAsRetrying() {
        this.status = MailStatus.RETRYING;
        this.lastRetryAt = LocalDateTime.now();
    }
}
