package com.openchord.server.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openchord")
/**
 * Typed server configuration.
 *
 * @param mediaRoot filesystem root containing managed artwork, tracks, and import staging files
 * @param publicBaseUrl base URL placed in GraphQL media links
 */
public record OpenChordProperties(Path mediaRoot, String publicBaseUrl) {
    /** Normalizes the public base URL so callers can safely append absolute endpoint paths. */
    public OpenChordProperties {
        publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }
}
