package dev.errorlens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ErrorLens - Execute. Trace. Investigate. Understand.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ErrorLensApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErrorLensApplication.class, args);
    }
}
