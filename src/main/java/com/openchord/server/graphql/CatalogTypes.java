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

/**
 * Immutable GraphQL input and output models plus domain-to-transport mapping helpers.
 *
 * <p>Timestamp conversion is explicit: persistence uses {@link java.time.Instant}, while GraphQL
 * exposes an offset timestamp normalized to UTC.
 */
public final class CatalogTypes {
    private CatalogTypes() {
    }

    /**
     * Public artist projection.
     *
     * @param id   artist identifier
     * @param name display name
     */
    public record ArtistView(UUID id, String name) {
        static ArtistView from(Artist artist) {
            return new ArtistView(artist.getId(), artist.getName());
        }
    }

    /**
     * Playback interval for one synchronized lyric line.
     *
     * @param id      lyric-line identifier
     * @param text    displayed lyric text
     * @param startMs inclusive offset from track start
     * @param endMs   exclusive offset from track start
     */
    public record LyricLineView(UUID id, String text, long startMs, long endMs) {
        static LyricLineView from(LyricLine line) {
            return new LyricLineView(line.getId(), line.getText(), line.getStartMs(), line.getEndMs());
        }
    }

    /**
     * Public track projection with its owning album context and resolved stream URL.
     *
     * @param id         track identifier
     * @param title      display title
     * @param durationMs duration in milliseconds
     * @param discNumber one-based disc number
     * @param number     one-based position on the disc
     * @param artistName owning artist display name
     * @param albumTitle owning album display title
     * @param streamUrl  absolute media endpoint URL
     * @param lyrics     synchronized lyrics in timestamp order
     */
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

    /**
     * Public album projection returned by catalog queries.
     *
     * @param id         album identifier
     * @param title      display title
     * @param year       release year
     * @param artworkUrl absolute artwork URL, or {@code null} when no artwork is stored
     * @param artist     owning artist
     * @param tracks     tracks in disc and track order
     */
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

    /**
     * Client playback state accepted by the playback mutation.
     *
     * @param trackId    played track identifier
     * @param positionMs current position in milliseconds; values beyond duration are clamped
     * @param completed  whether the client considers playback complete
     * @param playedAt   client event time, or {@code null} to use the server clock
     */
    public record PlaybackEventInput(
            UUID trackId, long positionMs, boolean completed, OffsetDateTime playedAt) {
    }

    /**
     * Persisted playback event returned to the GraphQL client.
     *
     * @param id         event identifier
     * @param trackId    played track identifier
     * @param playedAt   event time normalized to UTC
     * @param positionMs validated position in milliseconds
     * @param completed  client completion flag
     */
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
