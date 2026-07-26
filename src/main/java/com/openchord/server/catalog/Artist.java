package com.openchord.server.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Catalog artist referenced by one or more albums.
 *
 * <p>Administration workflows reuse artists by case-insensitive name; the entity itself contains
 * no lifecycle cascade to albums.
 */
@Entity
@Table(name = "artists")
public class Artist {
    @Id
    @GeneratedValue
    private UUID id;
    private String name;

    protected Artist() {
    }

    public Artist(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
