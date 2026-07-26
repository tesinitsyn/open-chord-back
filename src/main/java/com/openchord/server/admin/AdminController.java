package com.openchord.server.admin;

import com.openchord.server.catalog.Album;
import com.openchord.server.catalog.Track;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Trusted-network administration API for catalog inspection and direct track mutations.
 *
 * <p>Album-sized, reviewable uploads use {@link AlbumImportController}; this controller serves the
 * simpler single-track workflow.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminCatalogService catalog;

    public AdminController(AdminCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/catalog")
    public List<AlbumView> catalog() {
        return catalog.catalog();
    }

    @PostMapping(path = "/tracks", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public TrackView createTrack(
            @RequestParam String artist,
            @RequestParam String album,
            @RequestParam int releaseYear,
            @RequestParam String title,
            @RequestParam(defaultValue = "1") int discNumber,
            @RequestParam int trackNumber,
            @RequestParam long durationMs,
            @RequestParam(defaultValue = "") String lyrics,
            @RequestParam MultipartFile audio,
            @RequestParam(required = false) MultipartFile artwork)
            throws IOException {
        return catalog.createTrack(
                artist,
                album,
                releaseYear,
                title,
                discNumber,
                trackNumber,
                durationMs,
                lyrics,
                audio,
                artwork);
    }

    @PutMapping("/tracks/{id}/lyrics")
    public TrackView replaceLyrics(@PathVariable UUID id, @RequestBody LyricsRequest request) {
        return catalog.replaceLyrics(id, request.lyrics());
    }

    /**
     * Replacement synchronized lyrics submitted by an administrator.
     *
     * @param lyrics LRC or plain text; blank removes existing lines
     */
    public record LyricsRequest(String lyrics) {
    }

    /**
     * Stable error body returned for rejected admin requests.
     *
     * @param message human-readable reason suitable for the admin UI
     */
    public record ErrorView(String message) {
    }

    /**
     * Compact track projection used by the administration catalog.
     *
     * @param id         track identifier
     * @param title      display title
     * @param durationMs duration in milliseconds
     * @param discNumber one-based disc number
     * @param number     one-based position on the disc
     * @param lyricLines number of synchronized lyric intervals
     */
    public record TrackView(
            UUID id, String title, long durationMs, int discNumber, int number, int lyricLines) {
        static TrackView from(Track track) {
            return new TrackView(
                    track.getId(),
                    track.getTitle(),
                    track.getDurationMs(),
                    track.getDiscNumber(),
                    track.getNumber(),
                    track.getLyrics().size());
        }
    }

    /**
     * Album projection used by the administration catalog.
     *
     * @param id         album identifier
     * @param title      display title
     * @param year       release year
     * @param artist     artist display name
     * @param hasArtwork whether a managed artwork path is present
     * @param tracks     tracks in disc and track order
     */
    public record AlbumView(
            UUID id, String title, int year, String artist, boolean hasArtwork, List<TrackView> tracks) {
        static AlbumView from(Album album) {
            return new AlbumView(
                    album.getId(),
                    album.getTitle(),
                    album.getReleaseYear(),
                    album.getArtist().getName(),
                    album.getArtworkPath() != null,
                    album.getTracks().stream().map(TrackView::from).toList());
        }
    }
}
