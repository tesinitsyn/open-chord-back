package com.openchord.server.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Synchronized lyric segment active from {@code startMs} until {@code endMs}.
 *
 * <p>Times are measured from the beginning of the owning track. The start is inclusive and the end
 * is exclusive.
 */
@Entity
@Table(name = "lyric_lines")
public class LyricLine {
    @Id
    @GeneratedValue
    private UUID id;
    private String text;
    private long startMs;
    private long endMs;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id")
    private Track track;

    protected LyricLine() {
    }

    public LyricLine(String text, long startMs, long endMs) {
        this.text = text;
        this.startMs = startMs;
        this.endMs = endMs;
    }

    void attachTo(Track track) {
        this.track = track;
    }

    public UUID getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }
}
