package com.mailservice.repository;

import com.mailservice.entity.ServiceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para la entidad ServiceConfig.
 */
@Repository
public interface ServiceConfigRepository extends JpaRepository<ServiceConfig, String> {
}
