package com.openchord.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
/** Spring Boot entry point for the OpenChord catalog, media, and administration server. */
public class OpenChordServerApplication {

    /** Starts the application using the active Spring profile and environment configuration. */
    public static void main(String[] args) {
        SpringApplication.run(OpenChordServerApplication.class, args);
    }
}
