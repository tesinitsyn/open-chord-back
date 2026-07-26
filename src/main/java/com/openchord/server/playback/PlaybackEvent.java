package com.openchord.server.playback;

import com.openchord.server.catalog.Track;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "playback_events")
/** Immutable-in-practice snapshot of listening progress for one track. */
public class PlaybackEvent {
    /** Stable event identifier. */
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id")
    /** Track whose playback was reported. */
    private Track track;

    /** Client-supplied or server-generated UTC instant. */
    private Instant playedAt;
    /** Last known position, clamped to the track duration. */
    private long positionMs;
    /** Whether the client considered playback completed. */
    private boolean completed;

    /** Required by JPA. */
    protected PlaybackEvent() {
    }

    /** Creates a validated playback-history entry. */
    public PlaybackEvent(Track track, Instant playedAt, long positionMs, boolean completed) {
        this.track = track;
        this.playedAt = playedAt;
        this.positionMs = positionMs;
        this.completed = completed;
    }

    /** Returns the event identifier. */
    public UUID getId() {
        return id;
    }

    /** Returns the associated track. */
    public Track getTrack() {
        return track;
    }

    /** Returns when playback was reported. */
    public Instant getPlayedAt() {
        return playedAt;
    }

    /** Returns the last known playback position. */
    public long getPositionMs() {
        return positionMs;
    }

    /** Returns whether playback completed. */
    public boolean isCompleted() {
        return completed;
    }
}
