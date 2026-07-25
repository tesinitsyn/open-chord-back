package com.openchord.server.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openchord")
public record OpenChordProperties(Path mediaRoot, String publicBaseUrl) {
    public OpenChordProperties {
        publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }
}
