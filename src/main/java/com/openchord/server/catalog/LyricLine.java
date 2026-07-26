package com.openchord.server.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "lyric_lines")
/** One synchronized lyric segment with an inclusive start and exclusive end timestamp. */
public class LyricLine {
    /** Stable database identifier. */
    @Id
    @GeneratedValue
    private UUID id;
    /** Text displayed while this segment is active. */
    private String text;
    /** Segment start measured from the beginning of the track. */
    private long startMs;
    /** Segment end measured from the beginning of the track. */
    private long endMs;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id")
    /** Owning track, synchronized when the line is added to a track. */
    private Track track;

    /** Required by JPA. */
    protected LyricLine() {
    }

    /** Creates a lyric segment; callers are responsible for validated timestamps. */
    public LyricLine(String text, long startMs, long endMs) {
        this.text = text;
        this.startMs = startMs;
        this.endMs = endMs;
    }

    /** Synchronizes the inverse side of the track relationship. */
    void attachTo(Track track) {
        this.track = track;
    }

    /** Returns the persistent lyric identifier. */
    public UUID getId() {
        return id;
    }

    /** Returns the displayed lyric text. */
    public String getText() {
        return text;
    }

    /** Returns the start timestamp in milliseconds. */
    public long getStartMs() {
        return startMs;
    }

    /** Returns the end timestamp in milliseconds. */
    public long getEndMs() {
        return endMs;
    }
}
