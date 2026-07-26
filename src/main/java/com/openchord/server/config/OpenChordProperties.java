package com.openchord.server.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Filesystem and public URL settings for managed OpenChord media.
 *
 * @param mediaRoot root below which audio, artwork, and temporary imports are stored
 * @param publicBaseUrl externally reachable server URL used to construct media links
 */
@ConfigurationProperties(prefix = "openchord")
public record OpenChordProperties(Path mediaRoot, String publicBaseUrl) {
    public OpenChordProperties {
        publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }
}
