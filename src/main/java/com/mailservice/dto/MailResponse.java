package com.mailservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para confirmación de envío de email.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailResponse {

    private boolean success;
    private String message;

    public static MailResponse queued() {
        return MailResponse.builder()
                .success(true)
                .message("Email encolado para envío")
                .build();
    }

    public static MailResponse error(String errorMessage) {
        return MailResponse.builder()
                .success(false)
                .message(errorMessage)
                .build();
    }
}
