package com.openchord.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Bootstraps the OpenChord backend and discovers its typed configuration properties.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class OpenChordServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenChordServerApplication.class, args);
    }
}
