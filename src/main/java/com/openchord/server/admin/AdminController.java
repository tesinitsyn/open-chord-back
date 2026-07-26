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

@RestController
@RequestMapping("/api/admin")
/** HTTP adapter for direct catalog administration operations. */
public class AdminController {
    /** Application service that owns catalog mutations and media persistence. */
    private final AdminCatalogService catalog;

    /** Creates the controller for the supplied catalog service. */
    public AdminController(AdminCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/catalog")
    /** Returns the complete editable catalog used by OpenChord Studio. */
    public List<AlbumView> catalog() {
        return catalog.catalog();
    }

    @PostMapping(path = "/tracks", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    /** Creates one track, its missing artist/album parents, optional artwork, and lyrics. */
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
    /** Replaces every synchronized lyric line for a track. */
    public TrackView replaceLyrics(@PathVariable UUID id, @RequestBody LyricsRequest request) {
        return catalog.replaceLyrics(id, request.lyrics());
    }

    /** Request body for replacing an LRC document. */
    public record LyricsRequest(String lyrics) {
    }

    /** Stable JSON error envelope returned by administration endpoints. */
    public record ErrorView(String message) {
    }

    /** Editable track projection returned to the administration UI. */
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

    /** Album projection with its ordered tracks for administration screens. */
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
