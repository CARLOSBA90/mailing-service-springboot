package com.mailservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO de entrada para solicitudes de envío de email.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailRequest {

    @NotBlank(message = "El destinatario es obligatorio")
    @Email(message = "El email del destinatario no es válido")
    private String to;

    @NotBlank(message = "El asunto es obligatorio")
    private String subject;

    @NotBlank(message = "El template es obligatorio")
    private String template;

    private Map<String, Object> variables;
}
