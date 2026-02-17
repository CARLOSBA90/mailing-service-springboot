package com.mailservice;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class MailServiceApplication {

    public static void main(String[] args) {
        // Cargar variables del archivo .env
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing() // No falla si el .env no existe (útil en producción)
                .load();

        // Establecer las variables como propiedades del sistema
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        SpringApplication.run(MailServiceApplication.class, args);
    }
}
