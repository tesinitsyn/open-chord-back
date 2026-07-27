package com.openchord.server.playlist;

import com.openchord.server.catalog.Track;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * User-curated, ordered collection of unique catalog tracks.
 *
 * <p>Ordering belongs to the aggregate rather than the track. Every mutation normalizes positions
 * so the database uniqueness constraint remains valid and clients receive a dense zero-based
 * sequence.
 */
@Entity
@Table(name = "playlists")
public class Playlist {
    @Id
    @GeneratedValue
    private UUID id;

    private String name;
    private String description;
    private String artworkPath;
    private String artworkContentType;
    private Instant createdAt;
    private Instant updatedAt;

    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<PlaylistEntry> entries = new ArrayList<>();

    protected Playlist() {
    }

    public Playlist(String name, String description, Instant now) {
        this.name = name;
        this.description = description;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void rename(String name, Instant now) {
        this.name = name;
        touch(now);
    }

    public void addTrack(Track track, Instant now) {
        if (entries.stream().anyMatch(entry -> entry.getTrack().getId().equals(track.getId()))) {
            return;
        }
        entries.add(new PlaylistEntry(this, track, entries.size()));
        touch(now);
    }

    public boolean removeTrack(UUID trackId, Instant now) {
        boolean removed = entries.removeIf(entry -> entry.getTrack().getId().equals(trackId));
        if (removed) {
            normalizePositions();
            touch(now);
        }
        return removed;
    }

    public void moveTrack(UUID trackId, int targetPosition, Instant now) {
        PlaylistEntry entry =
                entries.stream()
                        .filter(candidate -> candidate.getTrack().getId().equals(trackId))
                        .findFirst()
                        .orElseThrow(() -> new PlaylistNotFoundException("Track is not in this playlist"));
        entries.remove(entry);
        entries.add(Math.clamp(targetPosition, 0, entries.size()), entry);
        normalizePositions();
        touch(now);
    }

    private void normalizePositions() {
        for (int index = 0; index < entries.size(); index++) {
            entries.get(index).setPosition(index);
        }
    }

    private void touch(Instant now) {
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getArtworkPath() {
        return artworkPath;
    }

    public String getArtworkContentType() {
        return artworkContentType;
    }

    public void setArtwork(String path, String contentType, Instant now) {
        artworkPath = path;
        artworkContentType = contentType;
        touch(now);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<PlaylistEntry> getEntries() {
        return List.copyOf(entries);
    }
}
