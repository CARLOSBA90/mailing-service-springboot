package com.mailservice.service;

import com.mailservice.entity.ServiceConfig;
import com.mailservice.repository.ServiceConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación del servicio de configuración dinámica.
 * Los valores se persisten en DB y se cachean en memoria para máximo
 * rendimiento.
 * La caché se invalida automáticamente al actualizar cualquier valor.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigServiceImpl implements ConfigService {

    // Claves de configuración disponibles
    public static final String KEY_DAILY_LIMIT = "daily_send_limit";
    public static final String KEY_SERVICE_ENABLED = "service_enabled";
    public static final String KEY_MAX_RETRIES = "max_retry_attempts";
    public static final String KEY_RETRY_COOLDOWN = "retry_cooldown_ms";
    public static final String KEY_ALLOWED_TEMPLATES = "allowed_templates";
    public static final String KEY_PURGE_RETENTION_MONTHS = "purge_retention_months";
    public static final String KEY_PURGE_CRON = "purge_cron";

    private final ServiceConfigRepository configRepository;

    /** Caché en memoria para evitar consultas a DB en cada envío. */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Inicializa los valores por defecto si no existen en la base de datos.
     * Se ejecuta una sola vez al arranque, después de que Hibernate crea las
     * tablas.
     */
    @PostConstruct
    @Transactional
    void initDefaults() {
        initIfAbsent(KEY_DAILY_LIMIT, "300", "INTEGER", "Límite máximo de emails por día (00:00 a 23:59)");
        initIfAbsent(KEY_SERVICE_ENABLED, "true", "BOOLEAN", "Habilitar o deshabilitar el envío de emails");
        initIfAbsent(KEY_MAX_RETRIES, "3", "INTEGER", "Número máximo de reintentos por email fallido");
        initIfAbsent(KEY_RETRY_COOLDOWN, "2000", "INTEGER",
                "Tiempo de espera inicial entre reintentos (ms), aplica backoff exponencial");
        initIfAbsent(KEY_ALLOWED_TEMPLATES, "welcome,password-reset,order-confirmation",
                "STRING", "Templates habilitados para envío (separados por coma)");
        initIfAbsent(KEY_PURGE_RETENTION_MONTHS, "6", "INTEGER",
                "Meses de retención de logs de email (0 = sin depuración)");
        initIfAbsent(KEY_PURGE_CRON, "0 0 3 1 1,7 *", "STRING",
                "Expresión cron para la depuración automática de logs (semestral por defecto)");
        log.info("ConfigService inicializado con {} configuraciones", configRepository.count());
    }

    @Override
    public int getDailySendLimit() {
        return getInt(KEY_DAILY_LIMIT, 300);
    }

    @Override
    public boolean isServiceEnabled() {
        return getBoolean(KEY_SERVICE_ENABLED, true);
    }

    @Override
    public int getMaxRetryAttempts() {
        return getInt(KEY_MAX_RETRIES, 3);
    }

    @Override
    public long getRetryCooldownMs() {
        return (long) getInt(KEY_RETRY_COOLDOWN, 2000);
    }

    @Override
    public String getAllowedTemplates() {
        return getString(KEY_ALLOWED_TEMPLATES, "welcome,password-reset,order-confirmation");
    }

    @Override
    public String getString(String key, String defaultValue) {
        String cached = cache.get(key);
        if (cached != null)
            return cached;

        return configRepository.findById(key)
                .map(config -> {
                    cache.put(key, config.getConfigValue());
                    return config.getConfigValue();
                })
                .orElse(defaultValue);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(getString(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            log.warn("Valor no numérico para clave '{}', usando default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key, String.valueOf(defaultValue));
        return "true".equalsIgnoreCase(value);
    }

    @Override
    @Transactional
    public void updateConfig(String key, String value) {
        configRepository.findById(key).ifPresent(config -> {
            config.setConfigValue(value);
            config.setUpdatedAt(LocalDateTime.now());
            configRepository.save(config);
            cache.remove(key); // Invalida caché para esta clave
            log.info("Configuración actualizada: {} = {}", key, value);
        });
    }

    @Override
    public List<ServiceConfig> getAllConfigs() {
        return configRepository.findAll();
    }

    // --- Privado ---

    private void initIfAbsent(String key, String value, String type, String description) {
        if (!configRepository.existsById(key)) {
            configRepository.save(ServiceConfig.builder()
                    .configKey(key)
                    .configValue(value)
                    .description(description)
                    .configType(type)
                    .build());
            log.debug("Config inicializada: {} = {}", key, value);
        }
    }
}
