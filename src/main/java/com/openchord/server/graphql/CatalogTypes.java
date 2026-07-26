package com.openchord.server.graphql;

import com.openchord.server.catalog.Album;
import com.openchord.server.catalog.Artist;
import com.openchord.server.catalog.LyricLine;
import com.openchord.server.catalog.Track;
import com.openchord.server.config.OpenChordProperties;
import com.openchord.server.playback.PlaybackEvent;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** GraphQL transport records and explicit mappings from persistence aggregates. */
public final class CatalogTypes {
    /** Utility holder; not instantiable. */
    private CatalogTypes() {
    }

    /** Client-facing artist projection. */
    public record ArtistView(UUID id, String name) {
        static ArtistView from(Artist artist) {
            return new ArtistView(artist.getId(), artist.getName());
        }
    }

    /** Client-facing synchronized lyric projection. */
    public record LyricLineView(UUID id, String text, long startMs, long endMs) {
        static LyricLineView from(LyricLine line) {
            return new LyricLineView(line.getId(), line.getText(), line.getStartMs(), line.getEndMs());
        }
    }

    /** Client-facing track projection with a playable media URL. */
    public record TrackView(
            UUID id,
            String title,
            long durationMs,
            int discNumber,
            int number,
            String artistName,
            String albumTitle,
            String streamUrl,
            List<LyricLineView> lyrics) {
        static TrackView from(Track track, OpenChordProperties properties) {
            return new TrackView(
                    track.getId(),
                    track.getTitle(),
                    track.getDurationMs(),
                    track.getDiscNumber(),
                    track.getNumber(),
                    track.getAlbum().getArtist().getName(),
                    track.getAlbum().getTitle(),
                    properties.publicBaseUrl() + "/media/tracks/" + track.getId(),
                    track.getLyrics().stream().map(LyricLineView::from).toList());
        }
    }

    /** Client-facing album projection with artwork and ordered tracks. */
    public record AlbumView(
            UUID id,
            String title,
            int year,
            String artworkUrl,
            ArtistView artist,
            List<TrackView> tracks) {
        static AlbumView from(Album album, OpenChordProperties properties) {
            String artworkUrl =
                    album.getArtworkPath() == null
                            ? null
                            : properties.publicBaseUrl() + "/media/artwork/" + album.getId();
            return new AlbumView(
                    album.getId(),
                    album.getTitle(),
                    album.getReleaseYear(),
                    artworkUrl,
                    ArtistView.from(album.getArtist()),
                    album.getTracks().stream().map(track -> TrackView.from(track, properties)).toList());
        }
    }

    /** Mutation input reported by a playback client. */
    public record PlaybackEventInput(
            UUID trackId, long positionMs, boolean completed, OffsetDateTime playedAt) {
    }

    /** Persisted playback event returned by the mutation. */
    public record PlaybackEventView(
            UUID id, UUID trackId, OffsetDateTime playedAt, long positionMs, boolean completed) {
        static PlaybackEventView from(PlaybackEvent event) {
            return new PlaybackEventView(
                    event.getId(),
                    event.getTrack().getId(),
                    event.getPlayedAt().atOffset(ZoneOffset.UTC),
                    event.getPositionMs(),
                    event.isCompleted());
        }
    }
}
