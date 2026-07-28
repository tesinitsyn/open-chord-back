package com.openchord.server.playlist;

import com.openchord.server.catalog.Track;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/** Ordered association between a playlist and one catalog track. */
@Entity
@Table(name = "playlist_entries")
public class PlaylistEntry {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "playlist_id")
    private Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id")
    private Track track;

    private int position;

    protected PlaylistEntry() {
    }

    PlaylistEntry(Playlist playlist, Track track, int position) {
        this.playlist = playlist;
        this.track = track;
        this.position = position;
    }

    void setPosition(int position) {
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public Track getTrack() {
        return track;
    }

    public int getPosition() {
        return position;
    }
}
