package com.idp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    @Override
    public void run(String... args) {
        log.info("[FLYWAY] Database schema and initial seed data managed via Flyway SQL migrations (db/migration/V1, V2).");
    }
}
