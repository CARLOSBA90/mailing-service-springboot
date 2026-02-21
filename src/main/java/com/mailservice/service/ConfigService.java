package com.mailservice.service;

import com.mailservice.entity.ServiceConfig;

import java.util.List;

/**
 * Servicio para gestionar la configuración dinámica del sistema.
 * Los valores se persisten en base de datos y se cachean en memoria.
 */
public interface ConfigService {

    /** Límite máximo de emails permitidos por día (00:00 a 23:59). */
    int getDailySendLimit();

    /** Indica si el servicio de envío está habilitado. */
    boolean isServiceEnabled();

    /** Máximo de reintentos automáticos por email fallido. */
    int getMaxRetryAttempts();

    /** Delay inicial en ms entre reintentos (aplica backoff exponencial). */
    long getRetryCooldownMs();

    /** Lista de templates permitidos separados por coma. */
    String getAllowedTemplates();

    /** Obtiene un valor string con fallback al valor por defecto. */
    String getString(String key, String defaultValue);

    /** Obtiene un valor entero con fallback al valor por defecto. */
    int getInt(String key, int defaultValue);

    /** Obtiene un valor booleano con fallback al valor por defecto. */
    boolean getBoolean(String key, boolean defaultValue);

    /** Actualiza el valor de una clave existente. */
    void updateConfig(String key, String value);

    /** Retorna todas las configuraciones del sistema. */
    List<ServiceConfig> getAllConfigs();
}
