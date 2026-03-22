package com.demo.sentinel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class SentinelApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentinelApplication.class, args);
        log.info("=======================================================");
        log.info("  Sentinel Component started successfully");
        log.info("  API Base  : http://localhost:8080/api/v1/work-tasks");
        log.info("  H2 Console: http://localhost:8080/h2-console");
        log.info("  Health    : http://localhost:8080/actuator/health");
        log.info("=======================================================");
    }
}
