package com.openchord.server.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "albums")
/** Persistent album aggregate that owns its ordered tracks and optional artwork. */
public class Album {
    /** Stable database identifier exposed through the API. */
    @Id
    @GeneratedValue
    private UUID id;
    /** Display title as supplied by the library owner. */
    private String title;
    /** Four-digit release year used for catalog ordering and presentation. */
    private int releaseYear;
    /** Media-root-relative path; never an arbitrary filesystem path or public URL. */
    private String artworkPath;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id")
    /** Artist shared by every track in this album aggregate. */
    private Artist artist;

    @OneToMany(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("discNumber, number")
    /** Tracks kept in disc/track order while preserving JPA orphan-removal semantics. */
    private Set<Track> tracks = new LinkedHashSet<>();

    /** Required by JPA; application code should use the validating constructor. */
    protected Album() {
    }

    /** Creates a new, not-yet-persisted album aggregate. */
    public Album(String title, int releaseYear, String artworkPath, Artist artist) {
        this.title = title;
        this.releaseYear = releaseYear;
        this.artworkPath = artworkPath;
        this.artist = artist;
    }

    /** Adds a track and synchronizes the owning side of the bidirectional relationship. */
    public void addTrack(Track track) {
        tracks.add(track);
        track.attachTo(this);
    }

    /** Replaces the media-root-relative artwork path after artwork is stored. */
    public void setArtworkPath(String artworkPath) {
        this.artworkPath = artworkPath;
    }

    /** Returns the persistent album identifier. */
    public UUID getId() {
        return id;
    }

    /** Returns the catalog title. */
    public String getTitle() {
        return title;
    }

    /** Returns the release year. */
    public int getReleaseYear() {
        return releaseYear;
    }

    /** Returns the stored artwork path, or {@code null} when the album has no artwork. */
    public String getArtworkPath() {
        return artworkPath;
    }

    /** Returns the album artist. */
    public Artist getArtist() {
        return artist;
    }

    /** Returns an immutable, correctly ordered snapshot of the track collection. */
    public List<Track> getTracks() {
        return List.copyOf(tracks);
    }
}
