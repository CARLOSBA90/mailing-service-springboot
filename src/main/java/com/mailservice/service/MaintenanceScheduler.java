package com.mailservice.service;

import com.mailservice.repository.MailLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Tarea programada de mantenimiento para depurar logs de emails antiguos.
 *
 * <p>
 * Implementa {@link SchedulingConfigurer} en lugar de usar {@code @Scheduled},
 * lo que permite que el cron sea releído desde la base de datos en cada
 * disparo.
 * De esta forma, el cambio del cron desde el panel de admin surte efecto
 * sin necesidad de reiniciar la aplicación.
 *
 * <p>
 * La retención (en meses) también es configurable dinámicamente mediante
 * la clave {@code purge_retention_months} en el panel de admin.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MaintenanceScheduler implements SchedulingConfigurer {

    private final MailLogRepository mailLogRepository;
    private final ConfigService configService;

    /**
     * Registra la tarea con un trigger dinámico.
     * En cada ejecución Spring evalúa el método {@code nextExecution} del trigger,
     * lo que permite que el cron sea leído de la DB en cada ciclo.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                this::scheduledPurge,
                context -> {
                    String cron = configService.getString(
                            ConfigServiceImpl.KEY_PURGE_CRON, "0 0 3 1 1,7 *");
                    return new CronTrigger(cron).nextExecution(context);
                });
    }

    /**
     * Lógica de purga ejecutada por el scheduler.
     * También puede ser invocado manualmente desde {@code AdminController}.
     */
    @Transactional
    public void scheduledPurge() {
        log.info("Iniciando depuración programada de logs de emails...");
        int deleted = purgeOldLogs();
        log.info("Depuración programada completada | registros eliminados: {}", deleted);
    }

    /**
     * Ejecuta la depuración y retorna la cantidad de registros eliminados.
     * Puede ser invocado manualmente desde el Admin.
     *
     * @return cantidad de registros eliminados
     */
    @Transactional
    public int purgeOldLogs() {
        int retentionMonths = configService.getInt(ConfigServiceImpl.KEY_PURGE_RETENTION_MONTHS, 6);
        if (retentionMonths <= 0) {
            log.warn("Retención configurada en {} meses — valor inválido, se omite la depuración.", retentionMonths);
            return 0;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusMonths(retentionMonths);
        log.info("Depurando logs anteriores a {} ({} meses de retención)", cutoff, retentionMonths);

        int deleted = mailLogRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Depuración finalizada | cutoff: {} | eliminados: {}", cutoff, deleted);
        return deleted;
    }
}
