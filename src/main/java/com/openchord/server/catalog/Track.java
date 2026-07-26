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
@Table(name = "tracks")
/** Persistent playable track belonging to one album and owning synchronized lyric lines. */
public class Track {
    /** Stable database identifier used by media and playback endpoints. */
    @Id
    @GeneratedValue
    private UUID id;
    /** Display title. */
    private String title;
    /** Full track duration in milliseconds. */
    private long durationMs;
    /** One-based disc number. */
    private int discNumber;
    /** One-based position within the disc. */
    private int number;
    /** Media-root-relative audio path. */
    private String audioPath;
    /** MIME type returned by the media endpoint. */
    private String contentType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id")
    /** Owning album; attached through {@link Album#addTrack(Track)}. */
    private Album album;

    @OneToMany(mappedBy = "track", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startMs")
    /** Synchronized lyric lines ordered by their start timestamp. */
    private Set<LyricLine> lyrics = new LinkedHashSet<>();

    /** Required by JPA. */
    protected Track() {
    }

    /** Creates a not-yet-attached track with stored-media metadata. */
    public Track(
            String title,
            long durationMs,
            int discNumber,
            int number,
            String audioPath,
            String contentType) {
        this.title = title;
        this.durationMs = durationMs;
        this.discNumber = discNumber;
        this.number = number;
        this.audioPath = audioPath;
        this.contentType = contentType;
    }

    /** Synchronizes the inverse side of the album relationship. */
    void attachTo(Album album) {
        this.album = album;
    }

    /** Adds a lyric line and synchronizes its track relationship. */
    public void addLyricLine(LyricLine line) {
        lyrics.add(line);
        line.attachTo(this);
    }

    /** Atomically replaces the ordered lyric collection for this aggregate. */
    public void replaceLyrics(List<LyricLine> lines) {
        lyrics.clear();
        lines.forEach(this::addLyricLine);
    }

    /** Returns the persistent track identifier. */
    public UUID getId() {
        return id;
    }

    /** Returns the display title. */
    public String getTitle() {
        return title;
    }

    /** Returns the duration in milliseconds. */
    public long getDurationMs() {
        return durationMs;
    }

    /** Returns the one-based disc number. */
    public int getDiscNumber() {
        return discNumber;
    }

    /** Returns the one-based track number. */
    public int getNumber() {
        return number;
    }

    /** Returns the media-root-relative audio path. */
    public String getAudioPath() {
        return audioPath;
    }

    /** Returns the audio MIME type. */
    public String getContentType() {
        return contentType;
    }

    /** Returns the owning album. */
    public Album getAlbum() {
        return album;
    }

    /** Returns an immutable timestamp-ordered lyric snapshot. */
    public List<LyricLine> getLyrics() {
        return List.copyOf(lyrics);
    }
}
