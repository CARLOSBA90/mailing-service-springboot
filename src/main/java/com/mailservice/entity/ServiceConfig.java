package com.mailservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidad de configuración dinámica del servicio.
 * Permite modificar parámetros del sistema sin reiniciar la aplicación.
 */
@Entity
@Table(name = "service_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceConfig {

    @Id
    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    @Column(name = "config_value", nullable = false, length = 1000)
    private String configValue;

    @Column(nullable = false, length = 255)
    private String description;

    /**
     * Tipo de dato: INTEGER, BOOLEAN, STRING
     */
    @Column(name = "config_type", nullable = false, length = 20)
    private String configType;

    @Builder.Default
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
