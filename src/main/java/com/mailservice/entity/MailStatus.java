package com.mailservice.entity;

/**
 * Estados posibles de un email en el sistema.
 */
public enum MailStatus {
    PENDING,
    SENT,
    FAILED,
    RETRYING
}
