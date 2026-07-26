package com.openchord.server.catalog;

import com.openchord.server.playback.PlaybackEventRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/** Read-only catalog facade that returns fully initialized aggregates to API adapters. */
public class CatalogService {
    /** Album queries with the entity graphs required outside the transaction. */
    private final AlbumRepository albums;
    /** Detailed track lookup used by playback and media consumers. */
    private final TrackRepository tracks;
    /** Playback history source used to derive recently played albums. */
    private final PlaybackEventRepository playbackEvents;

    /** Creates the catalog facade from its persistence ports. */
    public CatalogService(
            AlbumRepository albums, TrackRepository tracks, PlaybackEventRepository playbackEvents) {
        this.albums = albums;
        this.tracks = tracks;
        this.playbackEvents = playbackEvents;
    }

    @Transactional(readOnly = true)
    /** Searches and bounds a catalog page after normalizing optional user input. */
    public List<Album> albums(String search, int limit, int offset) {
        String normalized = search == null || search.isBlank() ? null : search.strip();
        List<Album> matches =
                normalized == null ? albums.findAllDetailed() : albums.searchDetailed(normalized);
        int from = Math.min(Math.max(offset, 0), matches.size());
        int to = Math.min(from + Math.clamp(limit, 1, 100), matches.size());
        return List.copyOf(matches.subList(from, to));
    }

    @Transactional(readOnly = true)
    /** Finds one fully initialized album by identifier. */
    public Optional<Album> album(UUID id) {
        return albums.findDetailedById(id);
    }

    @Transactional(readOnly = true)
    /** Finds one fully initialized track by identifier. */
    public Optional<Track> track(UUID id) {
        return tracks.findDetailedById(id);
    }

    @Transactional(readOnly = true)
    /** Returns distinct albums ordered by their most recent playback event. */
    public List<Album> recentlyPlayed(int limit) {
        return playbackEvents.findRecentAlbumIds(PageRequest.of(0, Math.clamp(limit, 1, 50))).stream()
                .map(albums::findDetailedById)
                .flatMap(Optional::stream)
                .toList();
    }
}
