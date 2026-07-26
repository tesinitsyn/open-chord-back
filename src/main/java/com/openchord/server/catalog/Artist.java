package com.openchord.server.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "artists")
/** Persistent catalog artist reused by albums with the same case-insensitive name. */
public class Artist {
    /** Stable database identifier. */
    @Id
    @GeneratedValue
    private UUID id;
    /** Human-readable artist name. */
    private String name;

    /** Required by JPA. */
    protected Artist() {
    }

    /** Creates a new artist with the supplied display name. */
    public Artist(String name) {
        this.name = name;
    }

    /** Returns the persistent artist identifier. */
    public UUID getId() {
        return id;
    }

    /** Returns the display name. */
    public String getName() {
        return name;
    }
}
