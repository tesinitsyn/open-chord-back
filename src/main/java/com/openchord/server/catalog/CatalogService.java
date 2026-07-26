package com.openchord.server.catalog;

import com.openchord.server.playback.PlaybackEventRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only application service for public catalog and listening-history queries.
 *
 * <p>Results are fully initialized inside each transaction so controllers can safely map lazy JPA
 * relationships after the repository call. User-provided pagination values are clamped to bounded
 * ranges.
 */
@Service
public class CatalogService {
    private final AlbumRepository albums;
    private final TrackRepository tracks;
    private final PlaybackEventRepository playbackEvents;

    public CatalogService(
            AlbumRepository albums, TrackRepository tracks, PlaybackEventRepository playbackEvents) {
        this.albums = albums;
        this.tracks = tracks;
        this.playbackEvents = playbackEvents;
    }

    @Transactional(readOnly = true)
    public List<Album> albums(String search, int limit, int offset) {
        String normalized = search == null || search.isBlank() ? null : search.strip();
        List<Album> matches =
                normalized == null ? albums.findAllDetailed() : albums.searchDetailed(normalized);
        int from = Math.min(Math.max(offset, 0), matches.size());
        int to = Math.min(from + Math.clamp(limit, 1, 100), matches.size());
        return List.copyOf(matches.subList(from, to));
    }

    @Transactional(readOnly = true)
    public Optional<Album> album(UUID id) {
        return albums.findDetailedById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Track> track(UUID id) {
        return tracks.findDetailedById(id);
    }

    @Transactional(readOnly = true)
    public List<Album> recentlyPlayed(int limit) {
        return playbackEvents.findRecentAlbumIds(PageRequest.of(0, Math.clamp(limit, 1, 50))).stream()
                .map(albums::findDetailedById)
                .flatMap(Optional::stream)
                .toList();
    }
}
