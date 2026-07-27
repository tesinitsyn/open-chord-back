package com.openchord.server.playlist;

import com.openchord.server.catalog.Track;
import com.openchord.server.catalog.TrackRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundary for playlist lifecycle and ordered membership mutations. */
@Service
public class PlaylistService {
    private final PlaylistRepository playlists;
    private final TrackRepository tracks;
    private final Clock clock = Clock.systemUTC();

    public PlaylistService(PlaylistRepository playlists, TrackRepository tracks) {
        this.playlists = playlists;
        this.tracks = tracks;
    }

    @Transactional(readOnly = true)
    public List<Playlist> playlists() {
        return playlists.findAllDetailed();
    }

    @Transactional(readOnly = true)
    public Playlist playlist(UUID id) {
        return findDetailed(id);
    }

    @Transactional
    public Playlist create(String name) {
        Instant now = clock.instant();
        return playlists.saveAndFlush(new Playlist(normalizeName(name), now));
    }

    @Transactional
    public Playlist rename(UUID id, String name) {
        Playlist playlist = findDetailed(id);
        playlist.rename(normalizeName(name), clock.instant());
        return playlists.saveAndFlush(playlist);
    }

    @Transactional
    public boolean delete(UUID id) {
        if (!playlists.existsById(id)) {
            return false;
        }
        playlists.deleteById(id);
        return true;
    }

    @Transactional
    public Playlist addTrack(UUID playlistId, UUID trackId) {
        Playlist playlist = findDetailed(playlistId);
        Track track =
                tracks.findDetailedById(trackId)
                        .orElseThrow(() -> new PlaylistNotFoundException("Track not found"));
        playlist.addTrack(track, clock.instant());
        playlists.saveAndFlush(playlist);
        return findDetailed(playlistId);
    }

    @Transactional
    public Playlist removeTrack(UUID playlistId, UUID trackId) {
        Playlist playlist = findDetailed(playlistId);
        if (!playlist.removeTrack(trackId, clock.instant())) {
            throw new PlaylistNotFoundException("Track is not in this playlist");
        }
        playlists.saveAndFlush(playlist);
        return findDetailed(playlistId);
    }

    @Transactional
    public Playlist moveTrack(UUID playlistId, UUID trackId, int position) {
        Playlist playlist = findDetailed(playlistId);
        playlist.moveTrack(trackId, position, clock.instant());
        playlists.saveAndFlush(playlist);
        return findDetailed(playlistId);
    }

    private Playlist findDetailed(UUID id) {
        return playlists.findDetailedById(id)
                .orElseThrow(() -> new PlaylistNotFoundException("Playlist not found"));
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.strip();
        if (normalized.isEmpty() || normalized.length() > 120) {
            throw new IllegalArgumentException("Playlist name must contain 1 to 120 characters");
        }
        return normalized;
    }
}
